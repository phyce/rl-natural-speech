package dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfaudio;

import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.LibObjC;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.SEL;

/**
 * @see <a href="https://developer.apple.com/documentation/avfaudio/avaudioformat?language=objc">Apple Documentation</a>
 */
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

	static long getChannelCount(ID self) {
		return LibObjC.objc_msgSend_long(self, selChannelCount);
	}

	static boolean getIsStandard(ID self) {
		return LibObjC.objc_msgSend_bool(self, selIsStandard);
	}

}
