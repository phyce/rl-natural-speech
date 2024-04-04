package dev.phyce.naturalspeech.tts.piper;

import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.audio.AudioPlayer;
import dev.phyce.naturalspeech.tts.VoiceID;
import static dev.phyce.naturalspeech.utils.CommonUtil.silentInterruptHandler;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import javax.sound.sampled.AudioInputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

// Renamed from TTSModel
@Slf4j
public class Piper {
	@Getter
	private final Map<Long, PiperProcess> processMap = new HashMap<>();
	private final ConcurrentLinkedQueue<PiperTask> piperTaskQueue = new ConcurrentLinkedQueue<>();
	private final AudioPlayer audioPlayer;
	private final AudioEngine audioEngine;

	@Getter
	private final PiperRepository.ModelLocal modelLocal;
	@Getter
	private final Path piperPath;
	private final Thread processPiperTaskThread;

	private final List<PiperProcessLifetimeListener> piperProcessLifetimeListeners = new ArrayList<>();


	private Piper(AudioEngine audioEngine, PiperRepository.ModelLocal modelLocal, Path piperPath, int instanceCount)
		throws IOException {
		this.audioEngine = audioEngine;
		this.modelLocal = modelLocal;
		this.piperPath = piperPath;

		audioPlayer = new AudioPlayer();

		startMore(instanceCount);

		processPiperTaskThread =
			new Thread(this::processPiperTask, String.format("[%s] Piper::processPiperTask Thread", this));
		processPiperTaskThread.setUncaughtExceptionHandler(silentInterruptHandler);
		processPiperTaskThread.start();

	}

	/**
	 * Create a piper and immediately start
	 *
	 * @throws IOException if piper fails to start an IOException will be thrown. (because stdin cannot be opened).
	 */
	public static Piper start(AudioEngine audioEngine, PiperRepository.ModelLocal modelLocal, Path piperPath,
							  int instanceCount)
		throws IOException {
		return new Piper(audioEngine, modelLocal, piperPath, instanceCount);
	}

	public void speak(String text, VoiceID voiceID, float volume, String audioQueueName) throws IOException {
		if (countAlive() == 0) {
			throw new IOException("No active PiperProcess instances running for " + voiceID.getModelName());
		}

		if (piperTaskQueue.size() > 10) {
			log.warn("Cleared queue because queue size is too large. (more then 10)");
			clearQueue();
		}

		List<String> segments = TextUtil.splitSentenceV2(text);
		//		List<String> segments = List.of(text);
		if (segments.size() > 1) {
			log.trace("Piper speech segmentation: {}", TextUtil.sentenceSegmentPrettyPrint(segments));
		}
		for (String segment : segments) {
			piperTaskQueue.add(new PiperTask(segment, voiceID, volume, audioQueueName));
		}
		synchronized (piperTaskQueue) {piperTaskQueue.notify();}
	}

	public void startMore(int instanceCount) throws IOException {
		//Instance count should not be more than 2
		for (int index = 0; index < instanceCount; index++) {
			PiperProcess process;
			try {
				process = PiperProcess.start(piperPath, modelLocal.getOnnx().toPath());
				triggerOnPiperProcessStart(process);
			} catch (IOException e) {
				log.error("Failed to start PiperProcess");
				throw e;
			}

			process.onExit().thenAccept(p -> {
				triggerOnPiperProcessExit(p);
			});

			processMap.put(process.getPid(), process);
		}
	}
	//Process message queue

	@SneakyThrows(InterruptedException.class)
	public void processPiperTask() {
		while (!processPiperTaskThread.isInterrupted()) {
			if (piperTaskQueue.isEmpty()) {
				synchronized (piperTaskQueue) {piperTaskQueue.wait();}
				continue; // double check emptiness after notify.
			}

			if (processMap.isEmpty()) {
				log.error("Processing thread running while no PiperProcess is alive. Stopping.");
				return;
			}

			// using iterator to loop, so if an invalid PiperProcess is found we can remove.
			boolean polled = false;
			Iterator<Map.Entry<Long, PiperProcess>> iter = processMap.entrySet().iterator();
			while (iter.hasNext()) {
				var keyValue = iter.next();
				long pid = keyValue.getKey();
				PiperProcess process = keyValue.getValue();

				if (!process.isAlive()) {
					iter.remove();
					triggerOnPiperProcessCrash(process);
					continue;
				}

				// Found available task
				if (!process.isLocked()) {
					triggerOnPiperProcessBusy(process);
					PiperTask task = piperTaskQueue.poll();
					polled = true;
					String text = Objects.requireNonNull(task).getText();
					Integer id = Objects.requireNonNull(task.getVoiceID().getIntId());
					Consumer<byte[]> onComplete = (byte[] audioClip) -> {
						if (audioClip != null && audioClip.length > 0) {

							AudioInputStream inStream = new AudioInputStream(
								new ByteArrayInputStream(audioClip),
								AudioEngine.getFormat(),
								audioClip.length);

							audioEngine.play(task.audioQueueName, inStream, () -> task.volume);

						}
						else {
							log.warn("PiperProcess returned null and did not generate, likely due to crash.");
						}

						// even in the case audioClip failed to generate, we must notify that the generation is finished
						synchronized (processMap) {processMap.notify();}
						triggerOnPiperProcessDone(process);
					};

					log.trace("{} -> {}", pid, text);
					process.generateAudio(text, id, onComplete);
					break;
				}
			}

			// all processes are busy, wait for PiperProcess to finish task, and try again.
			if (!polled) {
				synchronized (processMap) {processMap.wait();}
			}

		}
	}

	// Refactored to decouple from dependencies

	public void clearQueue() {
		piperTaskQueue.clear();
	}

	public int countAlive() {
		int result = 0;
		for (PiperProcess process : processMap.values()) {
			if (process.isAlive()) result++;
		}
		return result;
	}

	public void stop() {
		audioPlayer.stop();

		for (PiperProcess instance : processMap.values()) {
			instance.stop();
		}
		processMap.clear();

		// clear task and audio queue on stop
		clearQueue();

		processPiperTaskThread.interrupt();
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
	@Value
	@AllArgsConstructor
	private static class PiperTask {
		String text;
		VoiceID voiceID;
		float volume;
		String audioQueueName;
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
