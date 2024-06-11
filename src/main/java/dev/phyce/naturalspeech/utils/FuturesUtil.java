package dev.phyce.naturalspeech.utils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import java.util.concurrent.Executor;
import lombok.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FuturesUtil {

	public static class DirectFutures {
		public static <V extends @Nullable Object> void addSuccess(
			ListenableFuture<V> future,
			SuccessCallback<? super V> onSuccess
		) {
			Futures.addCallback(future, onSuccess, directExecutor());
		}

		public static <V extends @Nullable Object> void addFailure(
			ListenableFuture<V> future,
			FailureCallback<? super V> onFailure
		) {
			Futures.addCallback(future, onFailure, directExecutor());
		}
	}

	public static class ParallelFutures {
		public static <V extends @Nullable Object> void addSuccess(
			final ListenableFuture<V> future,
			final SuccessCallback<? super V> onSuccess,
			final Executor executor
		) {
			Futures.addCallback(future, onSuccess, executor);
		}

		public static <V extends @Nullable Object> void addFailure(
			final ListenableFuture<V> future,
			final FailureCallback<? super V> onFailure,
			final Executor executor
		) {
			Futures.addCallback(future, onFailure, executor);
		}

	}

	public interface SuccessCallback<T> extends FutureCallback<T> {
		default void onFailure(@NonNull Throwable t) { /* silent ignore */ }
	}

	public interface FailureCallback<T> extends FutureCallback<T> {
		default void onSuccess(@NonNull T t) { /* silent ignore */ }
	}
}
