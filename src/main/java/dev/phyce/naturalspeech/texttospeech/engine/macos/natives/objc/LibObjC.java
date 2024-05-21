package dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc;

import static com.google.common.base.Preconditions.checkState;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.foundation.NSString;

/**
 * <h1>Objective-C Runtime Library</h1>
 * <p>
 * <br>
 * Make sure to check {@link #INSTANCE} null-ness before using; null means the platform does not have libobjc.dylib available.
 * <br><br>
 * The only operating systems with libobjc.dylib bundled are Apple OS (macOS, iOS, etc.)
 * </p>
 * <h2>Naming Conventions</h2>
 * All functions in the Objective-C runtime library follow the naming conventions:
 * <ul>
 *     <li>Allocating functions must be prefixed with {@code alloc}; reminding user to dealloc or AutoRelease.<br>
 *     For example, {@link NSString#alloc(String)}.
 *     </li>
 *     <li>Field getters that does not require memory management must be prefixed with {@code get}.<br>
 *     For example, {@link NSString#getJavaString(ID)}.
 *     </li>
 * </ul>
 *
 * @see <a href="https://developer.apple.com/documentation/objectivec/objective-c_runtime?language=objc">Apple Documentation: Objective-C Runtime</a>
 */
public final class LibObjC {

	private LibObjC() {}

	public static final LibObjC INSTANCE;

	static {
		LibObjC inst;
		try {
			inst = new LibObjC();
		} catch (UnsatisfiedLinkError linkError) {
			// The Platform does not have libobjc available (e.g., Windows)
			inst = null;
		}

		INSTANCE = inst;
	}

	private final NativeLibrary dylib = NativeLibrary.getInstance("objc");

	private final Function objc_msgSend = dylib.getFunction("objc_msgSend");
	private final Function objc_getClass = dylib.getFunction("objc_getClass");
	private final Function sel_registerName = dylib.getFunction("sel_registerName");
	private final Function object_getClass = dylib.getFunction("object_getClass");

	private final Function _Block_release = dylib.getFunction("_Block_release");
	private final Function _Block_copy = dylib.getFunction("_Block_copy");

	// Foundation is closed source, the actual block runtime implementation is hidden and platform-specific
	// these are global memory addresses that will be updated with the actual addresses at runtime
	// https://github.com/llvm/llvm-project/blob/main/compiler-rt/lib/BlocksRuntime/Block_private.h#L127C1-L134C54
	public final Pointer _NSConcreteGlobalBlock = dylib.getGlobalVariableAddress("_NSConcreteGlobalBlock");
	public final Pointer _NSConcreteMallocBlock = dylib.getGlobalVariableAddress("_NSConcreteMallocBlock");

	/**
	 * Sends a message with a simple return value to an instance of a class.
	 *
	 * @param self A pointer to the instance of the class that is to receive the message.
	 * @param op   The op of the method that handles the message.
	 * @param args A variable argument list containing the arguments to the method.
	 *
	 * @return The return value of the method.
	 *
	 * @see <a href="https://github.com/apple-oss-distributions/objc4/blob/01edf1705fbc3ff78a423cd21e03dfc21eb4d780/runtime/objc.h#L155">Apple Documentation</a>
	 */
	public static ID objc_msgSend(ID self, SEL op, Object... args) {
		checkState(INSTANCE != null, "LibObjC is not available");
		checkState(INSTANCE.objc_msgSend != null, "objc_msgSend is not available");

		Object[] invokeArgs = new Object[args.length + 2];
		invokeArgs[0] = self;
		invokeArgs[1] = op;
		System.arraycopy(args, 0, invokeArgs, 2, args.length);

		return new ID(INSTANCE.objc_msgSend.invokePointer(invokeArgs));
	}

	public static Pointer objc_msgSend_pointer(ID self, SEL op, Object... args) {
		checkState(INSTANCE != null, "LibObjC is not available");
		checkState(INSTANCE.objc_msgSend != null, "objc_msgSend is not available");

		Object[] invokeArgs = new Object[args.length + 2];
		invokeArgs[0] = self;
		invokeArgs[1] = op;
		System.arraycopy(args, 0, invokeArgs, 2, args.length);

		return INSTANCE.objc_msgSend.invokePointer(invokeArgs);
	}

