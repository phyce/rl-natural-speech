package dev.phyce.naturalspeech.jna.macos.foundation.objects.collections;

import com.sun.jna.Pointer;
import dev.phyce.naturalspeech.jna.macos.foundation.Foundation;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.ID;

public interface NSArray {

	ID idClass = Foundation.FOUNDATION.objc_getClass("NSArray");

	Pointer selCount = Foundation.FOUNDATION.sel_registerName("count");
	Pointer selIndexOfObject = Foundation.FOUNDATION.sel_registerName("indexOfObject:");

	Pointer selContainsObject = Foundation.FOUNDATION.sel_registerName("containsObject:");
	Pointer selObjectAtIndex = Foundation.FOUNDATION.sel_registerName("objectAtIndex:");

	static long getCount(ID self) {
		return Foundation.FOUNDATION.objc_msgSend(self, selCount).longValue();
	}

	static long getIndexOfObject(ID self, ID object) {
		return Foundation.FOUNDATION.objc_msgSend(self, selIndexOfObject, object).longValue();
	}

	static boolean containsObject(ID self, ID object) {
		return Foundation.FOUNDATION.objc_msgSend(self, selContainsObject, object).booleanValue();
	}

	static ID getObjectAtIndex(ID self, long index) {
		return Foundation.FOUNDATION.objc_msgSend(self, selObjectAtIndex, index);
	}
}
