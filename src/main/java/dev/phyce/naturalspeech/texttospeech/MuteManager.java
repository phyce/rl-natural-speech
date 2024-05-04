package dev.phyce.naturalspeech.texttospeech;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.utils.Standardize;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginSingleton
public class MuteManager {

	private final List<String> usernameListenList = new ArrayList<>();
	private final List<String> usernameMuteList = new ArrayList<>();


	// WARNING: Most List.remove List.get have overloads for using index,
	// Can easily use List.remove(int index) accidentally
	// When we want to use List.remove(Integer value)
	private final List<Integer> npcIdListenList = new ArrayList<>();
	private final List<Integer> npcIdMuteList = new ArrayList<>();

	private final ConfigManager configManager;

	@Getter
	@Setter
	private boolean listenMode = false;

	@Inject
	public MuteManager(ConfigManager configManager) {
		this.configManager = configManager;

		loadConfig();
	}

	public boolean isActorAllowed(Actor actor) {
		if (actor == null) {
			return false;
		}

		if (actor instanceof NPC) {
			return isNpcAllowed((NPC) actor);
		} else {
			return isUsernameAllowed(Standardize.getStandardName(actor));
		}
	}

	public boolean isActorMuted(Actor actor) {
		if (actor == null) {
			return false;
		}

		if (actor instanceof NPC) {
			return !isNpcAllowed((NPC) actor);
		} else {
			return !isUsernameAllowed(Standardize.getStandardName(actor));
		}
	}

	public boolean isActorListened(Actor actor) {
		if (actor == null) {
			return false;
		}

		if (actor instanceof NPC) {
			return isNpcListened((NPC) actor);
		} else {
			return isUsernameListened(Standardize.getStandardName(actor));
		}
	}

	public void muteActor(Actor actor) {
		if (actor == null) {
			return;
		}

		if (actor instanceof NPC) {
			muteNpc((NPC) actor);
		} else {
			muteUsername(Standardize.getStandardName(actor));
		}
	}

	public void unmuteActor(Actor actor) {
		if (actor == null) {
			return;
		}

		if (actor instanceof NPC) {
			unmuteNpc((NPC) actor);
		} else {
			unmuteUsername(Standardize.getStandardName(actor));
		}
	}

	public void listenActor(Actor actor) {
		if (actor == null) {
			return;
		}

		if (actor instanceof NPC) {
			listenNpc((NPC) actor);
		} else {
			listenUsername(Standardize.getStandardName(actor));
		}
	}

	public void unlistenActor(Actor actor) {
		if (actor == null) {
			return;
		}

		if (actor instanceof NPC) {
			unlistenNpc((NPC) actor);
		} else {
			unlistenUsername(Standardize.getStandardName(actor));
		}
	}

	public boolean isNpcAllowed(NPC npc) {
		if (listenMode) {
			return isNpcListened(npc);
		} else {
			return isNpcUnmuted(npc);
		}
	}
	public boolean isNpcIdAllowed(Integer npcId) {
		if (listenMode) {
			return npcIdListenList.contains(npcId);
		} else {
			return !npcIdMuteList.contains(npcId);
		}
	}

	public boolean isNpcListened(NPC npc) {
		int npcId = npc.getId();
		int compId = npc.getComposition().getId();

		return npcIdListenList.contains(npcId) || npcIdListenList.contains(compId);
	}

	public boolean isNpcUnmuted(NPC npc) {
		int npcId = npc.getId();
		int compId = npc.getComposition().getId();

		return !npcIdMuteList.contains(npcId) && !npcIdMuteList.contains(compId);
	}

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

	public void muteNpcId(Integer npcId) {
		npcIdMuteList.remove(npcId); // de-duplicate
		npcIdMuteList.add(npcId);
	}

	public void muteNpc(@NonNull NPC npc) {
		muteNpcId(npc.getId());
		muteNpcId(npc.getComposition().getId());
		// deprecating muting NPC names
		log.trace("Muting NpcId:{} CompID:{}",
			npc.getId(), npc.getComposition().getId());
	}

	public void unmuteNpc(@NonNull NPC npc) {
		unmuteNpcId(npc.getId());
		unmuteNpcId(npc.getComposition().getId());
		log.trace("Unmuting NpcId:{} CompID:{}",
			npc.getId(), npc.getComposition().getId());
	}

	public void listenNpc(NPC npc) {
		int npcId = npc.getId();
		int compId = npc.getComposition().getId();

		listenNpcId(npcId);
		listenNpcId(compId);
	}

	public void unlistenNpc(NPC npc) {
		int npcId = npc.getId();
		int compId = npc.getComposition().getId();

		unlistenNpcId(npcId);
		unlistenNpcId(compId);
	}

	public void unmuteNpcId(Integer npcId) {
		npcIdMuteList.remove(npcId);
	}

	public void listenNpcId(Integer npcId) {
		npcIdListenList.remove(npcId); // de-duplicate
		npcIdListenList.add(npcId);
	}

	public void unlistenNpcId(Integer npcId) {
		npcIdListenList.remove(npcId);
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
		this.npcIdListenList.clear();
		this.usernameListenList.clear();
	}

	public void saveConfig() {
		saveUsernameConfig();
		saveNpcIdConfig();
		saveListenModeConfig();
	}



	public void loadConfig() {
		loadUsernameConfig();
		loadNpcIdConfig();
		loadListenModeConfig();
	}

	private void saveListenModeConfig() {
		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.LISTEN_MODE, listenMode);
	}


	private void saveNpcIdConfig() {
		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.NPC_ID_LISTEN_LIST,
			Text.toCSV(npcIdListenList
				.stream()
				.map(Object::toString)
				.collect(Collectors.toList()))
		);
		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.NPC_ID_MUTE_LIST,
			Text.toCSV(npcIdMuteList
				.stream()
				.map(Object::toString)
				.collect(Collectors.toList()))
		);
	}

	private void saveUsernameConfig() {
		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.USERNAME_LISTEN_LIST,
			Text.toCSV(usernameListenList));
		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.USERNAME_MUTE_LIST,
			Text.toCSV(usernameMuteList));
	}

	private void loadListenModeConfig() {
		String result = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.LISTEN_MODE);
		if (result != null) {
			listenMode = Boolean.parseBoolean(result);
		} else {
			listenMode = false;
		}
	}


	private void loadNpcIdConfig() {
		{
			npcIdListenList.clear();
			String result = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.NPC_ID_LISTEN_LIST);
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
			String result = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.NPC_ID_MUTE_LIST);
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
			String result = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.USERNAME_LISTEN_LIST);
			if (result != null) {
				usernameListenList.addAll(Text.fromCSV(result));

			}
		}

		{
			usernameMuteList.clear();
			String result = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.USERNAME_MUTE_LIST);
			if (result != null) {
				usernameMuteList.addAll(Text.fromCSV(result));
			}
		}
	}
}
