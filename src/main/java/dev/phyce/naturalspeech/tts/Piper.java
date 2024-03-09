package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

// Renamed from TTSModel
@Slf4j
public class Piper {
	@Getter
	private final List<PiperProcess> instances = new ArrayList<>();
	@Getter
	private final ConcurrentHashMap<String, AudioQueue> audioQueues = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<PiperTask> piperTaskQueue = new ConcurrentLinkedQueue<>();
	private final AudioPlayer audioPlayer;

	@Getter
	private final ModelRepository.ModelLocal modelLocal;
	@Getter
	private final Path piperPath;
	private final Thread processPiperTaskThread;
	private final Thread processAudioQueueThread;

	// decoupled and can be called anywhere now
	private Piper(ModelRepository.ModelLocal modelLocal, Path piperPath, int instanceCount) throws IOException {
		this.modelLocal = modelLocal;
		this.piperPath = piperPath;

		audioPlayer = new AudioPlayer();

		//Instance count should not be more than 2
		for (int index = 0; index < instanceCount; index++) {
			try {
				PiperProcess instance = PiperProcess.start(piperPath, modelLocal.getOnnx().toPath());
				instances.add(instance);
			} catch (IOException e) {
				throw e;
			}
		}

		processPiperTaskThread = new Thread(this::processPiperTask);
		processPiperTaskThread.start();

		processAudioQueueThread = new Thread(this::processAudioQueue);
		processAudioQueueThread.start();
	}

	public static Piper start(ModelRepository.ModelLocal modelLocal, Path piperPath, int instanceCount)
		throws IOException {
		return new Piper(modelLocal, piperPath, instanceCount);
	}

	//Process message queue
	public void processPiperTask() {
		while (!processPiperTaskThread.isInterrupted()) {
			if (piperTaskQueue.isEmpty()) continue;

			PiperTask task = piperTaskQueue.poll();

			for (PiperProcess instance : instances) {
				if (!instance.getPiperLocked().get()) {
					byte[] audioClip = instance.generateAudio(task.getText(), task.getVoiceID().getPiperVoiceID());
					if (audioClip != null && audioClip.length > 0) {
						AudioQueue audioQueue = audioQueues.computeIfAbsent(task.audioQueueName, audioQueueName -> new AudioQueue());
						audioQueue.queue.add(new AudioQueue.AudioTask(audioClip, task.getVolume()));
						break; // will only break for the nearest scoped loop, aka the for, not while
					}
				}
			}
		}
	}

	public void processAudioQueue() {
		while (!processAudioQueueThread.isInterrupted()) {
			audioQueues.forEach((queueName, audioQueue) -> {
				if (!audioQueue.queue.isEmpty() && !audioQueue.isPlaying().get()) {
					audioQueue.setPlaying(true);
					new Thread(() -> {
						try {
							AudioQueue.AudioTask task;
							while ((task = audioQueue.queue.poll()) != null) {
								audioPlayer.playClip(task.getAudioClip(), task.getVolume());
							}
						} finally {
							audioQueue.setPlaying(false);
						}
					}).start();
				}
			});

			try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	// Refactored to decouple from dependencies
	public void speak(String text, VoiceID voiceID, float volume, String audioQueueName) throws IOException {
		if (countProcessingInstances() == 0) throw new IOException("No active TTS engine instances running");
		if (piperTaskQueue.size() > 10) {
			log.info("Cleared queue because queue size is too large. (more then 10)");
			clearQueue();
		}

		piperTaskQueue.add(new PiperTask(text, voiceID, volume, audioQueueName));
	}

	public void clearQueue() {
		if (!piperTaskQueue.isEmpty()) piperTaskQueue.clear();
		audioQueues.values().forEach(audioQueue -> {
			if (!audioQueue.queue.isEmpty()) audioQueue.queue.clear();
		});
	}

	public int countProcessingInstances() {
		int result = 0;
		if (!instances.isEmpty()) {
			for (PiperProcess instance : instances) {
				if (instance.isProcessAlive()) result++;
			}
		}
		return result;
	}

	public void shutDown() {
		audioPlayer.shutDown();
		if (!instances.isEmpty()) {
			for (PiperProcess instance : instances) {
				instance.stop();
			}
		}
		processAudioQueueThread.interrupt();
		processPiperTaskThread.interrupt();
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


}
