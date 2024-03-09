package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

// Renamed from TTSModel
@Slf4j
public class Piper {
	@Getter
	private final Map<Long, PiperProcess> instances = new HashMap<>();
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

	// decoupled and can be called anywhere now
	private Piper(ModelRepository.ModelLocal modelLocal, Path piperPath, int instanceCount) throws IOException {
		this.modelLocal = modelLocal;
		this.piperPath = piperPath;

		audioPlayer = new AudioPlayer();

		//Instance count should not be more than 2
		for(int index = 0; index < instanceCount; index++) {
			PiperProcess instance;
			try {
				instance = PiperProcess.start(piperPath, modelLocal.getOnnx().toPath());
			} catch(IOException e) {
				// clean-up stray instances before throwing
				instances.forEach((pid, piperProcess) -> piperProcess.stop());
				instances.clear();
				throw e;
			}
			instances.put(instance.getPid(), instance);
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
		while(!processPiperTaskThread.isInterrupted()) {
			if(piperTaskQueue.isEmpty()) {
				synchronized(piperTaskQueue) {
					try {
						piperTaskQueue.wait();
					} catch(InterruptedException e) {
						return; // just exit on interrupt
					}
				}
				continue; // double check emptiness after notify.
			}

			PiperTask task = piperTaskQueue.poll();

			for(PiperProcess instance : instances.values()) {
				if(!instance.getPiperLocked().get()) {
					byte[] audioClip = instance.generateAudio(task.getText(), task.getVoiceID().getPiperVoiceID());
					if(audioClip != null && audioClip.length > 0) {
						AudioQueue audioQueue =
							namedAudioQueueMap.computeIfAbsent(task.audioQueueName, audioQueueName -> new AudioQueue());
						audioQueue.queue.add(new AudioQueue.AudioTask(audioClip, task.getVolume()));

						synchronized(namedAudioQueueMap) {namedAudioQueueMap.notify();}

						break; // will only break for the nearest scoped loop, aka the for, not while
					}
				}
			}
		}
	}

	public void processAudioQueue() {
		while(!processAudioQueueThread.isInterrupted()) {

			synchronized(namedAudioQueueMap) {
				try {
					namedAudioQueueMap.wait();
				} catch(InterruptedException e) {
					return;
				}
			}

			namedAudioQueueMap.forEach((queueName, audioQueue) -> {

				if(!audioQueue.isPlaying() && !audioQueue.queue.isEmpty()) {
					audioQueue.setPlaying(true);

					// start a thread for each named audio queue
					new Thread(() -> {
						try {
							AudioQueue.AudioTask task;
							while((task = audioQueue.queue.poll()) != null) {
								audioPlayer.playClip(task.getAudioClip(), task.getVolume());
							}
						} finally {
							audioQueue.setPlaying(false);
						}
					}).start();
				}

			});
		}
	}

	// Refactored to decouple from dependencies
	public void speak(String text, VoiceID voiceID, float volume, String audioQueueName) throws IOException {
		if(countAlive() == 0) {
			throw new IOException("No active PiperProcess instances running for " + voiceID.getModelName());
		}

		if(piperTaskQueue.size() > 10) {
			log.info("Cleared queue because queue size is too large. (more then 10)");
			clearQueue();
		}

		piperTaskQueue.add(new PiperTask(text, voiceID, volume, audioQueueName));
		synchronized(piperTaskQueue) {piperTaskQueue.notify();}
	}

	public void clearQueue() {
		piperTaskQueue.clear();
		namedAudioQueueMap.values().forEach(audioQueue -> {
			audioQueue.queue.clear();
		});
	}

	public int countAlive() {
		int result = 0;
		for(PiperProcess instance : instances.values()) {
			if(instance.isAlive()) result++;
		}
		return result;
	}

	public void stopAll() {
		audioPlayer.stop();

		for(PiperProcess instance : instances.values()) {
			instance.stop();
		}
		instances.clear();

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
