package dev.phyce.naturalspeech.playground;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class TaskChainTest {

	@Test
	public void testChain() throws InterruptedException {
		List<SettableFuture<Integer>> orderedAsyncTask = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			orderedAsyncTask.add(SettableFuture.create());
		}

		for (SettableFuture<Integer> task : orderedAsyncTask) {
			Futures.addCallback(task, new FutureCallback<Integer>() {
				@Override
				public void onSuccess(Integer result) {
					log.info("task {} completed", result);
				}

				@Override
				public void onFailure(@NonNull Throwable t) {
					log.error("task skipped");
				}
			}, Runnable::run);
		}



		for (int i = orderedAsyncTask.size() - 1; i >= 0; i--) {
			log.warn("!!! canceling {}", i);
			orderedAsyncTask.get(i).cancel(false);
		}
		for (int i = orderedAsyncTask.size() - 1; i >= 0; i--) {
			log.info("--> sending {}", i);
			orderedAsyncTask.get(i).set(i);
		}

	}

}
