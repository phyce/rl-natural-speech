package dev.phyce.naturalspeech.jna.macos.objc;

import static com.google.common.base.Preconditions.checkState;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/**
 * libobjc.dylib
 * @see <a href="https://developer.apple.com/documentation/objectivec/objective-c_runtime?language=objc">Apple Documentation: Objective-C Runtime</a>
 */
public final class LibObjC {

	private LibObjC() {}

	public static final LibObjC INSTANCE;

	static  {
		LibObjC inst;
		try {
			inst = new LibObjC();
		} catch (UnsatisfiedLinkError linkError) {
			inst = null;
		}

		INSTANCE = inst;
	}

	private final NativeLibrary dylib = NativeLibrary.getInstance("objc");

	private final Function objc_msgSend = dylib.getFunction("objc_msgSend");
	private final Function objc_getClass = dylib.getFunction("objc_getClass");
	private final Function sel_registerName = dylib.getFunction("sel_registerName");
	private final Function object_getClass = dylib.getFunction("object_getClass");

	// Runtime block copy/destroy helper functions (from Block_private.h in LLVM)
	// https://github.com/llvm/llvm-project/blob/main/compiler-rt/lib/BlocksRuntime/Block_private.h#L127C1-L134C54
	public final Pointer _NSConcreteGlobalBlock = dylib.getGlobalVariableAddress("_NSConcreteGlobalBlock");
	public final Pointer _NSConcreteStackBlock = dylib.getGlobalVariableAddress("_NSConcreteStackBlock");

	/**
	 * Sends a message with a simple return value to an instance of a class.
	 *
	 * @param self A pointer to the instance of the class that is to receive the message.
	 * @param op The op of the method that handles the message.
	 * @param args
	 *   A variable argument list containing the arguments to the method.
	 *
	 * @return The return value of the method.
	 *
	 * @note When it encounters a method call, the compiler generates a call to one of the
	 *  functions {@code objc_msgSend}, {@code objc_msgSend_stret}, {@code objc_msgSendSuper}, or {@code objc_msgSendSuper_stret}.
	 *  Messages sent to an object’s superclass (using the super keyword) are sent using {@code objc_msgSendSuper};
	 *  other messages are sent using objc_msgSend. Methods that have data structures as return values
	 *  are sent using {@code objc_msgSendSuper_stret} and {@code objc_msgSend_stret}.
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
	 *  if the class is not registered with the Objective-C runtime.
	 *
	 * @note The implementation of {@code objc_getClass} is identical to the implementation
	 *  of {@code objc_lookUpClass}.
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
	 * @return A pointer of type SEL specifying the selector for the named method.
	 *
	 * @note You must register a method name with the Objective-C runtime system to obtain the
	 * method’s selector before you can add the method to a class definition. If the method name
	 * has already been registered, this function simply returns the selector.
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

}
