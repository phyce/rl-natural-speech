package dev.phyce.naturalspeech.tts;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public class PlaybackGate {

	private static final Hold PASSTHROUGH = () -> {};

	private final ReentrantLock lock = new ReentrantLock(true);
	private final BooleanSupplier enabled;
	private final IntSupplier maxQueueSize;

	public PlaybackGate(BooleanSupplier enabled, IntSupplier maxQueueSize) {
		this.enabled = enabled;
		this.maxQueueSize = maxQueueSize;
	}

	public static PlaybackGate disabled() {
		return new PlaybackGate(() -> false, () -> Integer.MAX_VALUE);
	}

	public boolean isBacklogged(int pending) {
		return enabled.getAsBoolean() && pending >= maxQueueSize.getAsInt();
	}

	public Hold acquire() {
		if (!enabled.getAsBoolean()) return PASSTHROUGH;

		lock.lock();
		return lock::unlock;
	}

	public interface Hold extends AutoCloseable {
		@Override
		void close();
	}
}