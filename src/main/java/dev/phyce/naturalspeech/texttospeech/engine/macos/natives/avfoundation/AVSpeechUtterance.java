package dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfoundation;

import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.foundation.NSObject;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.foundation.NSString;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.SEL;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.LibObjC;

/**
 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechutterance?language=objc">Apple Documentation</a>
 */
public interface AVSpeechUtterance {
	ID idClass = LibObjC.objc_getClass("AVSpeechUtterance");
	SEL selSpeechUtteranceWithString = LibObjC.sel_registerName("speechUtteranceWithString:");

	SEL selSetRate = LibObjC.sel_registerName("setRate:");
	SEL selSetPitchMultiplier = LibObjC.sel_registerName("setPitchMultiplier:");
	SEL selSetVolume = LibObjC.sel_registerName("setVolume:");
	SEL selSetVoice = LibObjC.sel_registerName("setVoice:");

	SEL selSpeechString = LibObjC.sel_registerName("speechString");
	SEL selRate = LibObjC.sel_registerName("rate");
	SEL selPitchMultiplier = LibObjC.sel_registerName("pitchMultiplier");
	SEL selVolume = LibObjC.sel_registerName("volume");
	SEL selVoice = LibObjC.sel_registerName("voice");


	/**
	 * {@code + (AVSpeechUtterance *)speechUtteranceWithString:(NSString *)string}
	 * <br>
	 * A pointer to an AVSpeechUtterance object.
	 *
	 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechutterance/1619668-speechutterancewithstring?language=objc">Apple Documentation</a>
	 * @memory Special note, this returned object exists in the AutoReleasePool, so it is not necessary to release it.
	 */
	static ID getSpeechUtteranceWithString(String string) {
		ID nsString = NSString.alloc(string);
		ID result = LibObjC.objc_msgSend(idClass, selSpeechUtteranceWithString, nsString);
		NSObject.release(nsString);
		return result;
	}

	static void setVoice(ID self, ID voice) {
		LibObjC.objc_msgSend(self, selSetVoice, voice);
	}

	static ID getVoice(ID self) {
		return LibObjC.objc_msgSend(self, selVoice);
	}

	static ID getSpeechString(ID self) {
		return LibObjC.objc_msgSend(self, selSpeechString);
	}
}
