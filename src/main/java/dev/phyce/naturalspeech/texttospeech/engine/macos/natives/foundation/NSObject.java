package dev.phyce.naturalspeech.texttospeech.engine.macos.natives.foundation;

import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.LibObjC;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.SEL;

@SuppressWarnings("UnusedReturnValue")
public interface NSObject {

	ID idClass = LibObjC.objc_getClass("NSObject");

	SEL selAlloc = LibObjC.sel_registerName("alloc");
	SEL selInit = LibObjC.sel_registerName("init");
	SEL selRetain = LibObjC.sel_registerName("retain");
	SEL selRelease = LibObjC.sel_registerName("release");
	SEL selRetainCount = LibObjC.sel_registerName("retainCount");
	SEL selPerformSelectorOnMainThread$withObject$waitUntilDone
		= LibObjC.sel_registerName("performSelectorOnMainThread:withObject:waitUntilDone:");

	static long getRetainCount(ID self) {
		return LibObjC.objc_msgSend_long(self, selRetainCount);
	}

	static void retain(ID self) {
		LibObjC.objc_msgSend(self, selRetain);
	}

	static ID release(ID self) {
		return LibObjC.objc_msgSend(self, selRelease);
	}

}
