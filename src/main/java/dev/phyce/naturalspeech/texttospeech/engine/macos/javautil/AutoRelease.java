package dev.phyce.naturalspeech.texttospeech.engine.macos.javautil;

import static com.google.common.base.Preconditions.checkState;
import com.sun.jna.Pointer;
import dev.phyce.naturalspeech.texttospeech.engine.macos.foundation.NSObject;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.ID;
import java.lang.ref.Cleaner;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is used to automatically release the ARC objects when the java objects are phantom reachable.<br>
 *
 * @see #register(ID)
 * @see #register(Object, ID)
 */
@Slf4j
public final class AutoRelease {
	public static final Cleaner CLEANER = Cleaner.create();

	private AutoRelease() {}

	/**
	 * Registers the native object to be released when the java object is phantom reachable.<br><br>
	 *
	 * When the java object is phantom reachable, the native object will be released.
	 * This metaphorically links the java object's life cycle to the native object's life cycle.<br>
	 *
	 * example:
	 * <pre>{@code
	 * ID ID = NSObject.alloc();
	 * // keep the reference to this java object, representing the native object ID
	 * this.javaObj = new Object();
	 * // the ID will be released when this.javaObj is phantom reachable
	 * AutoRelease.register(javaObj, ID);
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
	 * @param javaObj the object to be registered
	 * @param nativeID the objective-c nativeID of the object
	 * @return the original nativeID and the object as a pair, for convenience
	 */
	public static <T> Pair<T> register(@NonNull T javaObj, @NonNull ID nativeID) {
		checkState(!nativeID.isNil(), "Attempting to registering nil to be auto released.");

		// Important:
		// We do not want the Releaser to hold a reference to the ID object.
		// So we capture the internal long value instead.
		long pointer = Pointer.nativeValue(nativeID);
		CLEANER.register(javaObj, new Releaser(pointer));
		return new Pair<>(javaObj, nativeID);
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
	 * <b>You must keep the ID object referenced until you don't need the native object anymore.<br></b>
	 *
	 * @param nativeID This ID Object will release the native object when it is phantom reachable.
	 * @return The original ID object for, convenience.
	 */
	public static ID register(@NonNull ID nativeID) {
		register(nativeID, nativeID);
		return nativeID;
	}

	@Value
	@AllArgsConstructor
	public static class Pair<T> {
		T javaObj;
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
