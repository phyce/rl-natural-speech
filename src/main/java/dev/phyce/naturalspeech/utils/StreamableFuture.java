package dev.phyce.naturalspeech.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import lombok.NonNull;

public class StreamableFuture<T> extends AbstractFuture<T> {

	private final ImmutableList<ListenableFuture<T>> segments;
	private final Function<ImmutableList<T>, T> joiner;

	public void addStreamListener(@NonNull FuncFutures.SuccessCallback<T> onSuccess, @NonNull Executor executor) {
		segments.forEach((segment) -> FuncFutures.onSuccess(segment, onSuccess, executor));
	}

	public StreamableFuture(
		@NonNull List<ListenableFuture<T>> segments,
		@NonNull Function<ImmutableList<T>, T> joiner
	) {
		this.segments = ImmutableList.copyOf(segments);
		this.joiner = joiner;

		ListenableFuture<List<T>> list = Futures.allAsList(segments);
		ListenableFuture<T> future = Futures.transform(list,
			(results) -> joiner.apply(ImmutableList.copyOf(results)),
			directExecutor());
		setFuture(future);
	}

	public static <T> @NonNull StreamableFuture<T> singular(@NonNull ListenableFuture<T> future) {
		return new StreamableFuture<>(ImmutableList.of(future), (segments) -> segments.get(0));
	}


}
