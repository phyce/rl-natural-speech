package dev.phyce.naturalspeech.jna.macos.foundation.objects.avfoundation;

import dev.phyce.naturalspeech.jna.macos.foundation.objects.ID;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.NSString;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.SEL;
import dev.phyce.naturalspeech.jna.macos.foundation.util.AutoRelease;
import dev.phyce.naturalspeech.jna.macos.foundation.util.Foundation;

public interface AVSpeechUtterance {
	ID idClass = Foundation.objc_getClass("AVSpeechUtterance");
	SEL selSpeechUtteranceWithString = Foundation.sel_registerName("speechUtteranceWithString:");

	SEL selSetRate = Foundation.sel_registerName("setRate:");
	SEL selSetPitchMultiplier = Foundation.sel_registerName("setPitchMultiplier:");
	SEL selSetVolume = Foundation.sel_registerName("setVolume:");
	SEL selSetVoice = Foundation.sel_registerName("setVoice:");

	SEL selSpeechString = Foundation.sel_registerName("speechString");
	SEL selRate = Foundation.sel_registerName("rate");
	SEL selPitchMultiplier = Foundation.sel_registerName("pitchMultiplier");
	SEL selVolume = Foundation.sel_registerName("volume");
	SEL selVoice = Foundation.sel_registerName("voice");

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
		return Foundation.objc_msgSend(idClass, selSpeechUtteranceWithString, nsString);
	}

	static void setVoice(ID self, ID voice) {
		Foundation.objc_msgSend(self, selSetVoice, voice);
	}

	static ID getVoice(ID self) {
		return Foundation.objc_msgSend(self, selVoice);
	}

	static ID getSpeechString(ID self) {
		return Foundation.objc_msgSend(self, selSpeechString);
	}
}
