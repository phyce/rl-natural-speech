package dev.phyce.naturalspeech.tts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.sound.sampled.LineUnavailableException;
import lombok.Getter;

public class TTSModel implements Runnable
{
	@Getter private String name;
	@Getter private Path path;
	@Getter private Path enginePath;
	@Getter final private List<TTSEngine> instances = new ArrayList<>();

	private final ConcurrentLinkedQueue<TTSItem> messageQueue = new ConcurrentLinkedQueue<>();


	public TTSModel(String name, Path path, Path enginePath) {
		this(name, path, enginePath, 2);

	}

	public TTSModel(String name, Path path, Path enginePath, int instanceCount) {
		this.name = name;
		this.path = path;
		this.enginePath = enginePath;

		//Instance count should not be more than 2
		for(int index = 1; index <= instanceCount; index++ ) {
			try {
				TTSEngine instance = new TTSEngine(enginePath, path);
				instances.add(instance);
			}
			catch (IOException e) { throw new RuntimeException(e); }
			catch (LineUnavailableException e) { throw new RuntimeException(e); }
		}

		new Thread(this).start();
	}
	@Override
	public void run() {
		while (activeInstances() > 0) {
			if (messageQueue.isEmpty()) continue;

			TTSItem message;
			message = messageQueue.poll();

			messageSend:
			while(true) {
				if(!instances.isEmpty()) {
					for (TTSEngine instance : instances) {
						if(!instance.getTtsLocked().get()) {
							new Thread(() -> {instance.sendStreamTTSData(message);}).start();
							break messageSend;
						}
					}
				}
			}
		}
	}
	public synchronized void speak(TTSItem message) throws IOException {
		if (activeInstances() == 0) throw new IOException("No active TTS engine instances running");
		if (messageQueue.size() > 10) clearQueue();

		messageQueue.add(message);
	}
	public void clearQueue() {
		if (!messageQueue.isEmpty()) {
			messageQueue.clear();
		}
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
		if(!instances.isEmpty()) {
			for (TTSEngine instance : instances) {
				instance.shutDown();
			}
		}
	}
}
