package dev.phyce.naturalspeech.jna.macos.objc.foundation;

import dev.phyce.naturalspeech.jna.macos.objc.ID;
import dev.phyce.naturalspeech.jna.macos.objc.ObjC;
import dev.phyce.naturalspeech.jna.macos.objc.SEL;

public interface NSArray {

	ID idClass = ObjC.objc_getClass("NSArray");

	SEL selCount = ObjC.sel_registerName("count");
	SEL selIndexOfObject = ObjC.sel_registerName("indexOfObject:");

	SEL selContainsObject = ObjC.sel_registerName("containsObject:");
	SEL selObjectAtIndex = ObjC.sel_registerName("objectAtIndex:");

	/**
	 * @param self
	 * @return unsigned int, represented as a long in Java (no unsigned int in Java)
	 */
	static long getCount(ID self) {
		return ObjC.objc_msgSend(self, selCount).longValue();
	}

	static long getIndexOfObject(ID self, ID object) {
		return ObjC.objc_msgSend(self, selIndexOfObject, object).longValue();
	}

	static boolean containsObject(ID self, ID object) {
		return ObjC.objc_msgSend(self, selContainsObject, object).booleanValue();
	}

	static ID getObjectAtIndex(ID self, long index) {
		return ObjC.objc_msgSend(self, selObjectAtIndex, index);
	}
}
