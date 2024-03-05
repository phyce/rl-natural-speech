package dev.phyce.naturalspeech.tts;

import lombok.Value;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioQueue {
	private final AtomicBoolean playing = new AtomicBoolean(false);
	public ConcurrentLinkedQueue<AudioTask> queue = new ConcurrentLinkedQueue<>();

	public synchronized AtomicBoolean isPlaying() {
		return playing;
	}

	public synchronized void setPlaying(boolean playing) {
		this.playing.set(playing);
	}

	// decoupled audio queue from plugin logic
	@Value
	public static class AudioTask {
		byte[] audioClip;
		float volume;
	}
}
