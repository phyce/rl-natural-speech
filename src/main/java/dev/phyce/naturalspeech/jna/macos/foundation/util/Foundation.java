package dev.phyce.naturalspeech.jna.macos.foundation.util;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.ID;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.SEL;

public final class Foundation {

	private Foundation() {}

	private static final NativeLibrary lib = NativeLibrary.getInstance("Foundation");
	private static final Function objc_msgSend = lib.getFunction("objc_msgSend");
	private static final Function objc_getClass = lib.getFunction("objc_getClass");
	private static final Function sel_registerName = lib.getFunction("sel_registerName");

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
