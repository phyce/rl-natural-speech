package dev.phyce.naturalspeech.jna.macos.objc.avfoundation;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.jna.macos.objc.ID;
import dev.phyce.naturalspeech.jna.macos.objc.foundation.NSString;
import dev.phyce.naturalspeech.jna.macos.objc.SEL;
import dev.phyce.naturalspeech.jna.macos.objc.foundation.NSArray;
import dev.phyce.naturalspeech.jna.macos.objc.ObjC;

public interface AVSpeechSynthesisVoice {

	ID idClass = ObjC.objc_getClass("AVSpeechSynthesisVoice");


	SEL selSpeechVoices = ObjC.sel_registerName("speechVoices");
	SEL selName = ObjC.sel_registerName("name");
	SEL selGender = ObjC.sel_registerName("gender");
	SEL selIdentifier = ObjC.sel_registerName("identifier");
	SEL selLanguage = ObjC.sel_registerName("language");

	static String getLanguage(ID self) {
		ID nsString = ObjC.objc_msgSend(self, selLanguage);
		return NSString.getJavaString(nsString);
	}

	static String getIdentifier(ID self) {
		ID nsString = ObjC.objc_msgSend(self, selIdentifier);
		return NSString.getJavaString(nsString);
	}

	static String getName(ID self) {
		ID nsString = ObjC.objc_msgSend(self, selName);
		return NSString.getJavaString(nsString);
	}

	static Gender getGender(ID self) {
		ID genderID = ObjC.objc_msgSend(self, selGender);
		int genderValue = genderID.intValue();
		switch (genderValue) {
			case AVSpeechSynthesisVoiceGender.AVSpeechSynthesisVoiceGenderMale:
				return Gender.MALE;
			case AVSpeechSynthesisVoiceGender.AVSpeechSynthesisVoiceGenderFemale:
				return Gender.FEMALE;
			case AVSpeechSynthesisVoiceGender.AVSpeechSynthesisVoiceGenderUnspecified:
			default:
				return Gender.OTHER;
		}
	}

	/**
	 * {@code + (NSArray<AVSpeechSynthesisVoice *> *)speechVoices}
	 * <br>
	 * A pointer to an NSArray of pointers to AVSpeechSynthesisVoice objects.
	 *
	 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechsynthesisvoice/1619697-speechvoices?language=objc">Apple Documentation</a>
	 */
	static ID[] getSpeechVoices() {
		ID nsArray = ObjC.objc_msgSend(idClass, selSpeechVoices);
		long count = NSArray.getCount(nsArray);

		ID[] voices = new ID[(int) count];
		for (int i = 0; i < count; i++) {
			ID voice = NSArray.getObjectAtIndex(nsArray, i);
			voices[i] = voice;
		}

		return voices;
	}
}
