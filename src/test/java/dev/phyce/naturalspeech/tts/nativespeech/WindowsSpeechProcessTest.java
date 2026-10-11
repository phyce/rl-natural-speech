package dev.phyce.naturalspeech.tts.nativespeech;

import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.io.IOException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;
import org.junit.Test;

public class WindowsSpeechProcessTest {

	@Test
	public void shortensWindowsVoiceNames() {
		assertEquals("david", WindowsSpeechProcess.shortID("Microsoft David Desktop"));
		assertEquals("zira", WindowsSpeechProcess.shortID("Microsoft Zira Desktop - English (United States)"));
		assertEquals("george", WindowsSpeechProcess.shortID("Microsoft George - English (United Kingdom)"));
		assertEquals("ivona-brian", WindowsSpeechProcess.shortID("IVONA Brian"));
		assertEquals("desktop", WindowsSpeechProcess.shortID("Microsoft Desktop"));
		assertEquals("weird-name", WindowsSpeechProcess.shortID("weird: name"));
	}

	@Test
	public void speaksWithTheInstalledVoices() throws IOException {
		Assume.assumeTrue("not Windows", OSValidator.IS_WINDOWS);

		WindowsSpeechProcess process = WindowsSpeechProcess.start();
		try {
			assertFalse(process.getVoices().isEmpty());
			NativeVoice voice = process.getVoices().get(0);

			byte[] audio = process.generateAudio(voice.getSystemName(), "Welcome to RuneScape.");
			assertNotNull(audio);
			assertTrue("audio was suspiciously short: " + audio.length, audio.length > 1000);
			assertEquals(0, audio.length % 2);

			assertNotNull(process.generateAudio(voice.getSystemName(), "tab\there \"quoted\" and 'single' \\ backslash"));

			assertNull(process.generateAudio("No Such Voice Installed", "this cannot be spoken"));
			assertTrue(process.isAlive());

			VoiceID voiceID = voice.toVoiceID();
			assertEquals(NativeSpeech.WINDOWS_MODEL_NAME, voiceID.getModelName());
			assertEquals(voiceID, VoiceID.fromIDString(voiceID.toVoiceIDString()));
		}
		finally {
			process.stop();
		}
	}
}
