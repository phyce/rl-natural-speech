package dev.phyce.naturalspeech.utils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import java.util.concurrent.Executor;
import lombok.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FuncFutures {

	private static final Logger log = LoggerFactory.getLogger(FuncFutures.class);

	// region run callback on "nearest" thread (read directExecutor)
	//////////////////////////////////////////////////////////////////////
	public static void onComplete(ListenableFuture<?> future, Runnable then) {
		future.addListener(then, directExecutor());
	}

	public static <V extends @Nullable Object> void onSuccess(
		ListenableFuture<V> future,
		SuccessCallback<? super V> then
	) {
		Futures.addCallback(future, then, directExecutor());
	}

	public static <V extends @Nullable Object> void onException(
		ListenableFuture<V> future,
		ExceptionCallback<? super V> then
	) {
		Futures.addCallback(future, then, directExecutor());
	}

	public static <R extends Result<?, ?>> void onResult(
		ListenableFuture<R> future,
		ResultCallback<R> onResult
	) {
		Futures.addCallback(future, onResult, directExecutor());
	}
	// endregion

	// region run callback on executor
	//////////////////////////////////////////////////////////////////////
	public static void onComplete(ListenableFuture<?> future, Runnable then, Executor executor) {
		future.addListener(then, executor);
	}

	public static <V extends @Nullable Object> void onSuccess(
		final ListenableFuture<V> future,
		final SuccessCallback<? super V> then,
		final Executor executor
	) {
		Futures.addCallback(future, then, executor);
	}

	public static <V extends @Nullable Object> void onException(
		final ListenableFuture<V> future,
		final ExceptionCallback<? super V> then,
		final Executor executor
	) {
		Futures.addCallback(future, then, executor);
	}

	public static <R extends @NonNull Result<?, ?>> void onResult(
		ListenableFuture<R> future,
		ResultCallback<R> then,
		final Executor executor
	) {
		Futures.addCallback(future, then, executor);
	}
	// endregion

	public interface SuccessCallback<T> extends FutureCallback<T> {
		// Interface Override
		default void onFailure(@NonNull Throwable t) { /* silent ignore */ }
	}

	public interface ExceptionCallback<T> extends FutureCallback<T> {
		// Interface Override
		default void onSuccess(@NonNull T t) { /* silent ignore */ }
	}


	public interface ResultCallback<V extends Result<?, ?>> extends FutureCallback<V> {
		void onSuccess(@NonNull V value);

		default void onFailure(@NonNull Throwable t) {
			log.error(
				"Result Future has uncaught exception. (Result Future should never throw, return Error(throwable) instead).",
				t);
		}

	}
}
