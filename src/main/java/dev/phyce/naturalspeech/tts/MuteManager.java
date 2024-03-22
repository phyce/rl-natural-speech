package dev.phyce.naturalspeech.tts;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class MuteManager {

	private static final String KEY_USERNAME_LISTEN_LIST = "usernameListenList";
	private static final String KEY_LISTEN_MODE = "listenMode";
	private final List<String> usernameListenList = new ArrayList<>();
	private static final String KEY_USERNAME_MUTE_LIST = "usernameMuteList";
	private final List<String> usernameMuteList = new ArrayList<>();


	private static final String KEY_NPC_ID_LISTEN_LIST = "npcIdListenList";
	private final List<Integer> npcIdListenList = new ArrayList<>();
	private static final String KEY_NPC_ID_MUTE_LIST = "npcIdMuteList";
	private final List<Integer> npcIdMuteList = new ArrayList<>();


	private static final String KEY_NPC_NAME_LISTEN_LIST = "npcNameListenList";
	private final List<String> npcNameListenList = new ArrayList<>();
	private static final String KEY_NPC_NAME_MUTE_LIST = "npcNameMuteList";
	private final List<String> npcNameMuteList = new ArrayList<>();
	private final ConfigManager configManager;

	@Getter
	@Setter
	private boolean listenMode = false;

	@Inject
	public MuteManager(ConfigManager configManager) {
		this.configManager = configManager;

		loadConfig();
	}

	public boolean isNpcAllowed(int npcId, String standardized_npcName) {
		if (listenMode) {
			return isNpcListened(npcId, standardized_npcName);
		} else {
			return isNpcUnmuted(npcId, standardized_npcName);
		}
	}
	public boolean isNpcListened(int npcId, String standardized_npcName) {
		return npcIdListenList.contains(npcId) || npcNameListenList.contains(standardized_npcName);
	}

	public boolean isNpcUnmuted(int npcId, String standardized_npcName) {
		return !npcIdMuteList.contains(npcId) && !npcNameMuteList.contains(standardized_npcName);
	}

//	public void unlistenNpcName(String npcName) {
//		npcNameListenList.remove(npcName);
//	}
//
//	public boolean isNpcIdAllowed(int npcId) {
//		return (listenMode && npcIdListenList.contains(npcId))
//			|| (!listenMode && !npcIdMuteList.contains(npcId));
//	}
//
	//	public boolean isNpcNameAllowed(String standardized_npcName) {
	//		return (listenMode && npcNameListenList.contains(standardized_npcName))

	//			|| (!listenMode && !npcNameMuteList.contains(standardized_npcName));
	//	}
	public boolean isUsernameAllowed(String standardized_username) {
		if (listenMode) {
			return isUsernameListened(standardized_username);
		} else {
			return isUsernameUnmuted(standardized_username);
		}
	}

	public boolean isUsernameUnmuted(String standardActorName) {
		return !usernameMuteList.contains(standardActorName);
	}

	public boolean isUsernameListened(String standardActorName) {
		return usernameListenList.contains(standardActorName);
	}

	public void muteNpcId(int npcId) {
		npcIdMuteList.remove(Integer.valueOf(npcId)); // de-duplicate
		npcIdMuteList.add(npcId);
	}

	public void unmuteNpcId(int npcId) {
		npcIdMuteList.remove(Integer.valueOf(npcId));
	}

	public void listenNpcId(int npcId) {
		npcIdListenList.remove(Integer.valueOf(npcId)); // de-duplicate
		npcIdListenList.add(npcId);
	}

	public void unlistenNpcId(int npcId) {
		npcIdListenList.remove(Integer.valueOf(npcId));
	}

	public void muteNpcName(String npcName) {
		npcNameMuteList.remove(npcName); // de-duplicate
		npcNameMuteList.add(npcName);
	}

	public void unmuteNpcName(String npcName) {
		npcNameMuteList.remove(npcName);
	}

	public void listenNpcName(String npcName) {
		npcNameListenList.remove(npcName); // de-duplicate
		npcNameListenList.add(npcName);
	}

	public void muteUsername(String username) {
		usernameMuteList.remove(username); // de-duplicate
		usernameMuteList.add(username);
	}

	public void unmuteUsername(String username) {
		usernameMuteList.remove(username);
	}

	public void listenUsername(String username) {
		usernameListenList.remove(username); // de-duplicate
		usernameListenList.add(username);
	}

	public void unlistenUsername(String username) {
		usernameListenList.remove(username);
	}

	public void clearListens() {
		this.npcNameListenList.clear();
		this.npcIdListenList.clear();
		this.usernameListenList.clear();
	}

	public void saveConfig() {
		saveUsernameConfig();
		saveNpcIdConfig();
		saveNpcNameConfig();
		saveListenModeConfig();
	}



	public void loadConfig() {
		loadUsernameConfig();
		loadNpcIdConfig();
		loadNpcNameConfig();
		loadListenModeConfig();
	}

	private void saveListenModeConfig() {
		configManager.setConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_LISTEN_MODE, listenMode);
	}

	private void saveNpcNameConfig() {
		configManager.setConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_NPC_NAME_LISTEN_LIST,
			Text.toCSV(npcNameListenList));
		configManager.setConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_NPC_NAME_MUTE_LIST,
			Text.toCSV(npcNameMuteList));
	}

	private void saveNpcIdConfig() {
		configManager.setConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_NPC_ID_LISTEN_LIST,
			Text.toCSV(npcIdListenList
				.stream()
				.map(Object::toString)
				.collect(Collectors.toList()))
		);
		configManager.setConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_NPC_ID_MUTE_LIST,
			Text.toCSV(npcIdMuteList
				.stream()
				.map(Object::toString)
				.collect(Collectors.toList()))
		);
	}

	private void saveUsernameConfig() {
		configManager.setConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_USERNAME_LISTEN_LIST,
			Text.toCSV(usernameListenList));
		configManager.setConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_USERNAME_MUTE_LIST,
			Text.toCSV(usernameMuteList));
	}

	private void loadListenModeConfig() {
		String result = configManager.getConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_LISTEN_MODE);
		if (result != null) {
			listenMode = Boolean.parseBoolean(result);
		} else {
			listenMode = false;
		}
	}

	private void loadNpcNameConfig() {
		{
			npcNameListenList.clear();
			String result = configManager.getConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_NPC_NAME_LISTEN_LIST);
			if (result != null) {
				npcNameListenList.addAll(Text.fromCSV(result));
			}
		}

		{
			npcNameMuteList.clear();
			String result = configManager.getConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_NPC_NAME_MUTE_LIST);
			if (result != null) {
				npcNameMuteList.addAll(Text.fromCSV(result));
			}
		}
	}

	private void loadNpcIdConfig() {
		{
			npcIdListenList.clear();
			String result = configManager.getConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_NPC_ID_LISTEN_LIST);
			if (result != null) {
				npcIdListenList.addAll(
					Text.fromCSV(result)
						.stream()
						.map(Integer::parseInt)
						.collect(Collectors.toList())
				);
			}
		}

		{
			npcIdMuteList.clear();
			String result = configManager.getConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_NPC_ID_MUTE_LIST);
			if (result != null) {
				npcIdMuteList.addAll(
					Text.fromCSV(result)
						.stream()
						.map(Integer::parseInt)
						.collect(Collectors.toList())
				);
			}
		}
	}

	private void loadUsernameConfig() {
		{
			usernameListenList.clear();
			String result = configManager.getConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_USERNAME_LISTEN_LIST);
			if (result != null) {
				usernameListenList.addAll(Text.fromCSV(result));

			}
		}

		{
			usernameMuteList.clear();
			String result = configManager.getConfiguration(NaturalSpeechConfig.CONFIG_GROUP, KEY_USERNAME_MUTE_LIST);
			if (result != null) {
				usernameMuteList.addAll(Text.fromCSV(result));
			}
		}
	}
}
