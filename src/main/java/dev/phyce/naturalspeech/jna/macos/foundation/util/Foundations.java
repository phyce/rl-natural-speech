package dev.phyce.naturalspeech.jna.macos.foundation.util;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.NativeLibrary;
import dev.phyce.naturalspeech.jna.macos.foundation.Foundation;
import static dev.phyce.naturalspeech.jna.macos.foundation.Foundation.FOUNDATION;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.ID;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.SEL;
import java.lang.reflect.Proxy;

/**
 * Utility class for {@link Foundation}.
 */
public final class Foundations {

	private Foundations() {}

	private static final Function objc_msgSend;

	static {
		NativeLibrary lib = ((Library.Handler)Proxy.getInvocationHandler(FOUNDATION)).getNativeLibrary();
		objc_msgSend = lib.getFunction("objc_msgSend");
	}

	private static Object[] prepInvoke(ID id, SEL selector, Object[] args) {
		Object[] invokeArgs = new Object[args.length + 2];
		invokeArgs[0] = id;
		invokeArgs[1] = selector;
		System.arraycopy(args, 0, invokeArgs, 2, args.length);
		return invokeArgs;
	}

	public static ID objc_msgSend(ID receiver, SEL selector) {
		return FOUNDATION.objc_msgSend(receiver, selector);
	}

	public static ID objc_msgSend(ID receiver, SEL selector, Object... args) {
		return new ID(objc_msgSend.invokeLong(prepInvoke(receiver, selector, args)));
	}

}
