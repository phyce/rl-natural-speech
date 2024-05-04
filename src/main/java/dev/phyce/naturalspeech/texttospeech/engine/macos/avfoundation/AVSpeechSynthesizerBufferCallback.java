package dev.phyce.naturalspeech.texttospeech.engine.macos.avfoundation;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;

public interface AVSpeechSynthesizerBufferCallback extends Callback {

	/**
	 * {@code typedef void (^AVSpeechSynthesizerBufferCallback)(AVAudioBuffer *buffer);}
	 *
	 * <b>This callback is called many times smaller segments until AVAudioBuffer's frameLength == 0.</b>
	 * Must join all segments to get the full audio data.
	 *
	 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechsynthesizerbuffercallback?language=objc">Apple Documentation</a>
	 * @note Due to JNA limitation, AVAudioBuffer cannot be ID but has to be Pointer.
	 * Otherwise, JNA will throw "argument type mismatch".
	 * Convert it manually to ID inside the invoke method.
	 */
	void invoke(Pointer block, Pointer avAudioBuffer);
}
