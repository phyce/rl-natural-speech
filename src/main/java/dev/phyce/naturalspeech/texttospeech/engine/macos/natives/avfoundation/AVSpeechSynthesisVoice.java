package dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfoundation;

import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.foundation.NSString;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.SEL;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.foundation.NSArray;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.LibObjC;

public interface AVSpeechSynthesisVoice {

	ID idClass = LibObjC.objc_getClass("AVSpeechSynthesisVoice");


	SEL selSpeechVoices = LibObjC.sel_registerName("speechVoices");
	SEL selName = LibObjC.sel_registerName("name");
	SEL selGender = LibObjC.sel_registerName("gender");
	SEL selIdentifier = LibObjC.sel_registerName("identifier");
	SEL selLanguage = LibObjC.sel_registerName("language");

	static String getLanguage(ID self) {
		ID nsString = LibObjC.objc_msgSend(self, selLanguage);
		return NSString.getJavaString(nsString);
	}

	static String getIdentifier(ID self) {
		ID nsString = LibObjC.objc_msgSend(self, selIdentifier);
		return NSString.getJavaString(nsString);
	}

	static String getName(ID self) {
		ID nsString = LibObjC.objc_msgSend(self, selName);
		return NSString.getJavaString(nsString);
	}

	static AVSpeechSynthesisVoiceGender getGender(ID self) {
		return AVSpeechSynthesisVoiceGender.fromValue(LibObjC.objc_msgSend_int(self, selGender));
	}

	/**
	 * {@code + (NSArray<AVSpeechSynthesisVoice *> *)speechVoices}
	 * <br>
	 * A pointer to an NSArray of pointers to AVSpeechSynthesisVoice objects.
	 *
	 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechsynthesisvoice/1619697-speechvoices?language=objc">Apple Documentation</a>
	 */
	static ID[] getSpeechVoices() {
		ID nsArray = LibObjC.objc_msgSend(idClass, selSpeechVoices);
		long count = NSArray.getCount(nsArray);

		ID[] voices = new ID[(int) count];
		for (int i = 0; i < count; i++) {
			ID voice = NSArray.getObjectAtIndex(nsArray, i);
			voices[i] = voice;
		}

		return voices;
	}
}
