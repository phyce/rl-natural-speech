package dev.phyce.naturalspeech.jna.macos.foundation;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.ID;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.SEL;
import dev.phyce.naturalspeech.jna.macos.foundation.util.Foundations;

@SuppressWarnings({"BooleanMethodIsAlwaysInverted", "UnusedReturnValue"})
public interface Foundation extends Library {

	Foundation FOUNDATION = Native.load("Foundation", Foundation.class);

	ID objc_getClass(String className);

	ID objc_msgSend(Object[] args);

	ID objc_msgSend(ID self, SEL selector);

	ID objc_msgSend(ID self, SEL selector, Object arg);

//	ID objc_msgSend(ID self, SEL selector, ID idArg);
//
//	ID objc_msgSend(ID self, SEL selector, Pointer pointerArg);
//
//	ID objc_msgSend(ID self, SEL selector, Number numberArg);
//
//	ID objc_msgSend(ID self, SEL selector, boolean boolArg);

	// Dangerous because Java char implies 16-bit Unicode, but Objective-C char is 8-bit ASCII
	// ID objc_msgSend(ID receiver, SEL selector, char charArg); <- intentionally omitted!
	// ID objc_msgSend(ID receiver, SEL selector, String javaString); <- intentionally omitted!

	SEL sel_registerName(String selectorName);

}
