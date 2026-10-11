package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.configs.VoiceConfig;
import java.util.List;
import net.runelite.http.api.RuneLiteAPI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class VoiceIDTest {

	@Test
	public void parsesPiperAndSystemVoices() {
		VoiceID piper = VoiceID.fromIDString("libritts:360");
		assertEquals(new VoiceID("libritts", 360), piper);
		assertEquals(360, piper.getPiperVoiceID());
		assertEquals("libritts:360", piper.toVoiceIDString());

		VoiceID system = VoiceID.fromIDString("microsoft:david");
		assertEquals(new VoiceID("microsoft", "david"), system);
		assertEquals(-1, system.getPiperVoiceID());
	}

	@Test
	public void rejectsMalformedIDStrings() {
		assertNull(VoiceID.fromIDString(""));
		assertNull(VoiceID.fromIDString("libritts"));
		assertNull(VoiceID.fromIDString(":360"));
		assertNull(VoiceID.fromIDString("libritts:"));
		assertNull(VoiceID.fromIDString("libritts:   "));
		assertNull(VoiceID.fromIDString("libritts:david"));
	}

	@Test
	public void readsLegacyVoiceConfigs() {
		String legacy = "{\"playerNameVoiceConfigData\":[{\"voiceIDs\":["
			+ "{\"modelName\":\"libritts\",\"piperVoiceID\":399,\"priority\":0}],\"playerName\":\"phyce\"}],"
			+ "\"npcIDVoiceConfigData\":[],\"npcNameVoiceConfigData\":[]}";

		List<VoiceID> voices = VoiceConfig.fromJson(legacy).findUsername("phyce");

		assertNotNull("saved player voice was lost", voices);
		assertEquals(List.of(new VoiceID("libritts", 399)), voices);
	}

	@Test
	public void writesTheNewFieldAndRoundTrips() {
		VoiceID original = new VoiceID("macos", "samantha");

		String json = RuneLiteAPI.GSON.toJson(original);

		assertTrue(json, json.contains("\"id\":\"samantha\""));
		assertFalse(json, json.contains("piperVoiceID"));
		assertEquals(original, RuneLiteAPI.GSON.fromJson(json, VoiceID.class));
	}
}
