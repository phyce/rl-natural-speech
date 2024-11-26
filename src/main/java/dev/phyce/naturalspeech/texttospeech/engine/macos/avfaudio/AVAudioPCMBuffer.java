package dev.phyce.naturalspeech.texttospeech.engine.macos.avfaudio;

import com.sun.jna.Pointer;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.LibObjC;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.SEL;

/**
 * @see <a href="https://developer.apple.com/documentation/avfaudio/avaudiopcmbuffer?language=objc">Apple Documentation</a>
 */
public interface AVAudioPCMBuffer {
	ID idClass = LibObjC.objc_getClass("AVAudioPCMBuffer");


	SEL selFrameLength = LibObjC.sel_registerName("frameLength");
	SEL selFrameCapacity = LibObjC.sel_registerName("frameCapacity");
	SEL selStride = LibObjC.sel_registerName("stride");
	SEL selInt16ChannelData = LibObjC.sel_registerName("int16ChannelData");
	SEL selInt32ChannelData = LibObjC.sel_registerName("int32ChannelData");
	SEL selFloatChannelData = LibObjC.sel_registerName("floatChannelData");


	static long getFrameLength(ID self) {
		return LibObjC.objc_msgSend_long(self, selFrameLength);
	}

	static long getFrameCapacity(ID self) {
		return LibObjC.objc_msgSend_long(self, selFrameCapacity);
	}

	static long getStride(ID self) {
		return LibObjC.objc_msgSend_long(self, selStride);
	}

	static Pointer getInt16ChannelData(ID self) {
		return LibObjC.objc_msgSend_pointer(self, selInt16ChannelData);
	}

	static Pointer getInt32ChannelData(ID self) {
		return LibObjC.objc_msgSend_pointer(self, selInt32ChannelData);
	}

	static Pointer getFloatChannelData(ID self) {
		return LibObjC.objc_msgSend_pointer(self, selFloatChannelData);
	}

}
