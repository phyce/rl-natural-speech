package dev.phyce.naturalspeech;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.statics.MagicNames;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import java.util.Arrays;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.eventbus.Subscribe;
import org.slf4j.LoggerFactory;

@Slf4j
public class CommandModule implements PluginModule {

	private final Client client;
	private final VoiceManager voiceManager;

	@Inject
	public CommandModule(Client client, VoiceManager voiceManager) {
		this.client = client;
		this.voiceManager = voiceManager;
	}

	@Subscribe
	private void onCommandExecuted(CommandExecuted commandExecuted) {
		String[] arguments = commandExecuted.getArguments();

		switch (commandExecuted.getCommand()) {
			case "nslogger": {
				final Logger logger = (Logger) LoggerFactory.getLogger(NaturalSpeechPlugin.class.getPackageName());
				String message;
				Level currentLoggerLevel = logger.getLevel();

				if (arguments.length < 1) {
					message = "Logger level is currently set to " + currentLoggerLevel;
				}
				else {
					Level newLoggerLevel = Level.toLevel(arguments[0], currentLoggerLevel);
					logger.setLevel(newLoggerLevel);
					message = "Logger level has been set to " + newLoggerLevel;
				}

				client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
				break;
			}
			case "setvoice": {
				if (arguments.length < 2) {
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"use ::setvoice model:id username, for example ::setvoice libritts:2 Zezima", null);
				}
				else {
					Optional<VoiceID> voiceId = VoiceID.fromIDString(arguments[0]);
					String username = Arrays.stream(arguments).skip(1).reduce((a, b) -> a + " " + b).orElse(arguments[1]);
					if (voiceId.isEmpty()) {
						client.addChatMessage(ChatMessageType.CONSOLE, "", "voice id " + arguments[1] + " is invalid.",
							null);
					}
					else {
						EntityID entityID = EntityID.name(username);
						voiceManager.set(entityID, voiceId.get());
						client.addChatMessage(ChatMessageType.CONSOLE, "", username + " voice is set to " + arguments[0],
							null);
					}
				}
				break;
			}
			case "unsetvoice": {
				if (arguments.length < 1) {
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"use ::unsetvoice username, for example ::unsetvoice Zezima", null);
				}
				else {
					String username = Arrays.stream(arguments).reduce((a, b) -> a + " " + b).orElse(arguments[0]);
					voiceManager.unset(EntityID.name(username));
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"All voices are removed for " + username, null);
				}
				break;
			}
			case "checkvoice": {
				String username;
				if (arguments.length < 1) username = MagicNames.LOCAL_PLAYER;
				else username = Arrays.stream(arguments).reduce((a, b) -> a + " " + b).orElse(arguments[0]);

				EntityID entityID = EntityID.name(username);
				if (!voiceManager.isSet(entityID)) {
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"There are no voices set for " + username + ".", null);
				}
				else {
					VoiceID voice = voiceManager.resolve(entityID);
					client.addChatMessage(ChatMessageType.CONSOLE, "", username + " voice is set to " +
						voice, null);
				}
				break;
			}
		}
	}
}
