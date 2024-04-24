package dev.phyce.naturalspeech.jna.macos.foundation.util;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.ID;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.SEL;

public final class Foundation {

	private Foundation() {}

	private static final NativeLibrary LIB = NativeLibrary.getInstance("Foundation");
	private static final Function OBJC_MSG_SEND = LIB.getFunction("objc_msgSend");
	private static final Function OBJC_GET_CLASS = LIB.getFunction("objc_getClass");
	private static final Function SEL_REGISTER_NAME = LIB.getFunction("sel_registerName");

	public static ID objc_msgSend(ID receiver, SEL selector, Object... args) {
		Object[] invokeArgs = new Object[args.length + 2];
		invokeArgs[0] = receiver;
		invokeArgs[1] = selector;
		System.arraycopy(args, 0, invokeArgs, 2, args.length);

		return new ID(OBJC_MSG_SEND.invokeLong(invokeArgs));
	}

	public static ID objc_getClass(String className) {
		return new ID(OBJC_GET_CLASS.invokeLong(new Object[] {className}));
	}

	public static SEL sel_registerName(String selectorName) {
		return new SEL(SEL_REGISTER_NAME.invokeLong(new Object[] {selectorName}));
	}

}
