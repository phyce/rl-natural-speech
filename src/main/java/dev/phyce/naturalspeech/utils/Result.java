package dev.phyce.naturalspeech.utils;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Mom: We have Rust at home<br>
 * Rust at home: {@link Result}
 */
public interface Result<@NonNull V, @NonNull E extends Throwable> {

	@NonNull
	static <V, E extends Throwable> Result<V, E> Ok(V value) {
		return new OkResult<>(value);
	}

	@NonNull
	static <E extends Throwable> Result<Void, E> Ok() {
		return new OkResult<>(null);
	}

	@NonNull
	static <V, E extends Throwable> Result<V, E> Error(@NonNull E exception) {
		return new ErrorResult<>(exception);
	}



	boolean isOk();

	boolean isError();

	void ifOk(@NonNull Consumer<? super V> then);

	void ifOk(@NonNull Runnable then);

	void ifError(@NonNull Consumer<? super E> then);

	<V2> Result<V2, E> map(@NonNull Function<? super V, V2> mapper);

	<E2 extends Throwable> Result<V, E2> mapError(@NonNull Function<? super E, E2> mapper);

	Optional<V> toOptional();

	@NonNull
	V unwrap() throws IllegalUnwrapException;

	@NonNull
	E unwrapError() throws IllegalUnwrapException;


	@NonNull
	static <V> Result<V, NoSuchElementException> fromNullable(@Nullable V value) {
		if (value == null) {
			return Error(new NoSuchElementException("Value is null"));
		} else {
			return Ok(value);
		}
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	@NonNull
	static <V> Result<V, NoSuchElementException> fromOptional(Optional<V> value) {
		if (value.isEmpty()) {
			return Error(new NoSuchElementException("Value is null"));
		} else {
			return Ok(value.get());
		}
	}

	@AllArgsConstructor(access=AccessLevel.PUBLIC)
	class OkResult<@NonNull V, E extends Throwable> implements Result<V, E> {
		private final V value;

		@Override
		public boolean isOk() {return true;}

		@Override
		public boolean isError() {return false;}

		@Override
		public void ifOk(@NonNull Consumer<? super V> then) {
			then.accept(value);
		}

		@Override
		public void ifOk(@NonNull Runnable then) {
			then.run();
		}

		@Override
		public void ifError(@NonNull Consumer<? super E> then) {}

		@Override
		public <V2> Result<V2, E> map(@NonNull Function<? super V, V2> mapper) {
			return new OkResult<>(mapper.apply(value));
		}

		@Override
		public <E2 extends Throwable> Result<V, E2> mapError(@NonNull Function<? super E, E2> mapper) {
			return new OkResult<>(value);
		}

		@Override
		public Optional<V> toOptional() {
			return Optional.of(value);
		}

		@Override
		public @NonNull V unwrap() {
			return value;
		}

		@Override
		public @NonNull E unwrapError() throws IllegalUnwrapException {
			throw new IllegalUnwrapException("Cannot unwrap error from Ok result");
		}

	}

	@Slf4j
	@AllArgsConstructor(access=AccessLevel.PRIVATE)
	class ErrorResult<V, @NonNull E extends Throwable> implements Result<V, E> {
		private final E exception;

		@Override
		public boolean isOk() {return false;}

		@Override
		public boolean isError() {return true;}

		@Override
		public void ifOk(@NonNull Consumer<? super V> then) {}

		@Override
		public void ifOk(@NonNull Runnable then) {}

		@Override
		public void ifError(@NonNull Consumer<? super E> then) {
			then.accept(exception);
		}

		@Override
		public <V2> Result<V2, E> map(@NonNull Function<? super V, V2> mapper) {
			return new ErrorResult<>(exception);
		}

		@Override
		public <E2 extends Throwable> Result<V, E2> mapError(@NonNull Function<? super E, E2> mapper) {
			return new ErrorResult<>(mapper.apply(exception));
		}

		@Override
		public Optional<V> toOptional() {
			return Optional.empty();
		}

		@Override
		public @NonNull V unwrap() throws IllegalStateException {
			throw new IllegalUnwrapException("Cannot unwrap value from Error result");
		}

		@Override
		public @NonNull E unwrapError() {
			return exception;
		}
	}

	class IllegalUnwrapException extends IllegalStateException {
		public IllegalUnwrapException(String message) {
			super(message);
		}
	}

	class ResultFutures {
		@NonNull
		public static <V,E extends Throwable> ListenableFuture<Result<V,E>> immediateError(@NonNull E exception) {
			return Futures.immediateFuture(Error(exception));
		}

		@NonNull
		public static <V,E extends Throwable> ListenableFuture<Result<V,E>> immediateOk(@NonNull V value) {
			return Futures.immediateFuture(Ok(value));
		}

		@NonNull
		public static <E extends Throwable> ListenableFuture<Result<Void,E>> immediateOk() {
			return Futures.immediateFuture(Ok());
		}

	}
}