	public static long objc_msgSend_long(ID self, SEL op, Object... args) {
		checkState(INSTANCE != null, "LibObjC is not available");
		checkState(INSTANCE.objc_msgSend != null, "objc_msgSend is not available");

		Object[] invokeArgs = new Object[args.length + 2];
		invokeArgs[0] = self;
		invokeArgs[1] = op;
		System.arraycopy(args, 0, invokeArgs, 2, args.length);

		return INSTANCE.objc_msgSend.invokeLong(invokeArgs);
	}

	public static boolean objc_msgSend_bool(ID self, SEL op, Object... args) {
		checkState(INSTANCE != null, "LibObjC is not available");
		checkState(INSTANCE.objc_msgSend != null, "objc_msgSend is not available");

		Object[] invokeArgs = new Object[args.length + 2];
		invokeArgs[0] = self;
		invokeArgs[1] = op;
		System.arraycopy(args, 0, invokeArgs, 2, args.length);

		return (boolean) INSTANCE.objc_msgSend.invoke(boolean.class, invokeArgs);
	}

	public static int objc_msgSend_int(ID self, SEL op, Object... args) {
		checkState(INSTANCE != null, "LibObjC is not available");
		checkState(INSTANCE.objc_msgSend != null, "objc_msgSend is not available");

		Object[] invokeArgs = new Object[args.length + 2];
		invokeArgs[0] = self;
		invokeArgs[1] = op;
		System.arraycopy(args, 0, invokeArgs, 2, args.length);

		return INSTANCE.objc_msgSend.invokeInt(invokeArgs);
	}

	public static double objc_msgSend_double(ID self, SEL op, Object... args) {
		checkState(INSTANCE != null, "LibObjC is not available");
		checkState(INSTANCE.objc_msgSend != null, "objc_msgSend is not available");

		Object[] invokeArgs = new Object[args.length + 2];
		invokeArgs[0] = self;
		invokeArgs[1] = op;
		System.arraycopy(args, 0, invokeArgs, 2, args.length);

		return INSTANCE.objc_msgSend.invokeDouble(invokeArgs);
	}

	/**
	 * Returns the class definition of a specified class.
	 *
	 * @param name The name of the class to look up.
	 *
	 * @return The Class object for the named class, or {@code nil}
	 * if the class is not registered with the Objective-C runtime.
	 *
	 * @see <a href="https://github.com/apple-oss-distributions/objc4/blob/01edf1705fbc3ff78a423cd21e03dfc21eb4d780/runtime/runtime.h#L268">Apple Documentation</a>
	 */
	public static ID objc_getClass(String name) {
		checkState(INSTANCE != null, "LibObjC is not available");
		checkState(INSTANCE.objc_getClass != null, "objc_getClass is not available");

		return new ID(INSTANCE.objc_getClass.invokeLong(new Object[] {name}));
	}

	/**
	 * Registers a method with the Objective-C runtime system, maps the method
	 * name to a selector, and returns the selector value.
	 * <br>
	 *
	 * @param selectorName A pointer to a C string. Pass the name of the method you wish to register.
	 *
	 * @return A pointer of type SEL specifying the selector for the named method.
	 *
	 * @see <a href="https://github.com/apple-oss-distributions/objc4/blob/01edf1705fbc3ff78a423cd21e03dfc21eb4d780/runtime/objc.h#L155">Apple Documentation</a>
	 */
	public static SEL sel_registerName(String selectorName) {
		checkState(INSTANCE != null, "LibObjC is not available");
		checkState(INSTANCE.sel_registerName != null, "sel_registerName is not available");

		return new SEL(INSTANCE.sel_registerName.invokeLong(new Object[] {selectorName}));
	}


	public static ID object_getClass(ID object) {
		checkState(INSTANCE != null, "LibObjC is not available");
		checkState(INSTANCE.object_getClass != null, "object_getClass is not available");

		return new ID(INSTANCE.object_getClass.invokePointer(new Object[] {object}));
	}

	public static void Block_release(Block block) {
		checkState(INSTANCE != null, "LibObjC is not available");
		checkState(INSTANCE._Block_release != null, "_Block_release is not available");

		INSTANCE._Block_release.invoke(new Object[] {block});
	}

	public static Block Block_copy(Block block) {
		checkState(INSTANCE != null, "LibObjC is not available");
		checkState(INSTANCE._Block_copy != null, "_Block_copy is not available");

		return Block.cast(INSTANCE._Block_copy.invokePointer(new Object[] {block}));
	}

}
