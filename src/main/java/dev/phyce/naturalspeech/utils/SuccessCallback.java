package dev.phyce.naturalspeech.utils;

import com.google.common.util.concurrent.FutureCallback;

public interface SuccessCallback<T> extends FutureCallback<T> {

	default void onFailure(Throwable t) {
		// silent ignore
	}
}
