package dev.phyce.naturalspeech.texttospeech.engine.macos.avfaudio;

/*
 */

import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <pre> {@code
 * typedef NS_ENUM(NSUInteger, AVAudioCommonFormat) {
 *     AVAudioOtherFormat = 0,
 *     AVAudioPCMFormatFloat32 = 1,
 *     AVAudioPCMFormatFloat64 = 2,
 *     AVAudioPCMFormatInt16 = 3,
 *     AVAudioPCMFormatInt32 = 4
 * } NS_ENUM_AVAILABLE(10_10, 8_0);
 * }</pre>
 *
 * @see <a href="https://developer.apple.com/documentation/avfaudio/avaudiocommonformat?language=objc">Apple Documentation</a>
 */
@Getter
@AllArgsConstructor
public enum AVAudioCommonFormat {
	AVAudioOtherFormat(0),
	AVAudioPCMFormatFloat32(1),
	AVAudioPCMFormatFloat64(2),
	AVAudioPCMFormatInt16(3),
	AVAudioPCMFormatInt32(4);

	private final int value;

	public static AVAudioCommonFormat fromValue(int value) {
		Preconditions.checkState(value >= 0 && value <= 4, "Invalid value: %d", value);
		return values()[value];
	}


}
