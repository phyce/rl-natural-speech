package dev.phyce.naturalspeech.jna.macos.foundation.objects.avfoundation;

import dev.phyce.naturalspeech.jna.macos.foundation.objects.ID;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.SEL;
import dev.phyce.naturalspeech.jna.macos.foundation.util.Foundation;

public interface AVSpeechSynthesizer {

	ID idClass = Foundation.objc_getClass("AVSpeechSynthesizer");

	SEL selAlloc = Foundation.sel_registerName("alloc");
	SEL selInit = Foundation.sel_registerName("init");
	SEL selSpeakUtterance = Foundation.sel_registerName("speakUtterance:");

	static ID alloc() {
		return Foundation.objc_msgSend(Foundation.objc_msgSend(idClass, selAlloc), selInit);
	}

	static void speakUtterance(ID self, ID utterance) {
		Foundation.objc_msgSend(self, selSpeakUtterance, utterance);
	}
}
