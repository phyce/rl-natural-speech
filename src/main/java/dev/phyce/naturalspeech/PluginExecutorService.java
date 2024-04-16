package dev.phyce.naturalspeech;

import dev.phyce.naturalspeech.guice.PluginSingleton;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import net.runelite.client.util.ExecutorServiceExceptionLogger;

@PluginSingleton
public class PluginExecutorService extends ExecutorServiceExceptionLogger {

	public PluginExecutorService() {
		super(new ScheduledThreadPoolExecutor(8));
	}
}
