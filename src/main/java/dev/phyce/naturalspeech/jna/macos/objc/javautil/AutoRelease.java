package dev.phyce.naturalspeech.jna.macos.objc.javautil;

import static com.google.common.base.Preconditions.checkState;
import dev.phyce.naturalspeech.jna.macos.objc.ID;
import dev.phyce.naturalspeech.jna.macos.objc.foundation.NSObject;
import java.lang.ref.Cleaner;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is used to automatically release the native objects when the java objects are phantom reachable.<br>
 *
 * @see #register(ID)
 * @see #register(Object, ID)
 */
@Slf4j
public final class AutoRelease {
	public static final Cleaner CLEANER = Cleaner.create();

	private AutoRelease() {}

	/**
	 * Registers the object to be released when the object is phantom reachable.<br>
	 *
	 * <b>This metaphorically links the java object's life cycle to the native object's life cycle.</b><br>
	 *
	 * When the java object is phantom reachable, the native object will be released.</b><br>
	 *
	 * example:
	 * <pre>{@code
	 * ID ID = NSObject.alloc();
	 * // keep the reference to this java object, representing the native object ID
	 * this.obj = new Object();
	 * // the ID will be released when this.obj is phantom reachable
	 * AutoRelease.register(obj, ID);
	 * }</pre>
	 *
	 * Special note: <br>
	 * Avoid using String as the object to be registered,
	 * because String may be interned and will never be phantom reachable.
	 * If you must, use new String("..."), never "..."<br>
	 * see {@link String#intern()} for more information<br><br>
	 *
	 * Make sure you understand the life cycle of the object you are registering.<br>
	 *
	 * @param obj the object to be registered
	 * @param id the objective-c id of the object
	 * @return the original id and the object as a pair, for convenience
	 */
	public static <T> Pair<T> register(@NonNull T obj, @NonNull ID id) {
		checkState(!id.isNil(), "Attempting to registering nil to be auto released.");

		// Important:
		// We do not want the Releaser to hold a reference to the ID object.
		// So we capture the internal long value instead.
		long idValue = id.longValue();

		CLEANER.register(obj, new Releaser(idValue));
		return new Pair<>(obj, id);
	}

	/**
	 * Registers the ID Java object itself to release the native object it represents when itself is phantom reachable.<br><br>
	 *
	 * example:
	 * <pre>{@code
	 * // registers the ID object itself
	 * ID obj = AutoRelease.register(NSObject.alloc());
	 * // keep the reference to the ID object
	 * this.obj = obj;
	 * }</pre>
	 *
	 * <b>You must keep the ID object referenced until you don't need the native object anymore.<br><b>
	 *
	 * @param id This ID Object will release the native object when it is phantom reachable.
	 * @return The original ID object for, convenience.
	 */
	public static ID register(@NonNull ID id) {
		register(id, id);
		return id;
	}

	@Value
	@AllArgsConstructor
	public static class Pair<T> {
		T obj;
		ID id;
	}

	private static class Releaser implements Runnable {
		private final long id;

		private Releaser(long id) {
			// we capture a long instead of the original ID
			// to avoid the reference to the ID object
			this.id = id;
		}

		@Override
		public void run() {
			// The cleaner thread is not synchronized with the main thread or the GC thread.

			// Therefore, for a brief moment on the main thread,
			// the java object will have been GC-ed, but the native object will still alive,
			// until the cleaner thread runs this code.

			// This may cause confusion if you are not aware of this behavior.
			// As what should be a segfault, may not be a segfault, yet.

			NSObject.release(new ID(id));
		}
	}
}
