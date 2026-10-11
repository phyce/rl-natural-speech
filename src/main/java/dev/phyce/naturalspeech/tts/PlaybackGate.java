package dev.phyce.naturalspeech.tts;

import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public class PlaybackGate {

	private static final Hold PASSTHROUGH = () -> {};
	private static final Set<String> UNQUEUED = Set.of(MagicUsernames.DIALOG, MagicUsernames.VOICE_EXPLORER);

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

	public boolean isQueued(String queueName) {
		return enabled.getAsBoolean() && !UNQUEUED.contains(queueName);
	}

	public boolean isBacklogged(int pending) {
		return enabled.getAsBoolean() && pending >= maxQueueSize.getAsInt();
	}

	public Hold acquire(String queueName) {
		if (!isQueued(queueName)) return PASSTHROUGH;

		lock.lock();
		return lock::unlock;
	}

	public interface Hold extends AutoCloseable {
		@Override
		void close();
	}
}
