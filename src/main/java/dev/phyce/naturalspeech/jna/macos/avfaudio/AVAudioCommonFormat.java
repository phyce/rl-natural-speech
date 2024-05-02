package dev.phyce.naturalspeech.jna.macos.avfaudio;

/*
typedef NS_ENUM(NSUInteger, AVAudioCommonFormat) {
	AVAudioOtherFormat = 0,
		AVAudioPCMFormatFloat32 = 1,
		AVAudioPCMFormatFloat64 = 2,
		AVAudioPCMFormatInt16 = 3,
		AVAudioPCMFormatInt32 = 4
} NS_ENUM_AVAILABLE(10_10, 8_0);
*/

import lombok.Getter;

/**
 *
 * 	  @enum		AVAudioCommonFormat
 *    @consta t	AVAudioOtherFormat
 * 					A format other than one of the common ones below.
 *    @consta t	AVAudioPCMFormatFloat32
 * 					Native-endian floats (this is the standard format).
 *    @consta t	AVAudioPCMFormatFloat64
 * 					Native-endian doubles.
 *    @consta t	AVAudioPCMFormatInt16
 * 					Signed 16-bit native-endian integers.
 *    @consta t	AVAudioPCMFormatInt32
 * 					Signed 32-bit native-endian integers.
 */
@Getter
public enum AVAudioCommonFormat {
	AVAudioOtherFormat(0),
	AVAudioPCMFormatFloat32(1),
	AVAudioPCMFormatFloat64(2),
	AVAudioPCMFormatInt16(3),
	AVAudioPCMFormatInt32(4);

	private final int value;

	AVAudioCommonFormat(int value) {
		this.value = value;
	}

	public static AVAudioCommonFormat fromValue(int value) {
		return values()[value];
	}

}
