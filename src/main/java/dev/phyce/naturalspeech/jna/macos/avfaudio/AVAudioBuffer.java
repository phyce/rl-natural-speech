package dev.phyce.naturalspeech.jna.macos.avfaudio;

import dev.phyce.naturalspeech.jna.macos.objc.ID;
import dev.phyce.naturalspeech.jna.macos.objc.LibObjC;
import dev.phyce.naturalspeech.jna.macos.objc.SEL;

public interface AVAudioBuffer {
	ID idClass = LibObjC.objc_getClass("AVAudioBuffer");

	SEL selFormat = LibObjC.sel_registerName("format");
	SEL selAudioBufferList = LibObjC.sel_registerName("audioBufferList");

	static ID getFormat(ID self) {
		return LibObjC.objc_msgSend(self, selFormat);
	}

	static ID getAudioBufferList(ID self) {
		return LibObjC.objc_msgSend(self, selAudioBufferList);
	}
}
