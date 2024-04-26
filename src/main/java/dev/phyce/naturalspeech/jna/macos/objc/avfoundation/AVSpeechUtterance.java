package dev.phyce.naturalspeech.jna.macos.objc.avfoundation;

import dev.phyce.naturalspeech.jna.macos.objc.ID;
import dev.phyce.naturalspeech.jna.macos.objc.foundation.NSString;
import dev.phyce.naturalspeech.jna.macos.objc.SEL;
import dev.phyce.naturalspeech.jna.macos.objc.javautil.AutoRelease;
import dev.phyce.naturalspeech.jna.macos.objc.ObjC;

public interface AVSpeechUtterance {
	ID idClass = ObjC.objc_getClass("AVSpeechUtterance");
	SEL selSpeechUtteranceWithString = ObjC.sel_registerName("speechUtteranceWithString:");

	SEL selSetRate = ObjC.sel_registerName("setRate:");
	SEL selSetPitchMultiplier = ObjC.sel_registerName("setPitchMultiplier:");
	SEL selSetVolume = ObjC.sel_registerName("setVolume:");
	SEL selSetVoice = ObjC.sel_registerName("setVoice:");

	SEL selSpeechString = ObjC.sel_registerName("speechString");
	SEL selRate = ObjC.sel_registerName("rate");
	SEL selPitchMultiplier = ObjC.sel_registerName("pitchMultiplier");
	SEL selVolume = ObjC.sel_registerName("volume");
	SEL selVoice = ObjC.sel_registerName("voice");

	/**
	 * {@code + (AVSpeechUtterance *)speechUtteranceWithString:(NSString *)string}
	 * <br>
	 * A pointer to an AVSpeechUtterance object.
	 *
	 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechutterance/1619668-speechutterancewithstring?language=objc">Apple Documentation</a>
	 */
	static ID allocSpeechUtteranceWithString(String string) {
		ID nsString = NSString.alloc(string);
		AutoRelease.register(nsString);
		return ObjC.objc_msgSend(idClass, selSpeechUtteranceWithString, nsString);
	}

	static void setVoice(ID self, ID voice) {
		ObjC.objc_msgSend(self, selSetVoice, voice);
	}

	static ID getVoice(ID self) {
		return ObjC.objc_msgSend(self, selVoice);
	}

	static ID getSpeechString(ID self) {
		return ObjC.objc_msgSend(self, selSpeechString);
	}
}
