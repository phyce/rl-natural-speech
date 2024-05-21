package dev.phyce.naturalspeech.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Threads {
	public static final Thread.UncaughtExceptionHandler silentInterruptHandler = (t, e) -> {
		if (!(e instanceof InterruptedException)) {
			log.error("Uncaught exception in thread {}", t, e);
		}
		else {
			log.trace("Interrupted {} ", t);
		}
	};
}
