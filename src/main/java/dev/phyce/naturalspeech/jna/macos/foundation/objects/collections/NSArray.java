package dev.phyce.naturalspeech.jna.macos.foundation.objects.collections;

import dev.phyce.naturalspeech.jna.macos.foundation.Foundation;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.ID;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.SEL;
import dev.phyce.naturalspeech.jna.macos.foundation.util.Foundations;

public interface NSArray {

	ID idClass = Foundation.FOUNDATION.objc_getClass("NSArray");

	SEL selCount = Foundation.FOUNDATION.sel_registerName("count");
	SEL selIndexOfObject = Foundation.FOUNDATION.sel_registerName("indexOfObject:");

	SEL selContainsObject = Foundation.FOUNDATION.sel_registerName("containsObject:");
	SEL selObjectAtIndex = Foundation.FOUNDATION.sel_registerName("objectAtIndex:");

	/**
	 * @param self
	 * @return unsigned int, represented as a long in Java (no unsigned int in Java)
	 */
	static long getCount(ID self) {
		return Foundations.objc_msgSend(self, selCount).longValue();
	}

	static long getIndexOfObject(ID self, ID object) {
		return Foundations.objc_msgSend(self, selIndexOfObject, object).longValue();
	}

	static boolean containsObject(ID self, ID object) {
		return Foundations.objc_msgSend(self, selContainsObject, object).booleanValue();
	}

	static ID getObjectAtIndex(ID self, long index) {
		return Foundations.objc_msgSend(self, selObjectAtIndex, index);
	}
}
