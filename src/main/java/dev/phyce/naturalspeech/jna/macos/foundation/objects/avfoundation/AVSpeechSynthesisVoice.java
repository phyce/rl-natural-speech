package dev.phyce.naturalspeech.jna.macos.foundation.objects.avfoundation;

import static dev.phyce.naturalspeech.jna.macos.foundation.Foundation.FOUNDATION;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.ID;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.NSString;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.SEL;
import dev.phyce.naturalspeech.jna.macos.foundation.objects.collections.NSArray;

public interface AVSpeechSynthesisVoice {

	ID idClass = FOUNDATION.objc_getClass("AVSpeechSynthesisVoice");

	SEL selSpeechVoices = FOUNDATION.sel_registerName("speechVoices");
	SEL selName = FOUNDATION.sel_registerName("name");

	static String getName(ID self) {
		ID nsString = FOUNDATION.objc_msgSend(self, selName);
		return NSString.getJavaString(nsString);
	}

	/**
	 * + (NSArray&lt;AVSpeechSynthesisVoice *&gt; *)speechVoices <br><br>
	 * Aka, a pointer to an NSArray of pointers to AVSpeechSynthesisVoice objects.
	 *
	 * @return NSArray&lt;AVSpeechSynthesisVoice *&gt; *
	 */
	static String[] getSpeechVoices() {
		ID nsArray = FOUNDATION.objc_msgSend(idClass, selSpeechVoices);
		long count = NSArray.getCount(nsArray);

		String[] voices = new String[(int) count];
		for (int i = 0; i < count; i++) {
			ID voice = NSArray.getObjectAtIndex(nsArray, i);
			voices[i] = getName(voice);
		}

		return voices;
	}
}
