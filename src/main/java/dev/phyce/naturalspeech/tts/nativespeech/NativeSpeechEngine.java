package dev.phyce.naturalspeech.tts.nativespeech;

import dev.phyce.naturalspeech.tts.AudioPlayer;
import dev.phyce.naturalspeech.tts.AudioQueue;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.IntSupplier;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NativeSpeechEngine {

	@Getter
	private final ConcurrentHashMap<String, AudioQueue> namedAudioQueueMap = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<SpeechTask> taskQueue = new ConcurrentLinkedQueue<>();

	private final AudioPlayer audioPlayer = new AudioPlayer();
	private final IntSupplier dialogGenSupplier;

	private final NativeSpeechProcess process;
	private final Thread processTaskThread;
	private final Thread processAudioQueueThread;

	private NativeSpeechEngine(NativeSpeechProcess process, IntSupplier dialogGenSupplier) {
		this.process = process;
		this.dialogGenSupplier = dialogGenSupplier;

		processTaskThread = new Thread(this::processTask, "NativeSpeechEngine::processTask Thread");
		processTaskThread.start();

		processAudioQueueThread =
			new Thread(this::processAudioQueue, "NativeSpeechEngine::processAudioQueue Thread");
		processAudioQueueThread.start();
	}

	public static NativeSpeechEngine start(IntSupplier dialogGenSupplier) throws IOException {
		return new NativeSpeechEngine(NativeSpeech.startProcess(), dialogGenSupplier);
	}

	public List<NativeVoice> getVoices() {
		return process.getVoices();
	}

	public boolean isAlive() {
		return process.isAlive();
	}

	public void speak(String text, VoiceID voiceID, float volume, String audioQueueName, int generation) {
		if (!process.isAlive()) {
			log.warn("System speech process is not running, dropping: {}", text);
			return;
		}

		taskQueue.add(new SpeechTask(text, voiceID, volume, audioQueueName, generation));
		synchronized (taskQueue) {taskQueue.notify();}
	}

	private void processTask() {
		while (!processTaskThread.isInterrupted()) {
			if (taskQueue.isEmpty()) {
				synchronized (taskQueue) {
					try {
						taskQueue.wait();
					}
					catch (InterruptedException e) {
						return;
					}
				}
				continue; // double check emptiness after notify
			}

			SpeechTask task = taskQueue.poll();
			if (task == null) continue;

			byte[] audioClip;
			try {
				audioClip = process.generateAudio(task.getVoiceID().getId(), task.getText());
			}
			catch (IOException e) {
				log.error("System speech failed, stopping engine", e);
				return;
			}

			if (audioClip == null || audioClip.length == 0) continue;

			if (task.generation >= 0 && task.generation != dialogGenSupplier.getAsInt()) {
				log.trace("Dropping stale audio for {} (gen {} != current {})",
					task.audioQueueName, task.generation, dialogGenSupplier.getAsInt());
				continue;
			}

			AudioQueue audioQueue =
				namedAudioQueueMap.computeIfAbsent(task.audioQueueName, name -> new AudioQueue());
			audioQueue.queue.add(new AudioQueue.AudioTask(audioClip, task.getVolume()));

			synchronized (namedAudioQueueMap) {namedAudioQueueMap.notify();}
		}
	}

	private void processAudioQueue() {
		while (!processAudioQueueThread.isInterrupted()) {
			synchronized (namedAudioQueueMap) {
				try {
					namedAudioQueueMap.wait();
				}
				catch (InterruptedException e) {
					return;
				}
			}

			namedAudioQueueMap.forEach((queueName, audioQueue) -> {
				if (!audioQueue.isPlaying() && !audioQueue.queue.isEmpty()) {
					audioQueue.setPlaying(true);

					new Thread(() -> {
						try {
							AudioQueue.AudioTask task;
							while ((task = audioQueue.queue.poll()) != null) {
								audioPlayer.playClip(task.getAudioClip(), task.getVolume(), queueName);
							}
						} finally {
							audioQueue.setPlaying(false);
						}
					}, String.format("NativeSpeechEngine AudioPlayer Thread for %s", queueName)).start();
				}
			});
		}
	}

	public void silenceQueue(String queueName) {
		taskQueue.removeIf(task -> queueName.equals(task.getAudioQueueName()));
		AudioQueue audioQueue = namedAudioQueueMap.get(queueName);
		if (audioQueue != null) {
			audioQueue.queue.clear();
		}
		audioPlayer.stopQueue(queueName);
	}

	public void clearQueue() {
		taskQueue.clear();
		namedAudioQueueMap.values().forEach(audioQueue -> audioQueue.queue.clear());
	}

	public void stop() {
		audioPlayer.stop();
		process.stop();
		clearQueue();

		processAudioQueueThread.interrupt();
		processTaskThread.interrupt();
		synchronized (taskQueue) {taskQueue.notifyAll();}
		synchronized (namedAudioQueueMap) {namedAudioQueueMap.notifyAll();}
	}

	@Override
	public String toString() {
		return String.format("System speech with %d voices", getVoices().size());
	}

	@Value
	@AllArgsConstructor
	private static class SpeechTask {
		String text;
		VoiceID voiceID;
		float volume;
		String audioQueueName;
		int generation;
	}
}
