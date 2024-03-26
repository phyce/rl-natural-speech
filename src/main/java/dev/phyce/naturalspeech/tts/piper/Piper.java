package dev.phyce.naturalspeech.tts.piper;

import dev.phyce.naturalspeech.tts.AudioPlayer;
import dev.phyce.naturalspeech.tts.AudioQueue;
import dev.phyce.naturalspeech.tts.ModelRepository;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

// Renamed from TTSModel
@Slf4j
public class Piper {
	@Getter
	private final Map<Long, PiperProcess> processMap = new HashMap<>();
	@Getter
	private final ConcurrentHashMap<String, AudioQueue> namedAudioQueueMap = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<PiperTask> piperTaskQueue = new ConcurrentLinkedQueue<>();
	private final AudioPlayer audioPlayer;

	@Getter
	private final ModelRepository.ModelLocal modelLocal;
	@Getter
	private final Path piperPath;
	private final Thread processPiperTaskThread;
	private final Thread processAudioQueueThread;

	private final List<PiperProcessLifetimeListener> piperProcessLifetimeListeners = new ArrayList<>();

	/**
	 * Create a piper and immediately start
	 *
	 * @throws IOException if piper fails to start an IOException will be thrown. (because stdin cannot be opened).
	 */
	public static Piper start(ModelRepository.ModelLocal modelLocal, Path piperPath, int instanceCount)
		throws IOException {
		return new Piper(modelLocal, piperPath, instanceCount);
	}

	private Piper(ModelRepository.ModelLocal modelLocal, Path piperPath, int instanceCount) throws IOException {
		this.modelLocal = modelLocal;
		this.piperPath = piperPath;

		audioPlayer = new AudioPlayer();

		startMore(instanceCount);

		processPiperTaskThread =
			new Thread(this::processPiperTask, String.format("[%s] Piper::processPiperTask Thread", this));
		processPiperTaskThread.start();

		processAudioQueueThread =
			new Thread(this::processAudioQueue, String.format("[%s] Piper::processAudioQueue Thread", this));
		processAudioQueueThread.start();
	}

	public void startMore(int instanceCount) throws IOException {
		//Instance count should not be more than 2
		for (int index = 0; index < instanceCount; index++) {
			PiperProcess process;
			try {
				process = PiperProcess.start(piperPath, modelLocal.getOnnx().toPath());
				triggerOnPiperProcessStart(process);
			} catch (IOException e) {
				// clean-up stray instances before throwing
				processMap.forEach((pid, piperProcess) -> piperProcess.stop());
				processMap.clear();
				throw e;
			}
			process.onExit().thenAccept(p -> {
				triggerOnPiperProcessExit(p);
			});
			processMap.put(process.getPid(), process);
		}
	}

	//Process message queue
	public void processPiperTask() {
		TaskIteration:
		while (!processPiperTaskThread.isInterrupted()) {
			if (piperTaskQueue.isEmpty()) {
				try { Thread.sleep(5); }
				catch(InterruptedException e) {
//					throw new RuntimeException(e);
					return;
				}
				continue TaskIteration;
			}

			PiperTask task = piperTaskQueue.poll();
			if (task == null) continue TaskIteration;

			Iterator<Long> processMapItem = processMap.keySet().iterator();
			ProcessIteration:
			while (processMapItem.hasNext()) {
				long processId = processMapItem.next();
				PiperProcess process = processMap.get(processId);

				if (!process.isAlive()) {
					processMapItem.remove();
					triggerOnPiperProcessCrash(process);
					continue ProcessIteration;
				}

				if (process.getPiperLocked().get()) continue ProcessIteration;

				CompletableFuture<AudioQueue.AudioTask> audioClipFuture = generateClip(process, task);

				AudioQueue audioQueue = namedAudioQueueMap.computeIfAbsent(task.audioQueueName,
					audioQueueName -> new AudioQueue());

				synchronized (audioQueue) { audioQueue.queue.add(audioClipFuture); }
				continue TaskIteration;
			}
		}
	}

	public CompletableFuture<AudioQueue.AudioTask> generateClip(PiperProcess process, PiperTask task) {
		CompletableFuture<AudioQueue.AudioTask> future = new CompletableFuture<>();

		new Thread(() -> {
			triggerOnPiperProcessBusy(process);
			try {
				byte[] audioClip = process.generateAudio(task.getText(), task.getVoiceID().getPiperVoiceID());

				if (audioClip != null && audioClip.length > 0) {
					AudioQueue.AudioTask audioTask = new AudioQueue.AudioTask(audioClip, task.getGainDb());
					future.complete(audioTask);
				} else {
					future.complete(null); // or consider using future.completeExceptionally(new SomeException("Error message"));
				}
			} catch (IOException | InterruptedException e) {
				log.error("{} had an unexpected exit, either crashed or terminated by user.", process);
				triggerOnPiperProcessCrash(process);
				process.stop();
				future.completeExceptionally(e);
			}
			triggerOnPiperProcessDone(process);
		}).start();

		return future;
	}

	public void processAudioQueue() {
		while (!processAudioQueueThread.isInterrupted()) {
			namedAudioQueueMap.forEach((queueName, audioQueue) -> {
				if (!audioQueue.isPlaying() && !audioQueue.queue.isEmpty()) {
					new Thread(() -> processAudioFuture(audioQueue, queueName)).start();
				} else {
					try { Thread.sleep(5); }
					catch(InterruptedException e) {
						audioQueue.setPlaying(false);
//						throw new RuntimeException(e);
						return;
					}
				}
			});
		}
	}

	public void processAudioFuture(AudioQueue audioQueue, String queueName) {
		audioQueue.setPlaying(true);
		try {
			CompletableFuture<AudioQueue.AudioTask> future;
			while ((future = audioQueue.queue.poll()) != null) {
				// Wait for the future to complete and then play the audio clip
				future.thenAccept(audioTask -> {
					if (audioTask != null) {
						audioPlayer.playClip(audioTask.getAudioClip(), audioTask.getVolume());
					}
				}).join(); // Wait for completion
			}
		} finally {
			audioQueue.setPlaying(false);
		}
	}

	// Refactored to decouple from dependencies
	public void speak(String text, VoiceID voiceID, float volumnDb, String audioQueueName) throws IOException {
		if (countAlive() == 0) {
			throw new IOException("No active PiperProcess instances running for " + voiceID.getModelName());
		}

		if (piperTaskQueue.size() > 10) {
			log.info("Cleared queue because queue size is too large. (more then 10)");
			clearQueue();
		}

		piperTaskQueue.add(new PiperTask(text, voiceID, volumnDb, audioQueueName));
		synchronized (piperTaskQueue) {piperTaskQueue.notify();}
	}

	public void clearQueue() {
		piperTaskQueue.clear();
		namedAudioQueueMap.values().forEach(audioQueue -> {
			audioQueue.queue.clear();
		});
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

		processAudioQueueThread.interrupt();
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
		float gainDb;
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
