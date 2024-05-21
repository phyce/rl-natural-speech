package dev.phyce.naturalspeech.texttospeech;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;
import net.runelite.http.api.RuneLiteAPI;

@Slf4j
@PluginSingleton
public class MuteManager {

	private final Set<EntityID> listenList = new HashSet<>();
	private final Set<EntityID> muteList = new HashSet<>();

	private final ConfigManager configManager;

	@Getter
	@Setter
	private boolean listenMode = false;

	@Inject
	public MuteManager(ConfigManager configManager) {
		this.configManager = configManager;

		load();
	}

	public boolean isAllowed(@NonNull EntityID eid) {
		if (listenMode) {
			return isListened(eid);
		}
		else {
			return !isMuted(eid);
		}
	}

	public boolean isMuted(EntityID eid) {
		return muteList.contains(eid);
	}

	public boolean isListened(EntityID eid) {
		return listenList.contains(eid);
	}

	public void listen(EntityID eid) {
		listenList.add(eid);
	}

	public void unlisten(EntityID eid) {
		listenList.remove(eid);
	}

	public void mute(EntityID eid) {
		muteList.add(eid);
	}

	public void unmute(EntityID eid) {
		muteList.remove(eid);
	}

	public void clearListens() {
		listenList.clear();
	}

	public void save() {
		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.LISTEN_MODE, listenMode);
	}

	public void load() {

		loadListenMode();
		loadListenList();
		loadMuteList();

		appendDeprecated();
	}

	private void loadMuteList() {
		this.muteList.clear();

		String result = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.MUTE_LIST);
		if (result != null) {
			Text.fromCSV(result)
				.stream()
				.map(json -> RuneLiteAPI.GSON.fromJson(json, EntityID.class))
				.forEach(muteList::add);
		}
	}

	private void loadListenList() {
		this.listenList.clear();

		String result = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.LISTEN_LIST);
		if (result != null) {
			Text.fromCSV(result)
				.stream()
				.map(json -> RuneLiteAPI.GSON.fromJson(json, EntityID.class))
				.forEach(listenList::add);
		}
	}

	private void loadListenMode() {
		String result = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.LISTEN_MODE);
		if (result != null) {
			listenMode = Boolean.parseBoolean(result);
		}
		else {
			listenMode = false;
		}
	}

	@SuppressWarnings("deprecation")
	private void appendDeprecated() {
		{
			String result = configManager.getConfiguration(
				NaturalSpeechPlugin.CONFIG_GROUP,
				ConfigKeys.DEPRECATED_NPC_ID_LISTEN_LIST
			);
			if (result != null) {
				Text.fromCSV(result)
					.stream()
					.map(Integer::parseInt)
					.map(EntityID::id)
					.forEach(listenList::add);
			}
		}

		{
			String result = configManager.getConfiguration(
				NaturalSpeechPlugin.CONFIG_GROUP,
				ConfigKeys.DEPRECATED_NPC_ID_MUTE_LIST
			);
			if (result != null) {
				Text.fromCSV(result)
					.stream()
					.map(Integer::parseInt)
					.map(EntityID::id)
					.forEach(muteList::add);
			}
		}

		{
			String result = configManager.getConfiguration(
				NaturalSpeechPlugin.CONFIG_GROUP,
				ConfigKeys.DEPRECATED_USERNAME_LISTEN_LIST
			);
			if (result != null) {
				Text.fromCSV(result)
					.stream()
					.map(EntityID::name)
					.forEach(listenList::add);
			}
		}

		{
			String result = configManager.getConfiguration(
				NaturalSpeechPlugin.CONFIG_GROUP,
				ConfigKeys.DEPRECATED_USERNAME_MUTE_LIST
			);
			if (result != null) {
				Text.fromCSV(result)
					.stream()
					.map(EntityID::name)
					.forEach(muteList::add);
			}
		}
	}


	//	private void saveNpcIdConfig() {
	//		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.NPC_ID_LISTEN_LIST,
	//			Text.toCSV(npcIdListenList
	//				.stream()
	//				.map(Object::toString)
	//				.collect(Collectors.toList()))
	//		);
	//		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.NPC_ID_MUTE_LIST,
	//			Text.toCSV(npcIdMuteList
	//				.stream()
	//				.map(Object::toString)
	//				.collect(Collectors.toList()))
	//		);
	//	}
	//
	//	private void saveUsernameConfig() {
	//		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.USERNAME_LISTEN_LIST,
	//			Text.toCSV(usernameListenList));
	//		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.USERNAME_MUTE_LIST,
	//			Text.toCSV(usernameMuteList));
	//	}


	//	private void loadNpcIdConfig() {
	//		{
	//			npcIdListenList.clear();
	//			String result = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.NPC_ID_LISTEN_LIST);
	//			if (result != null) {
	//				npcIdListenList.addAll(
	//					Text.fromCSV(result)
	//						.stream()
	//						.map(Integer::parseInt)
	//						.collect(Collectors.toList())
	//				);
	//			}
	//		}
	//
	//		{
	//			npcIdMuteList.clear();
	//			String result = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.NPC_ID_MUTE_LIST);
	//			if (result != null) {
	//				npcIdMuteList.addAll(
	//					Text.fromCSV(result)
	//						.stream()
	//						.map(Integer::parseInt)
	//						.collect(Collectors.toList())
	//				);
	//			}
	//		}
	//	}
	//
	//	private void loadUsernameConfig() {
	//		{
	//			usernameListenList.clear();
	//			String result = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.USERNAME_LISTEN_LIST);
	//			if (result != null) {
	//				usernameListenList.addAll(Text.fromCSV(result));
	//
	//			}
	//		}
	//
	//		{
	//			usernameMuteList.clear();
	//			String result = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.USERNAME_MUTE_LIST);
	//			if (result != null) {
	//				usernameMuteList.addAll(Text.fromCSV(result));
	//			}
	//		}
	//	}

}
