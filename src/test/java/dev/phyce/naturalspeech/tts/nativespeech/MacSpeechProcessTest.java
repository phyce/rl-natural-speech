package dev.phyce.naturalspeech.tts.nativespeech;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;
import org.junit.Test;

public class MacSpeechProcessTest {

	private static final String SAY_LISTING = String.join("\n",
		"Albert              en_US    # Hello! My name is Albert.",
		"Bad News            en_US    # The light you see at the end of the tunnel is a train.",
		"Eddy (English (UK)) en_GB    # Hello! My name is Eddy.",
		"Evan (Enhanced)     en_US    # Hello! My name is Evan.",
		"Fiona               en-scotland # Hello, my name is Fiona.",
		"Majed (Arabic (World)) ar_001   # مرحبًا",
		"Samantha (English (US)) en_US    # Hello! My name is Samantha.",
		"Samantha (English (US)) en_US    # Hello! My name is Samantha.",
		"");

	@Test
	public void parsesTheSayVoiceListing() {
		List<NativeVoice> voices = MacSpeechProcess.parseVoices(SAY_LISTING);

		assertEquals(
			List.of("albert", "bad-news", "eddy-english-uk", "evan-enhanced", "fiona", "majed-arabic-world",
				"samantha-english-us"),
			voices.stream().map(NativeVoice::getId).collect(Collectors.toList()));
		assertEquals(
			List.of("Albert", "Bad News", "Eddy (English (UK))", "Evan (Enhanced)", "Fiona", "Majed (Arabic (World))",
				"Samantha (English (US))"),
			voices.stream().map(NativeVoice::getSystemName).collect(Collectors.toList()));
	}

	@Test
	public void knowsTheGenderOfCommonVoices() {
		assertEquals(Gender.MALE, MacSpeechProcess.parseGender("Albert"));
		assertEquals(Gender.MALE, MacSpeechProcess.parseGender("Eddy (English (UK))"));
		assertEquals(Gender.FEMALE, MacSpeechProcess.parseGender("Samantha (English (US))"));
		assertEquals(Gender.OTHER, MacSpeechProcess.parseGender("Bad News"));
		assertEquals(Gender.OTHER, MacSpeechProcess.parseGender("Majed (Arabic (World))"));
	}

	@Test
	public void voiceIDsSurviveAVoiceSetting() {
		for (NativeVoice voice : MacSpeechProcess.parseVoices(SAY_LISTING)) {
			VoiceID voiceID = voice.toVoiceID();

			assertEquals(NativeSpeech.MACOS_MODEL_NAME, voiceID.getModelName());
			assertFalse(voice.getId(), voice.getId().contains(" "));
			assertEquals(voiceID, VoiceID.fromIDString(voiceID.toVoiceIDString()));
		}
	}

	@Test
	public void stripsEmbeddedSpeechCommands() {
		assertEquals("hello slnc 60000]] there", MacSpeechProcess.neutralise("hello [[slnc 60000]] there"));
		assertEquals("slnc 60000]]", MacSpeechProcess.neutralise("[[[slnc 60000]]"));
	}

	@Test
	public void speaksWithTheInstalledVoices() throws IOException {
		Assume.assumeTrue("not macOS", OSValidator.IS_MAC);

		MacSpeechProcess process = MacSpeechProcess.start();
		try {
			assertFalse(process.getVoices().isEmpty());
			String voice = process.getVoices().get(0).getSystemName();

			byte[] audio = process.generateAudio(voice, "Welcome to RuneScape.");
			assertNotNull(audio);
			assertTrue("audio was suspiciously short: " + audio.length, audio.length > 1000);
			assertEquals(0, audio.length % 2);

			byte[] commands = process.generateAudio(voice, "hello [[slnc 20000]] there");
			assertNotNull(commands);
			assertTrue("embedded silence was spoken", commands.length < 22050 * 2 * 10);

			assertTrue(process.isAlive());
		}
		finally {
			process.stop();
		}
		assertFalse(process.isAlive());
	}
}
