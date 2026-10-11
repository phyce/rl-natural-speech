package dev.phyce.naturalspeech.tts.nativespeech;

import dev.phyce.naturalspeech.tts.AudioPlayer;
import dev.phyce.naturalspeech.tts.AudioQueue;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NativeSpeechEngine {

	private static final SpeechTask STOP = new SpeechTask("", null, 0, "", -1);

	@Getter
	private final Map<String, AudioQueue> namedAudioQueueMap = new ConcurrentHashMap<>();
	private final BlockingQueue<SpeechTask> tasks = new LinkedBlockingQueue<>();
	private final AudioPlayer audioPlayer = new AudioPlayer();
	private final IntSupplier dialogGen;
	private volatile NativeSpeechProcess process;
	private volatile boolean stopped;

	public NativeSpeechEngine(
		IntSupplier dialogGen,
		Consumer<NativeSpeechEngine> onReady,
		Consumer<NativeSpeechEngine> onCrash) {
		this.dialogGen = dialogGen;

		Thread worker = new Thread(() -> run(onReady, onCrash), "NativeSpeechEngine");
		worker.setDaemon(true);
		worker.start();
	}

	public List<NativeVoice> getVoices() {
		NativeSpeechProcess process = this.process;
		return process == null ? List.of() : process.getVoices();
	}

	public boolean isAlive() {
		NativeSpeechProcess process = this.process;
		return !stopped && process != null && process.isAlive();
	}

	public void speak(String text, VoiceID voiceID, float volume, String audioQueueName, int generation) {
		if (tasks.size() > 10) {
			log.info("Cleared system voice queue because it grew past 10 messages");
			tasks.clear();
		}
		tasks.add(new SpeechTask(text, voiceID, volume, audioQueueName, generation));
	}

	public void silenceQueue(String queueName) {
		tasks.removeIf(task -> queueName.equals(task.getAudioQueueName()));
		AudioQueue audioQueue = namedAudioQueueMap.get(queueName);
		if (audioQueue != null) {
			audioQueue.queue.clear();
		}
		audioPlayer.stopQueue(queueName);
	}

	public void clearQueue() {
		tasks.clear();
		namedAudioQueueMap.values().forEach(audioQueue -> audioQueue.queue.clear());
	}

	public void stop() {
		stopped = true;
		clearQueue();
		tasks.add(STOP);
		audioPlayer.stop();

		NativeSpeechProcess process = this.process;
		if (process != null) {
			process.stop();
		}
	}

	private void run(Consumer<NativeSpeechEngine> onReady, Consumer<NativeSpeechEngine> onCrash) {
		try {
			process = NativeSpeech.startProcess();
			if (stopped) {
				process.stop();
				return;
			}
			process.onExit().thenRun(() -> {
				if (!stopped) onCrash.accept(this);
			});
			onReady.accept(this);

			SpeechTask task;
			while ((task = tasks.take()) != STOP) {
				generate(task);
			}
		}
		catch (InterruptedException ignored) {
		}
		catch (IOException e) {
			if (stopped) return;
			log.error("System voices stopped working", e);
			onCrash.accept(this);
		}
	}

	private void generate(SpeechTask task) throws IOException {
		String systemName = systemName(task.getVoiceID());
		if (systemName == null) {
			log.warn("No system voice called {}", task.getVoiceID());
			return;
		}

		byte[] audio;
		try {
			audio = process.generateAudio(systemName, task.getText());
		}
		catch (RuntimeException e) {
			log.warn("System voice {} failed to speak: {}", task.getVoiceID(), task.getText(), e);
			return;
		}

		if (stopped || audio == null || audio.length == 0) return;
		if (task.getGeneration() >= 0 && task.getGeneration() != dialogGen.getAsInt()) return;

		AudioQueue audioQueue = namedAudioQueueMap.computeIfAbsent(task.getAudioQueueName(), name -> new AudioQueue());
		audioQueue.queue.add(new AudioQueue.AudioTask(audio, task.getVolume()));
		play(task.getAudioQueueName(), audioQueue);
	}

	private String systemName(VoiceID voiceID) {
		return process.getVoices().stream()
			.filter(voice -> voice.toVoiceID().equals(voiceID))
			.map(NativeVoice::getSystemName)
			.findFirst()
			.orElse(null);
	}

	private void play(String queueName, AudioQueue audioQueue) {
		synchronized (audioQueue) {
			if (audioQueue.isPlaying()) return;
			audioQueue.setPlaying(true);
		}

		Thread player = new Thread(() -> {
			AudioQueue.AudioTask clip;
			while (!stopped && (clip = nextClip(audioQueue)) != null) {
				try {
					audioPlayer.playClip(clip.getAudioClip(), clip.getVolume(), queueName);
				}
				catch (RuntimeException e) {
					log.error("System voice clip failed to play", e);
				}
			}
		}, "NativeSpeechEngine " + queueName);
		player.setDaemon(true);
		player.start();
	}

	private static AudioQueue.AudioTask nextClip(AudioQueue audioQueue) {
		synchronized (audioQueue) {
			AudioQueue.AudioTask clip = audioQueue.queue.poll();
			if (clip == null) {
				audioQueue.setPlaying(false);
			}
			return clip;
		}
	}

	@Value
	private static class SpeechTask {
		String text;
		VoiceID voiceID;
		float volume;
		String audioQueueName;
		int generation;
	}
}
