package dev.phyce.naturalspeech.jna.macos.objc;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;

/**
 * libobjc.dylib
 * @see <a href=https://developer.apple.com/documentation/objectivec/objective-c_runtime?language=objc>Apple Documentation: Objective-C Runtime</a>
 */
public final class ObjC {

	private ObjC() {}

	private static final NativeLibrary dylib = NativeLibrary.getInstance("objc");
	private static final Function objc_msgSend = dylib.getFunction("objc_msgSend");
	private static final Function objc_getClass = dylib.getFunction("objc_getClass");
	private static final Function sel_registerName = dylib.getFunction("sel_registerName");

	/**
	 * Variadic obj_msgSend
	 * @param receiver self
	 * @param selector selector
	 * @param args arg obj array
	 * @return result
	 */
	public static ID objc_msgSend(ID receiver, SEL selector, Object... args) {
		Object[] invokeArgs = new Object[args.length + 2];
		invokeArgs[0] = receiver;
		invokeArgs[1] = selector;
		System.arraycopy(args, 0, invokeArgs, 2, args.length);

		return new ID(objc_msgSend.invokeLong(invokeArgs));
	}

	public static ID objc_getClass(String className) {
		return new ID(objc_getClass.invokeLong(new Object[] {className}));
	}

	public static SEL sel_registerName(String selectorName) {
		return new SEL(sel_registerName.invokeLong(new Object[] {selectorName}));
	}

}
