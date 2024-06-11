package dev.phyce.naturalspeech.executor;

import dev.phyce.naturalspeech.singleton.PluginSingleton;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import net.runelite.client.util.ExecutorServiceExceptionLogger;

@PluginSingleton
public class PluginExecutorService extends ExecutorServiceExceptionLogger {

	public PluginExecutorService() {
		super(new ScheduledThreadPoolExecutor(16));
	}

}
