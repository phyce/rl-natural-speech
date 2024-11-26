package dev.phyce.naturalspeech.texttospeech;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.utils.ClientHelper;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class TestVoiceManager {

	static {
		final Logger logger = (Logger) log;
		logger.setLevel(Level.TRACE);
	}

	@SuppressWarnings({"DataFlowIssue", "OptionalGetWithoutIsPresent"})
	@Test
	public void testVoiceSettings_Serialization_Version0() {
		ConfigManager configManager = mock(ConfigManager.class);
		ClientHelper clientHelper = mock(ClientHelper.class);
		NaturalSpeechConfig config = mock(NaturalSpeechConfig.class);

		final String version0Json =
			"{\"playerNameVoiceConfigData\":[{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":0}],\"playerName\":\"hidamaniak\"},{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":0}],\"playerName\":\"vyperxc\"},{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":0}],\"playerName\":\"phyce\"},{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":0}],\"playerName\":\"zanela\"},{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":0}],\"playerName\":\"dawncore\"},{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":1}],\"playerName\":\"&system\"},{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":359}],\"playerName\":\"&localuser\"}],\"npcIDVoiceConfigData\":[{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":0}],\"npcId\":13297},{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":0}],\"npcId\":1618},{\"voiceIDs\":[{\"modelName\":\"vctk\",\"piperVoiceID\":0}],\"npcId\":8628},{\"voiceIDs\":[{\"modelName\":\"vctk\",\"piperVoiceID\":0}],\"npcId\":2663},{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":0}],\"npcId\":10},{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":0}],\"npcId\":5035},{\"voiceIDs\":[{\"modelName\":\"vctk\",\"piperVoiceID\":0}],\"npcId\":8587},{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":0}],\"npcId\":6268}],\"npcNameVoiceConfigData\":[{\"voiceIDs\":[{\"modelName\":\"libritts\",\"piperVoiceID\":0}],\"npcName\":\"benny\"}]}\n";

		when(configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_CONFIG_KEY))
			.thenReturn(version0Json);

		Map<EntityID, VoiceID> npcs = Map.of(
			EntityID.id(13297), VoiceID.fromIDString("libritts:0").get(),
			EntityID.id(1618), VoiceID.fromIDString("libritts:0").get(),
			EntityID.id(8628), VoiceID.fromIDString("vctk:0").get(),
			EntityID.id(2663), VoiceID.fromIDString("vctk:0").get(),
			EntityID.id(10), VoiceID.fromIDString("libritts:0").get(),
			EntityID.id(5035), VoiceID.fromIDString("libritts:0").get(),
			EntityID.id(8587), VoiceID.fromIDString("vctk:0").get(),
			EntityID.id(6268), VoiceID.fromIDString("libritts:0").get()
		);

		Map<EntityID, VoiceID> players = Map.of(
			EntityID.name("hidamaniak"), VoiceID.fromIDString("libritts:0").get(),
			EntityID.name("vyperxc"), VoiceID.fromIDString("libritts:0").get(),
			EntityID.name("phyce"), VoiceID.fromIDString("libritts:0").get(),
			EntityID.name("zanela"), VoiceID.fromIDString("libritts:0").get(),
			EntityID.name("dawncore"), VoiceID.fromIDString("libritts:0").get(),
			EntityID.name("&system"), VoiceID.fromIDString("libritts:1").get(),
			EntityID.name("&localuser"), VoiceID.fromIDString("libritts:359").get()
		);


		VoiceManager voiceManager = new VoiceManager(configManager, clientHelper, config);

		// verify version0
		// defer assert until test is finished
		boolean success = verifyVoices(npcs, voiceManager);
		success = success && verifyVoices(players, voiceManager);

		// save back to json
		String savedJson = captureSavedJson(configManager, voiceManager, ConfigKeys.VOICE_CONFIG_KEY);

		// verify again with serialized json

		when(configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_CONFIG_KEY))
			.thenReturn(savedJson);
		voiceManager = new VoiceManager(configManager, clientHelper, config);

		success = success && verifyVoices(npcs, voiceManager);
		success = success && verifyVoices(players, voiceManager);

		assert success;
	}

	@SuppressWarnings({"DataFlowIssue", "OptionalGetWithoutIsPresent"})
	@Test
	public void testSettingVoice() {
		ConfigManager configManager = mock(ConfigManager.class);
		ClientHelper clientHelper = mock(ClientHelper.class);
		NaturalSpeechConfig config = mock(NaturalSpeechConfig.class);

		when(configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_CONFIG_KEY))
			.thenReturn("{version:1,settings:[]}");

		VoiceManager voiceManager = new VoiceManager(configManager, clientHelper, config);

		Map<EntityID, VoiceID> npcs = Map.of(
			EntityID.id(13297), VoiceID.fromIDString("libritts:0").get(),
			EntityID.id(1618), VoiceID.fromIDString("libritts:0").get(),
			EntityID.id(8628), VoiceID.fromIDString("vctk:0").get(),
			EntityID.id(2663), VoiceID.fromIDString("vctk:0").get(),
			EntityID.id(10), VoiceID.fromIDString("libritts:0").get(),
			EntityID.id(5035), VoiceID.fromIDString("libritts:0").get(),
			EntityID.id(8587), VoiceID.fromIDString("vctk:0").get(),
			EntityID.id(6268), VoiceID.fromIDString("libritts:0").get()
		);

		Map<EntityID, VoiceID> players = Map.of(
			EntityID.name("hidamaniak"), VoiceID.fromIDString("libritts:0").get(),
			EntityID.name("vyperxc"), VoiceID.fromIDString("libritts:0").get(),
			EntityID.name("phyce"), VoiceID.fromIDString("libritts:0").get(),
			EntityID.name("zanela"), VoiceID.fromIDString("libritts:0").get(),
			EntityID.name("dawncore"), VoiceID.fromIDString("libritts:0").get(),
			EntityID.name("&system"), VoiceID.fromIDString("libritts:1").get(),
			EntityID.name("&localuser"), VoiceID.fromIDString("libritts:359").get()
		);

		// set the voices and verify they applied
		npcs.forEach(voiceManager::set);
		players.forEach(voiceManager::set);

		boolean success = verifyVoices(npcs, voiceManager);
		success = success && verifyVoices(players, voiceManager);

		// save back to json
		String savedJson = captureSavedJson(configManager, voiceManager, ConfigKeys.VOICE_CONFIG_KEY);

		// verify again with serialized json
		when(configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_CONFIG_KEY))
			.thenReturn(savedJson);
		voiceManager = new VoiceManager(configManager, clientHelper, config);

		success = success && verifyVoices(npcs, voiceManager);
		success = success && verifyVoices(players, voiceManager);

		assert success;
	}

	@SuppressWarnings({"DataFlowIssue", "OptionalGetWithoutIsPresent"})
	@Test
	public void testBlacklist() {
		ConfigManager configManager = mock(ConfigManager.class);
		ClientHelper clientHelper = mock(ClientHelper.class);
		NaturalSpeechConfig config = mock(NaturalSpeechConfig.class);

		VoiceManager voiceManager = new VoiceManager(configManager, clientHelper, config);

		voiceManager.register(Voice.of(VoiceID.fromIDString("libritts:0").get(), Gender.FEMALE));
		voiceManager.register(Voice.of(VoiceID.fromIDString("vctk:0").get(), Gender.MALE));
		voiceManager.register(Voice.of(VoiceID.fromIDString("test:1").get(), Gender.MALE));

		assert voiceManager.speakable(VoiceID.fromIDString("libritts:0").get());
		assert voiceManager.speakable(VoiceID.fromIDString("vctk:0").get());
		assert voiceManager.speakable(VoiceID.fromIDString("test:1").get());

		voiceManager.blacklist(VoiceID.fromIDString("libritts:0").get());
		voiceManager.blacklist(VoiceID.fromIDString("vctk:0").get());
		assert !voiceManager.speakable(VoiceID.fromIDString("libritts:0").get());
		assert !voiceManager.speakable(VoiceID.fromIDString("vctk:0").get());

		voiceManager.unblacklist(VoiceID.fromIDString("libritts:0").get());
		voiceManager.unblacklist(VoiceID.fromIDString("vctk:0").get());
		assert voiceManager.speakable(VoiceID.fromIDString("libritts:0").get());
		assert voiceManager.speakable(VoiceID.fromIDString("vctk:0").get());

		voiceManager.blacklist(VoiceID.fromIDString("libritts:0").get());
		voiceManager.blacklist(VoiceID.fromIDString("vctk:0").get());

		String blacklistConfig = captureSavedJson(configManager, voiceManager, ConfigKeys.VOICE_BLACKLIST_KEY);
		when(configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_BLACKLIST_KEY))
			.thenReturn(blacklistConfig);


		voiceManager = new VoiceManager(configManager, clientHelper, config);
		voiceManager.register(Voice.of(VoiceID.fromIDString("libritts:0").get(), Gender.FEMALE));
		voiceManager.register(Voice.of(VoiceID.fromIDString("vctk:0").get(), Gender.MALE));
		voiceManager.register(Voice.of(VoiceID.fromIDString("test:1").get(), Gender.MALE));

		assert !voiceManager.speakable(VoiceID.fromIDString("libritts:0").get());
		assert !voiceManager.speakable(VoiceID.fromIDString("vctk:0").get());
		assert voiceManager.speakable(VoiceID.fromIDString("test:1").get());

	}

	private static String captureSavedJson(
		ConfigManager configManager,
		VoiceManager voiceManager,
		String configKey
	) {
		StringBuilder savedJsonCapture = new StringBuilder();
		doAnswer(call -> {
			savedJsonCapture.append((String) call.getArgument(2));
			return null;
		}).when(configManager).setConfiguration(eq(CONFIG_GROUP), eq(configKey), anyString());

		voiceManager.save();

		String savedJson = savedJsonCapture.toString();
		return savedJson;
	}

	private static boolean verifyVoices(Map<EntityID, VoiceID> settings, VoiceManager voiceManager) {
		boolean success = true;
		for (Map.Entry<EntityID, VoiceID> entry : settings.entrySet()) {
			EntityID entity = entry.getKey();
			VoiceID voice = entry.getValue();
			Optional<VoiceID> result = voiceManager.get(entity);
			if (result.isEmpty() || !result.get().equals(voice)) {
				success = false;
				log.error("{} VoiceID mismatch: {} != {}", entity, result, voice);
			}
			else {
				log.trace("{} VoiceID match: {} == {}", entity, result.get(), voice);
			}
		}
		return success;
	}
}
