package dev.phyce.naturalspeech.tts.elevenlabs;

import dev.phyce.naturalspeech.tts.VoiceID;

/**
 * Constants for the ElevenLabs backend. ElevenLabs voices live under the reserved {@code elevenlabs}
 * model namespace, so a voice id string looks like {@code elevenlabs:21m00Tcm4TlvDq8ikWAM} — the same
 * shape as {@code libritts:0} or {@code microsoft:david}.
 */
public final class ElevenLabs {

	public static final String MODEL_NAME = "elevenlabs";

	public static final String API_BASE = "https://api.elevenlabs.io";

	/**
	 * Headerless little-endian 16-bit PCM at 22.05 kHz, chosen to match {@link
	 * dev.phyce.naturalspeech.tts.AudioPlayer}'s fixed format so ElevenLabs audio can go through the
	 * same queues, volume control and playback gate as piper and the system voices.
	 */
	public static final String OUTPUT_FORMAT = "pcm_22050";

	private ElevenLabs() {}

	public static boolean isElevenLabsModel(String modelName) {
		return MODEL_NAME.equals(modelName);
	}

	public static VoiceID voiceID(String voiceId) {
		return new VoiceID(MODEL_NAME, voiceId);
	}
}
