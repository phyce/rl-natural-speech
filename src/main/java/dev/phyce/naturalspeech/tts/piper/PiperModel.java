package dev.phyce.naturalspeech.tts.piper;

import com.google.common.util.concurrent.MoreExecutors;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.tts.VoiceID;
import static dev.phyce.naturalspeech.utils.CommonUtil.silentInterruptHandler;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

// Renamed from TTSModel
@SuppressWarnings("UnstableApiUsage")
@Slf4j
public class PiperModel {
	@Getter
	private final ConcurrentHashMap<Long, PiperProcess> processMap = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<PiperTask> piperTaskQueue = new ConcurrentLinkedQueue<>();
	private final ConcurrentLinkedQueue<PiperTask> dispatchedQueue = new ConcurrentLinkedQueue<>();
	private final AudioEngine audioEngine;
	@Getter
	private final PiperRepository.ModelLocal modelLocal;
	@Getter
	private final Path piperPath;

	private final Vector<PiperProcessLifetimeListener> piperProcessLifetimeListeners = new Vector<>();

	private Thread processPiperTaskThread;

	@Getter
	private static final AudioFormat audioFormat =
		new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
			22050.0F, // Sample Rate (per second)
			16, // Sample Size (bits)
			1, // Channels
			2, // Frame Size (bytes)
			22050.0F, // Frame Rate (same as sample rate because PCM is 1 sample per 1 frame)
			false
		); // Little Endian

	public PiperModel(AudioEngine audioEngine, PiperRepository.ModelLocal modelLocal, Path piperPath)
		throws IOException {
		this.audioEngine = audioEngine;
		this.modelLocal = modelLocal;
		this.piperPath = piperPath;

		processPiperTaskThread = null;

	}

	/**
	 * Create a piper and immediately start
	 *
	 * @throws IOException if piper fails to start an IOException will be thrown. (because stdin cannot be opened).
	 */
	public void start(int processCount)
		throws IOException {

		startProcess(processCount);
		processPiperTaskThread =
			new Thread(this::processPiperTask, String.format("[%s] Piper::processPiperTask Thread", this));
		processPiperTaskThread.setUncaughtExceptionHandler(silentInterruptHandler);
		processPiperTaskThread.start();
	}

	public void stop() {
		for (PiperProcess instance : processMap.values()) {
			instance.stop();
		}
		processMap.clear();
		// clear task and audio queue on stop
		cancelAll();
		processPiperTaskThread.interrupt();
		processPiperTaskThread = null;
	}

	public boolean speak(VoiceID voiceID, String text, Supplier<Float> gainDB, String lineName) {
		if (processPiperTaskThread == null) {
			log.error("PiperModel for {} not started, cannot speak. text:{} lineName:{}", voiceID, text, lineName);
			return false;
		}

		if (countAlive() == 0) {
			log.error("No active processes for {} running. text:{} lineName:{}", voiceID, text, lineName);
			return false;
		}

		List<String> segments = TextUtil.splitSentence(text);
		if (segments.size() > 1) {
			log.trace("Piper speech segmentation: {}", TextUtil.sentenceSegmentPrettyPrint(segments));

			// build a linked list of tasks
			PiperTask parent = null;
			for (String segment : segments) {
				PiperTask child = new PiperTask(segment, voiceID, gainDB, lineName, parent);
				log.trace("{} task added. lineName:{} parent:{} text:{} ", voiceID, lineName, parent != null, segment);
				piperTaskQueue.add(child);
				parent = child;
			}
		}
		else {
			log.trace("{} task Added lineName:{} parent:null text:{}", voiceID, lineName, text);
			piperTaskQueue.add(new PiperTask(text, voiceID, gainDB, lineName, null));
		}
		synchronized (piperTaskQueue) {piperTaskQueue.notify();}

		return true;
	}

	public void startProcess(int processCount) throws IOException {
		//Instance count should not be more than 2
		for (int index = 0; index < processCount; index++) {
			PiperProcess process;
			try {
				process = PiperProcess.start(piperPath, modelLocal.getOnnx().toPath());
				triggerOnPiperProcessStart(process);
			} catch (IOException e) {
				log.error("Failed to start PiperProcess");
				throw e;
			}

			process.onExit().addListener(() -> triggerOnPiperProcessExit(process), MoreExecutors.directExecutor());

			processMap.put(process.getPid(), process);
			synchronized (processMap) {processMap.notify();}
		}
	}
	//Process message queue

	@SneakyThrows(InterruptedException.class)
	public void processPiperTask() {
		while (!processPiperTaskThread.isInterrupted()) {

			while (piperTaskQueue.isEmpty()) {
				synchronized (piperTaskQueue) {piperTaskQueue.wait();}
			}

			while (processMap.isEmpty()) {
				synchronized (processMap) {processMap.wait();}
			}

			boolean polled = false;

			// using iterator to loop, so if an invalid PiperProcess is found we can remove.
			// find alive and non-locked process to dispatch piperTask to
			Iterator<Map.Entry<Long, PiperProcess>> iter = processMap.entrySet().iterator();
			while (iter.hasNext()) {
				var keyValue = iter.next();
				PiperProcess process = keyValue.getValue();

				if (!process.isAlive()) {
					// found crashed piper, remove
					iter.remove();
					triggerOnPiperProcessCrash(process);
					continue;
				}

				if (!process.isLocked()) {
					// found available piperProcess, dispatch
					PiperTask task = Objects.requireNonNull(piperTaskQueue.poll());

					if (!task.skip) {
						dispatchedQueue.add(task);
						dispatchTask(process, task);
					}
					else {
						log.trace("Skipped task before dispatching:{}", task.text);
					}

					polled = true;
					break;
				}
			}

			// processMap is not empty, but all processes are busy,
			// wait for any processes to finish task and become available
			if (!polled) {
				synchronized (processMap) {processMap.wait();}
			}

		}
	}

	private void dispatchTask(PiperProcess process, PiperTask task) {
		log.trace("dispatching task:{}", task.text);
		triggerOnPiperProcessBusy(process);
		String text = Objects.requireNonNull(task).getText();
		Integer id = Objects.requireNonNull(task.getVoiceID().getIntId());

		Consumer<byte[]> onComplete = ((byte[] audioClip) -> {

			if (audioClip != null && audioClip.length > 0) {
				// if there are parent tasks, wait for them to complete
				if (task.parent != null) {
					synchronized (task.parent) {
						// wait until parent is completed or skipped
						while (!(task.parent.completed || task.parent.skip)) {
							try {task.parent.wait();} catch (InterruptedException ignored) {}
						}
					}
				}

				if (!task.skip) {
					AudioInputStream inStream = new AudioInputStream(
						new ByteArrayInputStream(audioClip),
						audioFormat,
						audioClip.length);

					log.trace("Pumping into AudioEngine:{}", task.text);
					audioEngine.play(task.lineName, inStream, task.gainDB);

					task.completed = true;
				}
				else {
					log.trace("Skipped task after completion, discarded result:{}", task.text);
				}
			}
			else {
				log.warn("PiperProcess returned null and did not generate, likely due to crash.");
			}

			// task either skipped or completed from here on
			// even in the case audioClip failed to generate, we must notify that the generation is finished

			triggerOnPiperProcessDone(process);
			// alerts any child tasks waiting on parent
			synchronized (task) {task.notify();}
			// notify that this process has become available
			synchronized (processMap) {processMap.notify();}
			dispatchedQueue.remove(task);

		}); // onComplete lambda block

		log.trace("generating audio {} -> {}", process, text);
		process.generateAudio(id, text, onComplete);
	}

	/**
	 * cancels any remaining tasks going into {@link AudioEngine#play(String, AudioInputStream, Supplier)}
	 *
	 * @param lineName Audio line name for {@link AudioEngine#play(String, AudioInputStream, Supplier)}
	 */
	public void cancelLine(String lineName) {

		for (PiperTask task : piperTaskQueue) {
			if (task.getLineName().equals(lineName)) {
				log.trace("CancelLine - Setting skipped to true for:{}", task.text);
				task.skip = true;
			}
		}

		for (PiperTask task : dispatchedQueue) {
			if (task.getLineName().equals(lineName)) {
				log.trace("CancelLine - Setting skipped to true for:{}", task.text);
				task.skip = true;
			}
		}

	}

	public void cancelConditional(Predicate<String> condition) {
		piperTaskQueue.forEach(task -> {
			if (condition.test(task.getLineName())) {
				log.trace("CancelConditional - Setting skipped to true for:{}", task.text);
				task.skip = true;
			}
		});

		dispatchedQueue.forEach(task -> {
			if (condition.test(task.getLineName())) {
				log.trace("CancelConditional - Setting skipped to true for:{}", task.text);
				task.skip = true;
			}
		});
	}

	public void cancelAll() {
		log.trace("CancelAll - Setting skipped true for all tasks.");
		piperTaskQueue.forEach(task -> task.skip = true);
		dispatchedQueue.forEach(task -> task.skip = true);
	}

	public int countAlive() {
		int result = 0;
		for (PiperProcess process : processMap.values()) {
			if (process.isAlive()) result++;
		}
		return result;
	}

	/**
	 * @param listener This is not called on the client thread, please be careful.
	 */
	public void addPiperListener(PiperProcessLifetimeListener listener) {
		piperProcessLifetimeListeners.add(listener);
	}

	public void removePiperListener(PiperProcessLifetimeListener listener) {
		piperProcessLifetimeListeners.remove(listener);
	}

	private void triggerOnPiperProcessBusy(PiperProcess process) {
		for (PiperProcessLifetimeListener listener : piperProcessLifetimeListeners) {
			listener.onPiperProcessBusy(process);
		}
	}

	private void triggerOnPiperProcessCrash(PiperProcess process) {
		for (PiperProcessLifetimeListener listener : piperProcessLifetimeListeners) {
			listener.onPiperProcessCrash(process);
		}
	}

	private void triggerOnPiperProcessDone(PiperProcess process) {
		for (PiperProcessLifetimeListener listener : piperProcessLifetimeListeners) {
			listener.onPiperProcessDone(process);
		}
	}

	private void triggerOnPiperProcessStart(PiperProcess process) {
		for (PiperProcessLifetimeListener listener : piperProcessLifetimeListeners) {
			listener.onPiperProcessStart(process);
		}
	}

	private void triggerOnPiperProcessExit(PiperProcess process) {
		for (PiperProcessLifetimeListener listener : piperProcessLifetimeListeners) {
			listener.onPiperProcessExit(process);
		}
	}

	@Override
	public String toString() {
		return String.format("Piper for %s with %d active processes", getModelLocal().getModelName(), countAlive());
	}

	// Renamed from TTSItem, decoupled from dependencies
	@Data
	private static class PiperTask {
		final String text;
		final VoiceID voiceID;
		final Supplier<Float> gainDB;
		final String lineName;
		final PiperTask parent;

		volatile boolean skip = false;
		volatile boolean completed = false;

		public PiperTask(String text, VoiceID voiceID, Supplier<Float> gainDB, String lineName, PiperTask parent) {
			this.text = text;
			this.voiceID = voiceID;
			this.gainDB = gainDB;
			this.lineName = lineName;
			this.parent = parent;
		}
	}

	public interface PiperProcessLifetimeListener {
		default void onPiperProcessStart(PiperProcess process) {}

		default void onPiperProcessExit(PiperProcess process) {}

		// Busy generating voices
		default void onPiperProcessBusy(PiperProcess process) {}

		// Done generating voices
		default void onPiperProcessDone(PiperProcess process) {}

		default void onPiperProcessCrash(PiperProcess process) {}
	}
}
