package dev.phyce.naturalspeech.texttospeech.engine.macos.avfaudio;

import com.sun.jna.Pointer;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.LibObjC;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.SEL;

public interface AVAudioPCMBuffer {
	ID idClass = LibObjC.objc_getClass("AVAudioPCMBuffer");


	SEL selFrameLength = LibObjC.sel_registerName("frameLength");
	SEL selFrameCapacity = LibObjC.sel_registerName("frameCapacity");
	SEL selStride = LibObjC.sel_registerName("stride");
	SEL selint16ChannelData = LibObjC.sel_registerName("int16ChannelData");
	SEL selint32ChannelData = LibObjC.sel_registerName("int32ChannelData");


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
		return LibObjC.objc_msgSend_pointer(self, selint16ChannelData);
	}

	static Pointer getInt32ChannelData(ID self) {
		return LibObjC.objc_msgSend_pointer(self, selint32ChannelData);
	}

}
