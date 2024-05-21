package dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfoundation;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;

public interface AVSpeechSynthesizerBufferCallback extends Callback {

	/**
	 * {@code typedef void (^AVSpeechSynthesizerBufferCallback)(AVAudioBuffer *buffer);}
	 * <b>This callback is called many times smaller segments until AVAudioBuffer's frameLength == 0.</b>
	 * Must join all segments to get the full audio data.
	 *
	 * @see <a href="https://developer.apple.com/documentation/avfaudio/avspeechsynthesizerbuffercallback?language=objc">Apple Documentation</a>
	 */
	void invoke(Pointer block, Pointer avAudioBuffer);
}
