package dev.phyce.naturalspeech.jna.macos.foundation.objects.avfoundation;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.ID;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.NSString;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.SEL;
import static dev.phyce.naturalspeech.jna.macos.foundation.objects.avfoundation.AVSpeechSynthesisVoiceGender.AVSpeechSynthesisVoiceGenderFemale;
import static dev.phyce.naturalspeech.jna.macos.foundation.objects.avfoundation.AVSpeechSynthesisVoiceGender.AVSpeechSynthesisVoiceGenderMale;
import static dev.phyce.naturalspeech.jna.macos.foundation.objects.avfoundation.AVSpeechSynthesisVoiceGender.AVSpeechSynthesisVoiceGenderUnspecified;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.collections.NSArray;
import dev.phyce.naturalspeech.jna.macos.foundation.util.AutoRelease;
import dev.phyce.naturalspeech.jna.macos.foundation.util.Foundation;

public interface AVSpeechSynthesisVoice {

	ID idClass = Foundation.objc_getClass("AVSpeechSynthesisVoice");


	SEL selSpeechVoices = Foundation.sel_registerName("speechVoices");
	SEL selName = Foundation.sel_registerName("name");
	SEL selGender = Foundation.sel_registerName("gender");
	SEL selIdentifier = Foundation.sel_registerName("identifier");
	SEL selLanguage = Foundation.sel_registerName("language");

	static String getLanguage(ID self) {
		ID nsString = Foundation.objc_msgSend(self, selLanguage);
		return NSString.getJavaString(nsString);
	}

	static String getIdentifier(ID self) {
		ID nsString = Foundation.objc_msgSend(self, selIdentifier);
		return NSString.getJavaString(nsString);
	}

	static String getName(ID self) {
		ID nsString = Foundation.objc_msgSend(self, selName);
		return NSString.getJavaString(nsString);
	}

	static Gender getGender(ID self) {
		ID genderID = Foundation.objc_msgSend(self, selGender);
		int genderValue = genderID.intValue();
		switch (genderValue) {
			case AVSpeechSynthesisVoiceGenderMale:
				return Gender.MALE;
			case AVSpeechSynthesisVoiceGenderFemale:
				return Gender.FEMALE;
			case AVSpeechSynthesisVoiceGenderUnspecified:
			default:
				return Gender.OTHER;
		}
	}

	/**
	 * {@code + (NSArray<AVSpeechSynthesisVoice *> *)speechVoices}
	 * <br>
	 * A pointer to an NSArray of pointers to AVSpeechSynthesisVoice objects.
	 *
	 * @see <a href="https://developer.apple.com/documentation/avfoundation/avspeechsynthesisvoice/1619619-speechvoices?language=objc">Apple Documentation</a>
	 */
	static ID[] getSpeechVoices() {
		ID nsArray = Foundation.objc_msgSend(idClass, selSpeechVoices);
		long count = NSArray.getCount(nsArray);

		ID[] voices = new ID[(int) count];
		for (int i = 0; i < count; i++) {
			ID voice = NSArray.getObjectAtIndex(nsArray, i);
			voices[i] = voice;
		}

		return voices;
	}
}
