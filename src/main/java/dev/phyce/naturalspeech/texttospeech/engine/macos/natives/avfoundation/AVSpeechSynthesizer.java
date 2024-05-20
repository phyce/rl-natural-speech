package dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfoundation;

import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.Block;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.foundation.NSObject;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.SEL;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.LibObjC;

/**
 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechsynthesizer?language=objc">Apple Documentation</a>
 */
public interface AVSpeechSynthesizer {

	ID idClass = LibObjC.objc_getClass("AVSpeechSynthesizer");

	SEL selSpeakUtterance = LibObjC.sel_registerName("speakUtterance:");
	SEL selWriteUtteranceToBufferCallback = LibObjC.sel_registerName("writeUtterance:toBufferCallback:");

	static ID alloc() {
		return LibObjC.objc_msgSend(LibObjC.objc_msgSend(idClass, NSObject.selAlloc), NSObject.selInit);
	}

	/**
	 * Writes the utterance to the buffer using the bufferCallbackBlock.
	 * <br>
	 * <pre> {@code
	 *  - (void)writeUtterance:(AVSpeechUtterance *)utterance
	 *       toBufferCallback:(AVSpeechSynthesizerBufferCallback)bufferCallback;
	 * } </pre>
	 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechsynthesizer/3141659-writeutterance?language=objc">Apple Documentation</a>
	 */
	static void writeUtteranceToBufferCallback(ID self, ID utterance, Block bufferCallbackBlock) {
		LibObjC.objc_msgSend(self, selWriteUtteranceToBufferCallback, utterance, bufferCallbackBlock);
	}

	/**
	 * Speaks the utterance using systems' audio output.
	 * @param self
	 * @param utterance
	 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechsynthesizer/1619686-speakutterance?language=objc">Apple Documentation</a>
	 */
	static void speakUtterance(ID self, ID utterance) {
		LibObjC.objc_msgSend(self, selSpeakUtterance, utterance);
	}
}
