package dev.phyce.naturalspeech.tts;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PlaybackGateTest {

	private static final int TIMEOUT_SECONDS = 5;

	@Test
	public void enabledGateLetsOnlyOneThroughAtATime() throws InterruptedException {
		PlaybackGate gate = new PlaybackGate(() -> true, () -> Integer.MAX_VALUE);

		final int threads = 8;
		AtomicInteger concurrent = new AtomicInteger();
		AtomicInteger peakConcurrent = new AtomicInteger();
		CountDownLatch startTogether = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(threads);

		for (int i = 0; i < threads; i++) {
			new Thread(() -> {
				try {
					startTogether.await();
					try (PlaybackGate.Hold ignored = gate.acquire()) {
						peakConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
						// hold long enough that an ungated run would overlap
						Thread.sleep(20);
						concurrent.decrementAndGet();
					}
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				finally {
					finished.countDown();
				}
			}).start();
		}

		startTogether.countDown();
		assertTrue("threads did not finish", finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertEquals("clips played over each other", 1, peakConcurrent.get());
	}

	@Test
	public void disabledGateDoesNotBlock() throws InterruptedException {
		PlaybackGate gate = new PlaybackGate(() -> false, () -> Integer.MAX_VALUE);

		CountDownLatch firstAcquired = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondAcquired = new CountDownLatch(1);

		Thread holder = new Thread(() -> {
			try (PlaybackGate.Hold ignored = gate.acquire()) {
				firstAcquired.countDown();
				releaseFirst.await();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		holder.start();

		assertTrue(firstAcquired.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

		// second acquire must go straight through while the first is still held
		new Thread(() -> {
			try (PlaybackGate.Hold ignored = gate.acquire()) {
				secondAcquired.countDown();
			}
		}).start();

		assertTrue("disabled gate blocked a second caller",
			secondAcquired.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

		releaseFirst.countDown();
		holder.join();
	}

	@Test
	public void enabledGateBlocksASecondCallerUntilTheFirstReleases() throws InterruptedException {
		PlaybackGate gate = new PlaybackGate(() -> true, () -> Integer.MAX_VALUE);

		CountDownLatch firstAcquired = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondAcquired = new CountDownLatch(1);

		Thread holder = new Thread(() -> {
			try (PlaybackGate.Hold ignored = gate.acquire()) {
				firstAcquired.countDown();
				releaseFirst.await();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		holder.start();

		assertTrue(firstAcquired.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

		new Thread(() -> {
			try (PlaybackGate.Hold ignored = gate.acquire()) {
				secondAcquired.countDown();
			}
		}).start();

		assertFalse("second caller should have been made to wait",
			secondAcquired.await(200, TimeUnit.MILLISECONDS));

		releaseFirst.countDown();
		assertTrue("second caller never got through after release",
			secondAcquired.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		holder.join();
	}

	@Test
	public void backlogIsCappedAtTheConfiguredSize() {
		PlaybackGate gate = new PlaybackGate(() -> true, () -> 30);

		assertFalse(gate.isBacklogged(0));
		assertFalse(gate.isBacklogged(29));
		assertTrue(gate.isBacklogged(30));
		assertTrue(gate.isBacklogged(500));
	}

	@Test
	public void backlogIsUnboundedWhenPlayingConcurrently() {
		// concurrent playback drains on its own, so nothing should ever be dropped
		PlaybackGate gate = new PlaybackGate(() -> false, () -> 30);

		assertFalse(gate.isBacklogged(0));
		assertFalse(gate.isBacklogged(10_000));
	}

	@Test
	public void backlogLimitOfOnePlaysOneAtATime() {
		PlaybackGate gate = new PlaybackGate(() -> true, () -> 1);

		assertFalse(gate.isBacklogged(0));
		assertTrue(gate.isBacklogged(1));
	}

	@Test
	public void togglingWhileHeldStillReleasesCleanly() throws InterruptedException {
		AtomicBoolean enabled = new AtomicBoolean(true);
		PlaybackGate gate = new PlaybackGate(enabled::get, () -> Integer.MAX_VALUE);

		try (PlaybackGate.Hold ignored = gate.acquire()) {
			enabled.set(false);
		}

		enabled.set(true);

		CountDownLatch acquired = new CountDownLatch(1);
		new Thread(() -> {
			try (PlaybackGate.Hold ignored = gate.acquire()) {
				acquired.countDown();
			}
		}).start();

		assertTrue("gate was left locked", acquired.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
	}
}
