package dev.phyce.naturalspeech.jna.macos.objc.foundation;

import dev.phyce.naturalspeech.jna.macos.objc.ID;
import dev.phyce.naturalspeech.jna.macos.objc.ObjC;
import dev.phyce.naturalspeech.jna.macos.objc.SEL;

@SuppressWarnings("UnusedReturnValue")
public interface NSObject {

    ID idClass = ObjC.objc_getClass("NSObject");

    SEL selAlloc = ObjC.sel_registerName("alloc");
    SEL selInit = ObjC.sel_registerName("init");
	SEL selRetain = ObjC.sel_registerName("retain");
    SEL selRelease = ObjC.sel_registerName("release");
	SEL selRetainCount = ObjC.sel_registerName("retainCount");
    SEL selPerformSelectorOnMainThread$withObject$waitUntilDone
            = ObjC.sel_registerName("performSelectorOnMainThread:withObject:waitUntilDone:");

	static long getRetainCount(ID self) {
		return ObjC.objc_msgSend(self, selRetainCount).longValue();
	}

	static void retain(ID self) {
		ObjC.objc_msgSend(self, selRetain);
	}

	static ID release(ID self) {
		return ObjC.objc_msgSend(self, selRelease);
	}

}
