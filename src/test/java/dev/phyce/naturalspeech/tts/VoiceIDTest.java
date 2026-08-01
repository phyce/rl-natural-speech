package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.configs.VoiceConfig;
import java.util.List;
import net.runelite.http.api.RuneLiteAPI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class VoiceIDTest {

	@Test
	public void parsesPiperVoices() {
		VoiceID voiceID = VoiceID.fromIDString("libritts:360");

		assertNotNull(voiceID);
		assertEquals("libritts", voiceID.getModelName());
		assertEquals("360", voiceID.getId());
		assertEquals(360, voiceID.getPiperVoiceID());
		assertEquals("libritts:360", voiceID.toVoiceIDString());
	}

	@Test
	public void parsesVoiceNamesContainingSpaces() {
		VoiceID voiceID = VoiceID.fromIDString("microsoft:Microsoft Hazel Desktop");

		assertNotNull(voiceID);
		assertEquals("microsoft", voiceID.getModelName());
		assertEquals("Microsoft Hazel Desktop", voiceID.getId());
		assertEquals("microsoft:Microsoft Hazel Desktop", voiceID.toVoiceIDString());
	}

	@Test
	public void nonNumericVoicesReportNoPiperID() {
		// -1 is what piper treats as "no speaker id", so a stray Windows voice cannot pick speaker 0
		assertEquals(-1, VoiceID.fromIDString("microsoft:Microsoft Hazel Desktop").getPiperVoiceID());
	}

	@Test
	public void rejectsMalformedIDStrings() {
		assertNull(VoiceID.fromIDString(null));
		assertNull(VoiceID.fromIDString(""));
		assertNull(VoiceID.fromIDString("libritts"));
		assertNull(VoiceID.fromIDString(":360"));
		assertNull(VoiceID.fromIDString("libritts:"));
		assertNull(VoiceID.fromIDString("libritts:   "));
	}

	@Test
	public void readsLegacyNumericConfigs() {
		String legacy = "{\"modelName\":\"libritts\",\"piperVoiceID\":399}";

		VoiceID voiceID = RuneLiteAPI.GSON.fromJson(legacy, VoiceID.class);

		assertNotNull(voiceID);
		assertEquals("libritts", voiceID.getModelName());
		assertEquals("399", voiceID.getId());
		assertEquals(399, voiceID.getPiperVoiceID());
	}

	@Test
	public void writesOnlyTheCurrentFields() {
		assertTrue(RuneLiteAPI.GSON.toJson(new VoiceID("libritts", 399)).contains("\"id\":\"399\""));

		String json = RuneLiteAPI.GSON.toJson(new VoiceID("microsoft", "Microsoft Hazel Desktop"));

		assertTrue(json, json.contains("\"id\":\"Microsoft Hazel Desktop\""));
		assertTrue(json, !json.contains("piperVoiceID"));
	}

	@Test
	public void roundTripsThroughGson() {
		VoiceID original = new VoiceID("microsoft", "Microsoft Hazel Desktop");

		VoiceID parsed = RuneLiteAPI.GSON.fromJson(RuneLiteAPI.GSON.toJson(original), VoiceID.class);

		assertEquals(original, parsed);
	}

	@Test
	public void loadsARealLegacyVoiceConfig() {
		String legacy = "{\"playerNameVoiceConfigData\":[{\"voiceIDs\":["
			+ "{\"modelName\":\"libritts\",\"piperVoiceID\":399,\"priority\":0}],\"playerName\":\"phyce\"}],"
			+ "\"npcIDVoiceConfigData\":[],\"npcNameVoiceConfigData\":[]}";

		VoiceConfig config = VoiceConfig.fromJson(legacy);
		List<VoiceID> voices = config.findUsername("phyce");

		assertNotNull("saved player voice was lost", voices);
		assertEquals(1, voices.size());
		assertEquals("libritts", voices.get(0).getModelName());
		assertEquals(399, voices.get(0).getPiperVoiceID());
	}
}
