package dev.phyce.naturalspeech.jna.macos.foundation.objects;

import com.sun.jna.Pointer;
import dev.phyce.naturalspeech.jna.macos.foundation.Foundation;

@SuppressWarnings("UnusedReturnValue")
public interface NSObject {

    ID idClass = Foundation.FOUNDATION.objc_getClass("NSObject");

    Pointer selAlloc = Foundation.FOUNDATION.sel_registerName("alloc");
    Pointer selInit = Foundation.FOUNDATION.sel_registerName("init");
	Pointer selRetain = Foundation.FOUNDATION.sel_registerName("retain");
    Pointer selRelease = Foundation.FOUNDATION.sel_registerName("release");
	Pointer selRetainCount = Foundation.FOUNDATION.sel_registerName("retainCount");
    Pointer selPerformSelectorOnMainThread$withObject$waitUntilDone
            = Foundation.FOUNDATION.sel_registerName("performSelectorOnMainThread:withObject:waitUntilDone:");

	static long getRetainCount(ID self) {
		return Foundation.FOUNDATION.objc_msgSend(self, selRetainCount).longValue();
	}

	static void retain(ID self) {
		Foundation.FOUNDATION.objc_msgSend(self, selRetain);
	}

	static ID release(ID self) {
		return Foundation.FOUNDATION.objc_msgSend(self, selRelease);
	}

}
