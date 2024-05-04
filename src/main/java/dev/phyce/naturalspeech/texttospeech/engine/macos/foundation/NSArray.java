package dev.phyce.naturalspeech.texttospeech.engine.macos.foundation;

import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.SEL;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.LibObjC;

public interface NSArray {

	ID idClass = LibObjC.objc_getClass("NSArray");

	SEL selCount = LibObjC.sel_registerName("count");
	SEL selIndexOfObject = LibObjC.sel_registerName("indexOfObject:");

	SEL selContainsObject = LibObjC.sel_registerName("containsObject:");
	SEL selObjectAtIndex = LibObjC.sel_registerName("objectAtIndex:");

	/**
	 * @param self
	 * @return unsigned int, represented as a long in Java (no unsigned int in Java)
	 */
	static long getCount(ID self) {
		return LibObjC.objc_msgSend_long(self, selCount);
	}

	static long getIndexOfObject(ID self, ID object) {
		return LibObjC.objc_msgSend_long(self, selIndexOfObject, object);
	}

	static boolean containsObject(ID self, ID object) {
		return LibObjC.objc_msgSend_bool(self, selContainsObject, object);
	}

	static ID getObjectAtIndex(ID self, long index) {
		return LibObjC.objc_msgSend(self, selObjectAtIndex, index);
	}
}
