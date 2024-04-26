package dev.phyce.naturalspeech.jna.macos.objc.avfoundation;

import com.sun.jna.Callback;
import dev.phyce.naturalspeech.jna.macos.objc.ID;

public interface AVSpeechSynthesizerBufferCallback extends Callback {

	/**
	 * {@code typedef void (^AVSpeechSynthesizerBufferCallback)(AVAudioBuffer *buffer);}
	 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechsynthesizerbuffercallback?language=objc">Apple Documentation</a>
	 */
	void invoke(ID avAudioBuffer);
}
