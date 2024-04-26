package dev.phyce.naturalspeech.jna.macos.objc.avfoundation;

import dev.phyce.naturalspeech.jna.macos.objc.ID;
import static dev.phyce.naturalspeech.jna.macos.objc.foundation.NSObject.*;
import dev.phyce.naturalspeech.jna.macos.objc.SEL;
import dev.phyce.naturalspeech.jna.macos.objc.ObjC;

public interface AVSpeechSynthesizer {

	ID idClass = ObjC.objc_getClass("AVSpeechSynthesizer");

	SEL selSpeakUtterance = ObjC.sel_registerName("speakUtterance:");
	SEL selWriteUtteranceToBufferCallback = ObjC.sel_registerName("writeUtterance:toBufferCallback:");

	static ID alloc() {
		return ObjC.objc_msgSend(ObjC.objc_msgSend(idClass, selAlloc), selInit);
	}

	/**
	 * Writes the utterance to the buffer using the callback.
	 * <br>
	 * <pre> {@code
	 *  - (void)writeUtterance:(AVSpeechUtterance *)utterance
	 *       toBufferCallback:(AVSpeechSynthesizerBufferCallback)bufferCallback;
	 * } </pre>
	 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechsynthesizer/3141659-writeutterance?language=objc">Apple Documentation</a>
	 */
	static void writeUtteranceToBufferCallback(ID self, ID utterance, AVSpeechSynthesizerBufferCallback callback) {
		ObjC.objc_msgSend(self, selWriteUtteranceToBufferCallback, utterance, callback);
	}

	/**
	 * Speaks the utterance using systems' audio output.
	 * @param self
	 * @param utterance
	 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechsynthesizer/1619686-speakutterance?language=objc">Apple Documentation</a>
	 */
	static void speakUtterance(ID self, ID utterance) {
		ObjC.objc_msgSend(self, selSpeakUtterance, utterance);
	}
}
