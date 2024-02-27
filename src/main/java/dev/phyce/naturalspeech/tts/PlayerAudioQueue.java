package dev.phyce.naturalspeech.tts;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerAudioQueue {
	public ConcurrentLinkedQueue<TTSItem> queue = new ConcurrentLinkedQueue<>();
	private AtomicBoolean playing = new AtomicBoolean(false);


	public synchronized AtomicBoolean isPlaying() {
		return playing;
	}

	public synchronized void setPlaying(boolean playing) {
		this.playing.set(playing);
	}
}
