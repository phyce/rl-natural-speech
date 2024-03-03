package dev.phyce.naturalspeech.tts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.sound.sampled.LineUnavailableException;
import lombok.Getter;
import net.runelite.api.ChatMessageType;

public class TTSModel implements Runnable
{
	@Getter private String name;
	@Getter private Path path;
	@Getter private Path enginePath;
	@Getter final private List<TTSEngine> instances = new ArrayList<>();

	private AudioPlayer audio;
	@Getter private final ConcurrentHashMap<String, PlayerAudioQueue> audioQueues = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<TTSItem> messageQueue = new ConcurrentLinkedQueue<>();
	public TTSModel(String name, Path path, Path enginePath) {
		this(name, path, enginePath, 1);
	}
	public TTSModel(String name, Path path, Path enginePath, int instanceCount) {
		this.name = name;
		this.path = path;
		this.enginePath = enginePath;
		audio = new AudioPlayer();

		//Instance count should not be more than 2
		for(int index = 0; index < instanceCount; index++ ) {
			try {
				TTSEngine instance = new TTSEngine(enginePath, path);
				instances.add(instance);
			}
			catch (IOException e) { throw new RuntimeException(e); }
			catch (LineUnavailableException e) { throw new RuntimeException(e); }
		}

		new Thread(this).start();
		new Thread(this::processAudioQueue).start();
	}
	@Override
	//Process message queue
	public void run() {
		while (activeInstances() > 0) {
			if (messageQueue.isEmpty()) {
				continue;
			}

			TTSItem message;
			message = messageQueue.poll();

			messageSend:
			if(!instances.isEmpty()) {
				for (TTSEngine instance : instances) {
					if(!instance.getTtsLocked().get()) {
						message.audioClip = instance.sendStreamTTSData(message);
						String key = (message.getType() == ChatMessageType.DIALOG) ? "&dialog" : message.getName();
						if (message.audioClip.length > 0) {
							audioQueues.computeIfAbsent(key, k -> new PlayerAudioQueue()).queue.add(message);
							break messageSend;
						}
					}
				}
			}
		}
	}
	public void processAudioQueue() {
		while (activeInstances() > 0) {
			audioQueues.forEach((key, audioQueue) -> {
				if (!audioQueue.queue.isEmpty() && !audioQueue.isPlaying().get()) {
					audioQueue.setPlaying(true);
					new Thread(() -> {
						try {
							TTSItem sentence;
							while ((sentence = audioQueue.queue.poll()) != null) {
								audio.playClip(sentence);
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
	public ConcurrentLinkedQueue<TTSItem> getAudioQueue(String key) {
		return audioQueues.computeIfAbsent(key, k -> new PlayerAudioQueue()).queue;
	}
	public synchronized void speak(TTSItem message) throws IOException {
		if (activeInstances() == 0) throw new IOException("No active TTS engine instances running");
		if (messageQueue.size() > 10) clearQueue();

		System.out.println("adding message to text queue");
		messageQueue.add(message);
	}
	public void clearQueue() {
		if (!messageQueue.isEmpty()) {
			messageQueue.clear();
		}
		audioQueues.values().forEach(audioQueue -> {
			if (!audioQueue.queue.isEmpty()) {
				audioQueue.queue.clear();
			}
		});
	}
	public int activeInstances() {
		int result = 0;
		if(!instances.isEmpty()) {
			for (TTSEngine instance : instances) {
				if(instance.isProcessing())result++;
			}
		}
		return result;
	}
	public void shutDown() {
		audio.shutDown();
		if(!instances.isEmpty()) {
			for (TTSEngine instance : instances) {
				instance.shutDown();
			}
		}
	}
}
