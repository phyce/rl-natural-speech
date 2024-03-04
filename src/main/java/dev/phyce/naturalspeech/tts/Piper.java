package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

// Renamed from TTSModel
public class Piper implements Runnable {


	@Getter
	final private List<PiperProcess> instances = new ArrayList<>();
	@Getter
	private final ConcurrentHashMap<String, AudioQueue> audioQueues = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<PiperTask> piperTaskQueue = new ConcurrentLinkedQueue<>();
	private final AudioPlayer audioPlayer;
	@Getter
	ModelRepository.ModelLocal modelLocal;
	@Getter
	private final Path enginePath;

	public Piper(ModelRepository.ModelLocal modelLocal, Path enginePath, int instanceCount) {
		this.enginePath = enginePath;
		audioPlayer = new AudioPlayer();

		//Instance count should not be more than 2
		for (int index = 0; index < instanceCount; index++) {
			try {
				PiperProcess instance = new PiperProcess(enginePath, modelLocal.getOnnx().toPath());
				instances.add(instance);
			} catch (IOException | LineUnavailableException e) {
				throw new RuntimeException(e);
			}
		}

		new Thread(this).start();
		new Thread(this::processAudioQueue).start();
	}

	@Override
	//Process message queue
	public void run() {
		while (countRunningInstances() > 0) {
			if (piperTaskQueue.isEmpty()) {
				continue;
			}

			PiperTask task = piperTaskQueue.poll();

			messageSend:
			if (!instances.isEmpty()) {
				for (PiperProcess instance : instances) {
					if (!instance.getPiperLocked().get()) {
						byte[] audioClip =  instance.generateAudio(task.getText(), task.getVoiceID().getPiperVoiceID());
						if (audioClip.length > 0) {
							AudioQueue audioQueue = audioQueues.computeIfAbsent(task.audioQueueName, audioQueueName -> new AudioQueue());
							audioQueue.queue.add(new AudioQueue.AudioTask(audioClip, task.getVolume()));
							break messageSend;
						}
					}
				}
			}
		}
	}

	public void processAudioQueue() {
		while (countRunningInstances() > 0) {
			audioQueues.forEach((key, audioQueue) -> {
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

//	public ConcurrentLinkedQueue<AudioQueue.AudioTask> getAudioQueue(String key) {
//		return audioQueues.computeIfAbsent(key, k -> new AudioQueue()).queue;
//	}

	public synchronized void speak(String text, VoiceID voiceID, float volume, String audioQueueName) throws IOException {
		if (countRunningInstances() == 0) throw new IOException("No active TTS engine instances running");
		if (piperTaskQueue.size() > 10) clearQueue();


		piperTaskQueue.add(new PiperTask(text, voiceID, volume, audioQueueName));
	}

	public void clearQueue() {
		if (!piperTaskQueue.isEmpty()) {
			piperTaskQueue.clear();
		}
		audioQueues.values().forEach(audioQueue -> {
			if (!audioQueue.queue.isEmpty()) {
				audioQueue.queue.clear();
			}
		});
	}

	public int countRunningInstances() {
		int result = 0;
		if (!instances.isEmpty()) {
			for (PiperProcess instance : instances) {
				if (instance.isProcessing()) result++;
			}
		}
		return result;
	}

	public void shutDown() {
		audioPlayer.shutDown();
		if (!instances.isEmpty()) {
			for (PiperProcess instance : instances) {
				instance.shutDown();
			}
		}
	}

	@Value
	@AllArgsConstructor
	public static class PiperTask {
		String text;
		VoiceID voiceID;
		float volume;
		String audioQueueName;
	}


}
