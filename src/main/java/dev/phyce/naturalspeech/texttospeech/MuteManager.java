package dev.phyce.naturalspeech.texttospeech;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import java.lang.reflect.Type;
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
		saveListenMode();
		saveMuteList();
		saveListenList();

		unsetDeprecated();
	}

	public void load() {

		loadListenMode();
		loadListenList();
		loadMuteList();

		appendDeprecated();
	}

	private void saveListenMode() {
		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.LISTEN_MODE, listenMode);
	}

	private void saveMuteList() {
		configManager.setConfiguration(
			NaturalSpeechPlugin.CONFIG_GROUP,
			ConfigKeys.MUTE_LIST,
			RuneLiteAPI.GSON.toJson(muteList)
		);
	}

	private void saveListenList() {
		configManager.setConfiguration(
			NaturalSpeechPlugin.CONFIG_GROUP,
			ConfigKeys.LISTEN_LIST,
			RuneLiteAPI.GSON.toJson(listenList)
		);
	}

	private void loadMuteList() {
		this.muteList.clear();

		String configString = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.MUTE_LIST);
		if (configString == null) return;

		try {
			Type muteListType = new TypeToken<Set<EntityID>>() {}.getType();
			muteList.addAll(RuneLiteAPI.GSON.fromJson(configString, muteListType));
		} catch (JsonSyntaxException e) {
			log.error("Failed to parse mute list from config: {}", configString, e);
		}
	}

	private void loadListenList() {
		this.listenList.clear();

		String configString = configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.LISTEN_LIST);
		if (configString == null) return;

		try {
			Type listenListType = new TypeToken<Set<EntityID>>() {}.getType();
			listenList.addAll(RuneLiteAPI.GSON.fromJson(configString, listenListType));
		} catch (JsonSyntaxException e) {
			log.error("Failed to parse listen list from config: {}", configString, e);
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

	@SuppressWarnings("deprecation")
	private void unsetDeprecated() {
		configManager.unsetConfiguration(
			NaturalSpeechPlugin.CONFIG_GROUP,
			ConfigKeys.DEPRECATED_NPC_ID_LISTEN_LIST
		);
		configManager.unsetConfiguration(
			NaturalSpeechPlugin.CONFIG_GROUP,
			ConfigKeys.DEPRECATED_NPC_ID_MUTE_LIST
		);
		configManager.unsetConfiguration(
			NaturalSpeechPlugin.CONFIG_GROUP,
			ConfigKeys.DEPRECATED_USERNAME_LISTEN_LIST
		);
		configManager.unsetConfiguration(
			NaturalSpeechPlugin.CONFIG_GROUP,
			ConfigKeys.DEPRECATED_USERNAME_MUTE_LIST
		);
	}

}
