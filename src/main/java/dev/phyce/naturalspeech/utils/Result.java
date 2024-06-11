package dev.phyce.naturalspeech.utils;

import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Value;

/**
 * Mom: We have Rust at home<br>
 * Rust at home: {@link Result}
 */
public interface Result<V, E extends Throwable> {

	boolean isOk();

	boolean isError();

	@NonNull
	Result<V, E> ifOk(@NonNull Consumer<V> then);

	@NonNull
	Result<V, E> ifError(@NonNull Consumer<E> then);

	@NonNull
	V unwrap() throws E;

	@NonNull
	E unwrapError() throws IllegalStateException;

	@Value
	@AllArgsConstructor(access=AccessLevel.PRIVATE)
	class ErrorResult<V, E extends Throwable> implements Result<V, E> {
		@NonNull
		E exception;

		public static <T, E extends Throwable> ErrorResult<T, E> Error(@NonNull E exception) {
			return new ErrorResult<>(exception);
		}

		@Override
		public boolean isOk() {return false;}

		@Override
		public boolean isError() {return true;}

		@Override
		public @NonNull Result<V, E> ifOk(@NonNull Consumer<V> then) {
			return this;
		}

		@Override
		public @NonNull Result<V, E> ifError(@NonNull Consumer<E> then) {
			then.accept(exception);
			return this;
		}

		@Override
		public @NonNull V unwrap() throws E {
			throw exception;
		}

		@Override
		public @NonNull E unwrapError() throws IllegalStateException {
			return exception;
		}
	}

	@Value
	@AllArgsConstructor(access=AccessLevel.PRIVATE)
	class OkResult<V, E extends Throwable> implements Result<V, E> {
		V value;

		public static <T, E extends Throwable> @NonNull OkResult<T, E> Ok(@NonNull T value) {
			return new OkResult<>(value);
		}

		public static <Void, E extends Throwable> @NonNull OkResult<Void, E> Ok() {
			return new OkResult<>(null);
		}

		@Override
		public boolean isOk() {return true;}

		@Override
		public boolean isError() {return false;}

		@Override
		public @NonNull Result<V, E> ifOk(@NonNull Consumer<V> then) {
			then.accept(value);
			return this;
		}

		@Override
		public @NonNull Result<V, E> ifError(@NonNull Consumer<E> then) {
			return this;
		}

		@Override
		public @NonNull V unwrap() {
			return value;
		}

		@Override
		public @NonNull E unwrapError() throws IllegalStateException {
			throw new IllegalStateException("Cannot unwrap error from Ok result");
		}

	}

	@NoArgsConstructor(access=AccessLevel.PRIVATE)
	class Results {
		public static <T, E extends Throwable> Result<T, E> Ok(T value) {
			return OkResult.Ok(value);
		}

		public static <Void, E extends Throwable> Result<Void, E> Ok() {
			return OkResult.Ok();
		}

		public static <T, E extends Throwable> Result<T, E> Error(@NonNull E exception) {
			return ErrorResult.Error(exception);
		}
	}
}
