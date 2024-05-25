package dev.phyce.naturalspeech.texttospeech;

import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TestMuteManager {

	@Test
	public void testMute() {

		List<EntityID> muted = List.of(
			EntityID.id(999),
			EntityID.id(1),
			EntityID.id(2),
			EntityID.id(3),
			EntityID.id(0),
			EntityID.name("Dawncore"),
			EntityID.name("Zanela"),
			EntityID.name("Phyce"),
			EntityID.name("VyperXC"),
			EntityID.name("Hidamaniak")
		);

		ConfigManager configManager = mock(ConfigManager.class);
		MuteManager muteManager = new MuteManager(configManager);

		// initial state
		for (EntityID entity : muted) {
			assert !muteManager.isMuted(entity);
			assert muteManager.isAllowed(entity);
		}

		// mute
		for (EntityID entity : muted) {
			muteManager.mute(entity);
			assert muteManager.isMuted(entity);
			assert !muteManager.isAllowed(entity);
		}

		// not listening mode, so listening does not override mute
		for (EntityID entity : muted) {
			muteManager.listen(entity);
			assert muteManager.isMuted(entity);
			assert !muteManager.isAllowed(entity);
		}

		muteManager.setListenMode(true);

		// listening mode overrides mute for listened
		for (EntityID entity : muted) {
			assert muteManager.isMuted(entity);
			assert muteManager.isAllowed(entity);
		}

		muteManager.setListenMode(false);

		// unmute
		for (EntityID entity : muted) {
			muteManager.unmute(entity);
			assert !muteManager.isMuted(entity);
			assert muteManager.isAllowed(entity);
		}

	}

	@Test
	public void testListen() {

		List<EntityID> listened = List.of(
			EntityID.id(999),
			EntityID.id(1),
			EntityID.id(2),
			EntityID.id(3),
			EntityID.id(0),
			EntityID.name("Dawncore"),
			EntityID.name("Zanela"),
			EntityID.name("Phyce")
		);

		ConfigManager configManager = mock(ConfigManager.class);
		MuteManager muteManager = new MuteManager(configManager);

		muteManager.setListenMode(true);

		// listen mode, but not listened
		for (EntityID entity : listened) {
			assert !muteManager.isListened(entity);
			assert !muteManager.isAllowed(entity);
		}

		// listen
		for (EntityID entity : listened) {
			muteManager.listen(entity);
			assert muteManager.isListened(entity);
			assert muteManager.isAllowed(entity);
		}

		// mute, ignored during listen mode
		for (EntityID entity : listened) {
			muteManager.mute(entity);
			assert muteManager.isMuted(entity);
			assert muteManager.isAllowed(entity);
		}

		muteManager.setListenMode(false);

		// mute, applied, no longer listen mode
		for (EntityID entity : listened) {
			assert muteManager.isMuted(entity);
			assert !muteManager.isAllowed(entity);
		}

	}

	@SuppressWarnings("deprecation")
	@Test
	public void testSaveLoad() {


		ConfigManager configManager = mock(ConfigManager.class);

		// test data
		List<EntityID> listened = List.of(
			EntityID.id(1),
			EntityID.id(2),
			EntityID.id(3),
			EntityID.name("Dawncore")
		);

		List<EntityID> muted = List.of(
			EntityID.id(11),
			EntityID.id(12),
			EntityID.id(13),
			EntityID.name("Zanela"),
			EntityID.name("Phyce")
		);
		List<Integer> deprecatedNPCMute = List.of(200, 201, 202);
		List<Integer> deprecatedNPCListen = List.of(300, 301, 302);
		List<String> deprecatedUserMute = List.of("LegacyMuted1", "LegacyMuted2");
		List<String> deprecatedUserListen = List.of("LegacyListened1", "LegacyListened2");
		String muteMode = "true";

		// mock deprecated test configs
		when(configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_NPC_ID_MUTE_LIST))
			.thenReturn(Text.toCSV(deprecatedNPCMute.stream().map(String::valueOf).collect(Collectors.toList())));
		when(configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_NPC_ID_LISTEN_LIST))
			.thenReturn(Text.toCSV(deprecatedNPCListen.stream().map(String::valueOf).collect(Collectors.toList())));
		when(configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_USERNAME_MUTE_LIST))
			.thenReturn(Text.toCSV(deprecatedUserMute));
		when(configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP,
			ConfigKeys.DEPRECATED_USERNAME_LISTEN_LIST))
			.thenReturn(Text.toCSV(deprecatedUserListen));
		when(configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.LISTEN_MODE))
			.thenReturn(muteMode);

		MuteManager muteManager = new MuteManager(configManager);

		// just set the listened and mute directly
		for (EntityID entity : listened) {
			muteManager.listen(entity);
		}

		for (EntityID entity : muted) {
			muteManager.mute(entity);
		}

		// assert the test data is applied
		_testSaveLoadAsserts(muteManager, muted, listened, deprecatedNPCMute, deprecatedNPCListen, deprecatedUserMute,
			deprecatedUserListen);

		///// save the test data and load it back into MuteManager

		// capture the configuration set by the mute manager
		StringBuilder muteListConfigCapture = captureConfigManagerSetConfiguration(ConfigKeys.MUTE_LIST, configManager);
		StringBuilder listenListConfigCapture = captureConfigManagerSetConfiguration(ConfigKeys.LISTEN_LIST, configManager);
		StringBuilder listenModeConfigCapture = captureConfigManagerSetConfiguration(ConfigKeys.LISTEN_MODE, configManager);
		muteManager.save();

		// verify that the deprecated configurations are unset
		verify(configManager)
			.unsetConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_NPC_ID_LISTEN_LIST);
		verify(configManager)
			.unsetConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_NPC_ID_MUTE_LIST);
		verify(configManager)
			.unsetConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_USERNAME_LISTEN_LIST);
		verify(configManager)
			.unsetConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_USERNAME_MUTE_LIST);

		// mock the unset deprecated configs
		when(configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_NPC_ID_LISTEN_LIST))
			.thenReturn(null);
		when(configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_NPC_ID_MUTE_LIST))
			.thenReturn(null);
		when(configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_USERNAME_LISTEN_LIST))
			.thenReturn(null);
		when(configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_USERNAME_MUTE_LIST))
			.thenReturn(null);

		// mock the config manager to return the saved configuration
		when(configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.MUTE_LIST))
			.thenReturn(muteListConfigCapture.toString());
		when(configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.LISTEN_LIST))
			.thenReturn(listenListConfigCapture.toString());
		when(configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.LISTEN_MODE))
			.thenReturn(listenModeConfigCapture.toString());
		// load the saved configuration
		muteManager.load();

		// assert the test configs are loaded back and applied
		_testSaveLoadAsserts(muteManager, muted, listened, deprecatedNPCMute, deprecatedNPCListen, deprecatedUserMute,
			deprecatedUserListen);

	}

	private static void _testSaveLoadAsserts(
		MuteManager muteManager,
		List<EntityID> muted,
		List<EntityID> listened,
		List<Integer> deprecatedNPCMute,
		List<Integer> deprecatedNPCListen,
		List<String> legacyUserMute,
		List<String> legacyUserListen
	) {
		assert muteManager.isListenMode();

		for (EntityID entity : muted) {
			assert muteManager.isMuted(entity);
			assert !muteManager.isAllowed(entity);
		}

		for (EntityID entity : listened) {
			assert muteManager.isListened(entity);
			assert muteManager.isAllowed(entity);
		}

		for (Integer npcId : deprecatedNPCMute) {
			assert muteManager.isMuted(EntityID.id(npcId));
			assert !muteManager.isAllowed(EntityID.id(npcId));
		}

		for (Integer npcId : deprecatedNPCListen) {
			assert muteManager.isListened(EntityID.id(npcId));
			assert muteManager.isAllowed(EntityID.id(npcId));
		}

		for (String username : legacyUserMute) {
			assert muteManager.isMuted(EntityID.name(username));
			assert !muteManager.isAllowed(EntityID.name(username));
		}

		for (String username : legacyUserListen) {
			assert muteManager.isListened(EntityID.name(username));
			assert muteManager.isAllowed(EntityID.name(username));
		}
	}

	private StringBuilder captureConfigManagerSetConfiguration(String configKey, ConfigManager configManager) {
		StringBuilder capture = new StringBuilder();

		lenient().doAnswer((call) -> {
			throw new RuntimeException("Unsupported capture type, if you need to test, just add another doAnswer");
		}).when(configManager)
			.setConfiguration(eq(NaturalSpeechPlugin.CONFIG_GROUP), eq(configKey), any());


		doAnswer((call) -> {
			capture.append((String) call.getArgument(2));
			return null;
		}).when(configManager)
			.setConfiguration(eq(NaturalSpeechPlugin.CONFIG_GROUP), eq(configKey), anyString());

		doAnswer((call) -> {
			capture.append(((boolean) call.getArgument(2)) ? "true" : "false");
			return null;
		}).when(configManager)
			.setConfiguration(eq(NaturalSpeechPlugin.CONFIG_GROUP), eq(configKey), anyBoolean());
		return capture;
	}
}
