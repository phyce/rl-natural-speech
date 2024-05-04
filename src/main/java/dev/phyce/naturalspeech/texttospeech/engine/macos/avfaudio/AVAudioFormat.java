package dev.phyce.naturalspeech.texttospeech.engine.macos.avfaudio;

import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.SEL;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.LibObjC;

public interface AVAudioFormat {
	ID idClass = LibObjC.objc_getClass("AVAudioFormat");

	SEL selSampleRate = LibObjC.sel_registerName("sampleRate");
	SEL selChannelCount = LibObjC.sel_registerName("channelCount");
	SEL selIsStandard = LibObjC.sel_registerName("isStandard");
	SEL selCommonFormat = LibObjC.sel_registerName("commonFormat");

	static AVAudioCommonFormat getCommonFormat(ID self) {
		return AVAudioCommonFormat.fromValue(LibObjC.objc_msgSend_int(self, selCommonFormat));
	}

	static double getSampleRate(ID self) {
		return LibObjC.objc_msgSend_double(self, selSampleRate);
	}

	static int getChannelCount(ID self) {
		return LibObjC.objc_msgSend_int(self, selChannelCount);
	}

	static boolean getIsStandard(ID self) {
		return LibObjC.objc_msgSend_bool(self, selIsStandard);
	}

}
