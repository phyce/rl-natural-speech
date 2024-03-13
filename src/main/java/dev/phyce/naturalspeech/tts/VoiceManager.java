package dev.phyce.naturalspeech.tts;

import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.VOICE_CONFIG_FILE;
import dev.phyce.naturalspeech.configs.VoiceConfig;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCIDVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.PlayerNameVoiceConfigDatum;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;


@Singleton
public class VoiceManager {

	private VoiceConfig voiceConfig;
	private final TextToSpeech textToSpeech;
	private final ConfigManager configManager;

	@Inject
	public VoiceManager(NaturalSpeechPlugin plugin, ConfigManager configManager) {
		this.textToSpeech = plugin.getTextToSpeech();
		this.configManager = configManager;

		loadVoiceConfig();
	}

	public VoiceID getVoiceIDFromChatMessage(ChatMessage message) {
		VoiceID[] results;
		if (message.getName().equals(PluginHelper.getLocalPlayerUsername())) {
			results = voiceConfig.getPlayerVoiceIDs(message.getName());
		}
		else {
			switch (message.getType()) {
				//			case DIALOG:
				case WELCOME:
				case GAMEMESSAGE:
				case CONSOLE:
					results = voiceConfig.getNpcVoiceIDs(message.getName());
					break;
				default:
					results = voiceConfig.getPlayerVoiceIDs(message.getName());
					break;
			}
		}
		if (results == null) {
			for (ModelRepository.ModelLocal modelLocal : textToSpeech.getActiveModels()) {

				if (message.getType() == ChatMessageType.PUBLICCHAT) {
					Player user = PluginHelper.getFromUsername(message.getName());
					if (user != null) {
						int gender = user.getPlayerComposition().getGender();
						results =
							new VoiceID[] {modelLocal.calculateGenderedVoice(message.getName(), gender)};
						break;
					}
				}
				results = new VoiceID[] {modelLocal.calculateVoice(message.getName())};
			}
		}
		return results[0];
	}

	public VoiceID getVoiceIDFromNPCId(int npcId, String npcName) {
		npcName = npcName.toLowerCase();
		VoiceID[] results = {};
		results = voiceConfig.getNpcVoiceIDs(npcId);

		if (results == null) results = voiceConfig.getNpcVoiceIDs(npcName);

		if (results == null) {
			for (ModelRepository.ModelLocal modelLocal : textToSpeech.getActiveModels()) {
				results = new VoiceID[] {modelLocal.calculateVoice(npcName)};
			}
		}

		// FIXME(Louis) Fix getVoiceIDFromNPCId to consider all options
		return results[0];
	}

	public VoiceID[] getVoiceIDFromNPC(NPC npc) {
		VoiceID[] results = {};
		results = voiceConfig.getNpcVoiceIDs(npc.getName());

		if (results == null) {
			for (ModelRepository.ModelLocal modelLocal : textToSpeech.getActiveModels()) {
				results = new VoiceID[] {modelLocal.calculateVoice(npc.getName())};
			}
		}

		return results;
	}

	public VoiceID getVoiceIdForLocalPlayer() {
		return getVoiceIDFromUsername(PluginHelper.getLocalPlayerUsername());
	}

	public VoiceID getVoiceIDFromUsername(String username) {
		VoiceID[] results = {};
		results = voiceConfig.getPlayerVoiceIDs(username);

		if (results == null) {
			for (ModelRepository.ModelLocal modelLocal : textToSpeech.getActiveModels()) {
				results = new VoiceID[] {modelLocal.calculateVoice(username)};
			}
		}

		return results[0];
	}

	public void loadVoiceConfig() throws JsonSyntaxException {
		// FIXME(Louis): Reading from local file but saving to runtimeConfig right now
		//fetch from config manager
		//if empty, load in the
		voiceConfig = new VoiceConfig(VOICE_CONFIG_FILE);

		VoiceConfig customConfig = null;
		//fetch from config manager
		String json = configManager.getConfiguration(CONFIG_GROUP, VOICE_CONFIG_FILE);
		if (json != null) {
			customConfig = new VoiceConfig(json);
		}

		if (customConfig != null) {
			for (String key : voiceConfig.playerVoices.keySet()) {
				PlayerNameVoiceConfigDatum customDatum = customConfig.playerVoices.remove(key);
				if (customDatum != null) voiceConfig.playerVoices.put(key, customDatum);
			}

			for (Integer key : voiceConfig.npcIDVoices.keySet()) {
				NPCIDVoiceConfigDatum customDatum = customConfig.npcIDVoices.remove(key);
				if (customDatum != null) voiceConfig.npcIDVoices.put(key, customDatum);
			}

			for (String key : voiceConfig.npcNameVoices.keySet()) {
				NPCNameVoiceConfigDatum customDatum = customConfig.npcNameVoices.remove(key);
				if (customDatum != null) voiceConfig.npcNameVoices.put(key, customDatum);
			}

			voiceConfig.playerVoices.putAll(customConfig.playerVoices);
			voiceConfig.npcIDVoices.putAll(customConfig.npcIDVoices);
			voiceConfig.npcNameVoices.putAll(customConfig.npcNameVoices);
		}
	}

	public void saveVoiceConfig() {
		configManager.setConfiguration(CONFIG_GROUP, VOICE_CONFIG_FILE, voiceConfig.toJson());
	}

	public void setActorVoiceID(Actor actor, String model, int voiceId) {
		if (actor instanceof NPC) {
			NPC npc = ((NPC) actor);

			NPCIDVoiceConfigDatum voiceConfigDatum = new NPCIDVoiceConfigDatum(
				new VoiceID[] {new VoiceID(model, voiceId)},
				npc.getId()
			);

			voiceConfig.npcIDVoices.put(npc.getId(), voiceConfigDatum);
		}
		else {
			PlayerNameVoiceConfigDatum voiceConfigDatum = new PlayerNameVoiceConfigDatum(
				new VoiceID[] {new VoiceID(model, voiceId)},
				actor.getName().toLowerCase()
			);

			voiceConfig.playerVoices.put(actor.getName().toLowerCase(), voiceConfigDatum);
		}
	}
}
