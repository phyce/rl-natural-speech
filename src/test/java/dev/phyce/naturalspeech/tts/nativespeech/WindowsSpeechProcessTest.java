package dev.phyce.naturalspeech.tts.nativespeech;

import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Drives a real PowerShell process against the real Windows speech engine, so it only runs on
 * Windows. Skipped elsewhere rather than failed.
 */
public class WindowsSpeechProcessTest {

	@Before
	public void onlyOnWindows() {
		Assume.assumeTrue("not Windows", OSValidator.IS_WINDOWS);
	}

	@Test
	public void enumeratesInstalledVoices() throws IOException {
		WindowsSpeechProcess process = WindowsSpeechProcess.start();
		try {
			List<NativeVoice> voices = process.getVoices();

			assertFalse("Windows reported no voices at all", voices.isEmpty());
			for (NativeVoice voice : voices) {
				assertNotNull(voice.getId());
				assertFalse(voice.getId().isEmpty());
				assertNotNull(voice.getGender());
				assertNotNull(voice.getLanguage());
			}
		}
		finally {
			process.stop();
		}
	}

	@Test
	public void generatesPlayableAudio() throws IOException {
		WindowsSpeechProcess process = WindowsSpeechProcess.start();
		try {
			String voice = process.getVoices().get(0).getId();

			byte[] audio = process.generateAudio(voice, "Welcome to RuneScape.");

			assertNotNull("no audio came back", audio);
			assertTrue("audio was suspiciously short: " + audio.length, audio.length > 1000);
			// 16 bit mono means an even number of bytes, otherwise the format does not line up
			assertEquals("not 16 bit mono PCM", 0, audio.length % 2);
		}
		finally {
			process.stop();
		}
	}

	@Test
	public void survivesAnUnknownVoice() throws IOException {
		WindowsSpeechProcess process = WindowsSpeechProcess.start();
		try {
			assertNull(process.generateAudio("No Such Voice Installed", "this cannot be spoken"));

			// the process must stay usable, one bad voice should not take the engine down
			assertTrue(process.isAlive());
			String voice = process.getVoices().get(0).getId();
			assertNotNull(process.generateAudio(voice, "but this still works"));
		}
		finally {
			process.stop();
		}
	}

	@Test
	public void handlesTextThatWouldBreakTheFraming() throws IOException {
		WindowsSpeechProcess process = WindowsSpeechProcess.start();
		try {
			String voice = process.getVoices().get(0).getId();

			// tabs and quotes are the delimiter and would corrupt a naive protocol
			byte[] audio = process.generateAudio(voice, "tab\there \"quoted\" and 'single' \\ backslash");

			assertNotNull("framing broke on awkward text", audio);
			assertTrue(audio.length > 1000);
		}
		finally {
			process.stop();
		}
	}

	@Test
	public void voicesConvertToUsableVoiceIDs() throws IOException {
		WindowsSpeechProcess process = WindowsSpeechProcess.start();
		try {
			NativeVoice voice = process.getVoices().get(0);
			VoiceID voiceID = voice.toVoiceID();

			assertEquals(NativeSpeech.WINDOWS_MODEL_NAME, voiceID.getModelName());
			assertEquals(voice.getId(), voiceID.getId());
			// must survive the round trip through config, which stores the string form
			assertEquals(voiceID, VoiceID.fromIDString(voiceID.toVoiceIDString()));
		}
		finally {
			process.stop();
		}
	}
}
