package dev.phyce.naturalspeech.tts;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Value;

public class AudioQueue {
	private final AtomicBoolean playing = new AtomicBoolean(false);
	public ConcurrentLinkedQueue<AudioTask> queue = new ConcurrentLinkedQueue<>();

	public boolean isPlaying() {
		return playing.get();
	}

	public void setPlaying(boolean playing) {
		this.playing.set(playing);
	}

	// decoupled audio queue from plugin logic
	@Value
	public static class AudioTask {
		byte[] audioClip;
		float volume;
	}
}
