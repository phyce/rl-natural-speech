package dev.phyce.naturalspeech.texttospeech.engine.piper;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.utils.SuccessCallback;
import dev.phyce.naturalspeech.utils.Texts;
import static dev.phyce.naturalspeech.utils.Threads.silentInterruptHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

// Renamed from TTSModel
@Slf4j
public class PiperModel {

	private final PluginExecutorService pluginExecutorService;
	private final AudioEngine audioEngine;

	@Getter
	private final ConcurrentHashMap<Long, PiperProcess> processMap = new ConcurrentHashMap<>();
	private final Vector<PiperTask> dispatchedTasks = new Vector<>();
	private final BlockingQueue<PiperProcess> idleProcessQueue = Queues.newLinkedBlockingQueue();
	private final BlockingQueue<PiperTask> piperTasksQueue = Queues.newLinkedBlockingQueue();


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

	public PiperModel(
		PluginExecutorService pluginExecutorService,
		AudioEngine audioEngine, PiperRepository.ModelLocal modelLocal, Path piperPath
	) {
		this.pluginExecutorService = pluginExecutorService;
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
		for (PiperProcess process : processMap.values()) {
			process.destroy();
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

		List<String> segments;
		if (text.length() > 50) {
			segments = Texts.splitSentence(text);
		}
		else {
			segments = List.of(text);
		}
		if (segments.size() > 1) {
			log.trace("Piper speech segmentation: {}", Texts.sentenceSegmentPrettyPrint(segments));

			// build a linked list of tasks
			PiperTask parent = null;
			for (String segment : segments) {
				PiperTask child = new PiperTask(segment, voiceID, gainDB, lineName, parent);
				log.trace("{} task added. lineName:{} parent:{} text:{} ", voiceID, lineName, parent != null, segment);
				piperTasksQueue.add(child);
				parent = child;
			}
		}
		else {
			log.trace("{} task Added lineName:{} parent:null text:{}", voiceID, lineName, text);
			piperTasksQueue.add(new PiperTask(text, voiceID, gainDB, lineName, null));
		}

		return true;
	}

	public void startProcess(int processCount) throws IOException {
		//Instance count should not be more than 2
		for (int index = 0; index < processCount; index++) {
			PiperProcess process;
			try {
				process = PiperProcess.start(pluginExecutorService, piperPath, modelLocal.getOnnx().toPath());
				triggerOnPiperProcessStart(process);
			} catch (IOException e) {
				log.error("Failed to start PiperProcess");
				throw e;
			}

			Futures.addCallback(process.onExit(),
				(SuccessCallback<PiperProcess>) result -> triggerOnPiperProcessExit(process),
				MoreExecutors.directExecutor()
			);
			Futures.addCallback(process.onCrash(),
				(SuccessCallback<PiperProcess>) result -> triggerOnPiperProcessCrash(process),
				MoreExecutors.directExecutor()
			);

			processMap.put(process.getPid(), process);
			idleProcessQueue.add(process);
		}
	}

	//Process message queue
	@SneakyThrows(InterruptedException.class)
	public void processPiperTask() {
		while (!processPiperTaskThread.isInterrupted()) {

			// blocks until taken
			PiperTask task = piperTasksQueue.take();

			PiperProcess process;
			do {
				// inspect the process taken for invalids
				process = idleProcessQueue.take();

				// is it locked? severe: should never have locked a process in idleQueue
				if (process.isLocked()) {
					log.error("Found locked PiperProcess in idleProcessDeque");
				}
				else {
					break;
				}

				// is it dead?
				if (!process.isAlive()) {
					processMap.remove(process.getPid());
					triggerOnPiperProcessCrash(process);
				}
			} while (process.isLocked() || !process.isAlive());

			// found available piperProcess, dispatch
			if (task.skip) {
				log.trace("Skipped task before dispatching:{}", task.text);
			}
			else {
				dispatchTask(process, task);
			}
		}
	}

	private void dispatchTask(PiperProcess process, PiperTask task) {
		log.trace("dispatching task:{}", task.text);
		dispatchedTasks.add(task);
		triggerOnPiperProcessBusy(process);
		String text = Objects.requireNonNull(task).getText();
		Integer id = Objects.requireNonNull(task.getVoiceID().getIntId());

		log.trace("generating audio {} -> {}", process, text);

		ListenableFuture<byte[]> onDone = process.generateAudio(id, text);
		Futures.addCallback(onDone, new TaskDoneCallback(process, task), pluginExecutorService);
	}

	/**
	 * cancels any remaining tasks going into {@link AudioEngine#play(String, AudioInputStream, Supplier)}
	 *
	 * @param lineName Audio line name for {@link AudioEngine#play(String, AudioInputStream, Supplier)}
	 */
	public void cancelLine(String lineName) {

		for (PiperTask task : piperTasksQueue) {
			if (task.getLineName().equals(lineName)) {
				log.trace("CancelLine - Setting skipped to true for:{}", task.text);
				task.skip = true;
			}
		}

		for (PiperTask task : dispatchedTasks) {
			if (task.getLineName().equals(lineName)) {
				log.trace("CancelLine - Setting skipped to true for:{}", task.text);
				task.skip = true;
			}
		}

	}

	public void cancelConditional(Predicate<String> condition) {
		piperTasksQueue.forEach(task -> {
			if (condition.test(task.getLineName())) {
				log.trace("CancelConditional - Setting skipped to true for:{}", task.text);
				task.skip = true;
			}
		});

		dispatchedTasks.forEach(task -> {
			if (condition.test(task.getLineName())) {
				log.trace("CancelConditional - Setting skipped to true for:{}", task.text);
				task.skip = true;
			}
		});
	}

	public void cancelAll() {
		log.trace("CancelAll - Setting skipped true for all tasks.");
		piperTasksQueue.forEach(task -> task.skip = true);
		dispatchedTasks.forEach(task -> task.skip = true);
	}

	public int countAlive() {
		int result = 0;
		for (PiperProcess process : processMap.values()) {
			if (process.isAlive()) result++;
		}
		return result;
	}

	public int getTaskQueueSize() {
		return piperTasksQueue.size();
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

	private class TaskDoneCallback implements FutureCallback<byte[]> {

		private final PiperProcess process;
		private final PiperTask task;

		private TaskDoneCallback(PiperProcess process, PiperTask task) {
			this.process = process;
			this.task = task;
		}

		@Override
		public void onSuccess(byte[] audioClip) {
			// add the process back to idleProcessQueue, it's now idle again
			idleProcessQueue.add(process);
			triggerOnPiperProcessDone(process);

			// synchronize with parent task audio playback
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
				task.completed = true;
				log.warn("PiperProcess returned null and did not generate, likely due to crash.");
			}

			// task either skipped or completed from here on
			// even in the case audioClip failed to generate, we must notify that the generation is finished

			// alerts any child tasks waiting on parent
			synchronized (task) {task.notify();}
			dispatchedTasks.remove(task);
		}

		@Override
		public void onFailure(@NonNull Throwable t) {
			log.error("PiperProcess exception while generating audio for:{}", task.text, t);
			idleProcessQueue.add(process);
			triggerOnPiperProcessDone(process);
			// alerts any child tasks waiting on parent
			task.completed = true;
			synchronized (task) {task.notify();}
			dispatchedTasks.remove(task);
		}

	}
}
