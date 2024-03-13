package dev.phyce.naturalspeech.intruments;


import com.google.inject.Singleton;
import dev.phyce.naturalspeech.tts.VoiceID;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class VoiceLogger {

	private static VoiceLogger instance;

	public VoiceHistory current;

	public VoiceLogger() {
		instance = this;
	}

	public static void startRecord() {
		if (instance == null) {
			throw new RuntimeException("VoiceLogger was not initialized.");
		}
		instance._startRecord();
	}

	public static VoiceHistory stopRecord() {
		if (instance == null) {
			throw new RuntimeException("VoiceLogger was not initialized.");
		}
		return instance._stopRecord();
	}

	public static void addReason(VoiceID voiceID, String reason) {
		instance._addReason(voiceID, reason);
	}

	private void _addReason(VoiceID voiceID, String reason) {
		// silently ignore when not recording
		if (current == null) {
			return;
		}

		current.history.add(new VoiceHistory.Reason(voiceID, reason));
	}

	private void _startRecord() {
		// probably two threads fighting over the logger
		if (current != null) {
			throw new RuntimeException("VoiceLogger StartRecord called when already recording. " +
				"Might two threads fighting over the logger. (or called twice due to bug)");
		}
		current = new VoiceHistory();
	}

	private VoiceHistory _stopRecord() {
		// probably two threads fighting over the logger
		if (current == null) {
			throw new RuntimeException("VoiceLogger StartRecord called when already recording. " +
				"Might two threads fighting over the logger. (or called twice due to bug)");
		}

		VoiceHistory result = current;
		current = null;

		return result;
	}



}
