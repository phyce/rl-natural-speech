package dev.phyce.naturalspeech.jna.macos.foundation.objects;

import dev.phyce.naturalspeech.jna.macos.foundation.util.Foundation;

@SuppressWarnings("UnusedReturnValue")
public interface NSObject {

    ID idClass = Foundation.objc_getClass("NSObject");

    SEL selAlloc = Foundation.sel_registerName("alloc");
    SEL selInit = Foundation.sel_registerName("init");
	SEL selRetain = Foundation.sel_registerName("retain");
    SEL selRelease = Foundation.sel_registerName("release");
	SEL selRetainCount = Foundation.sel_registerName("retainCount");
    SEL selPerformSelectorOnMainThread$withObject$waitUntilDone
            = Foundation.sel_registerName("performSelectorOnMainThread:withObject:waitUntilDone:");

	static long getRetainCount(ID self) {
		return Foundation.objc_msgSend(self, selRetainCount).longValue();
	}

	static void retain(ID self) {
		Foundation.objc_msgSend(self, selRetain);
	}

	static ID release(ID self) {
		return Foundation.objc_msgSend(self, selRelease);
	}

}
