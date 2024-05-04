package dev.phyce.naturalspeech.clienteventhandlers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.statics.AudioLineNames;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.eventbus.Subscribe;
import org.slf4j.LoggerFactory;

@Slf4j
public class CommandExecutedEventHandler {

	private final Client client;
	private final VoiceManager voiceManager;

	@Inject
	public CommandExecutedEventHandler(Client client, VoiceManager voiceManager) {
		this.client = client;
		this.voiceManager = voiceManager;
	}

	@Subscribe
	private void onCommandExecuted(CommandExecuted commandExecuted) {
		String[] args = commandExecuted.getArguments();

		switch (commandExecuted.getCommand()) {
			case "nslogger": {
				final Logger logger = (Logger) LoggerFactory.getLogger(NaturalSpeechPlugin.class.getPackageName());
				String message;
				Level currentLoggerLevel = logger.getLevel();

				if (args.length < 1) {
					message = "Logger level is currently set to " + currentLoggerLevel;
				}
				else {
					Level newLoggerLevel = Level.toLevel(args[0], currentLoggerLevel);
					logger.setLevel(newLoggerLevel);
					message = "Logger level has been set to " + newLoggerLevel;
				}

				client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
				break;
			}
			case "setvoice": {
				if (args.length < 2) {
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"use ::setvoice model:id username, for example ::setvoice libritts:2 Zezima", null);
				}
				else {
					VoiceID voiceId = VoiceID.fromIDString(args[0]);
					String username = Arrays.stream(args).skip(1).reduce((a, b) -> a + " " + b).orElse(args[1]);
					if (voiceId == null) {
						client.addChatMessage(ChatMessageType.CONSOLE, "", "voice id " + args[1] + " is invalid.",
							null);
					}
					else {
						voiceManager.setDefaultVoiceIDForUsername(username, voiceId);
						client.addChatMessage(ChatMessageType.CONSOLE, "", username + " voice is set to " + args[0],
							null);
					}
				}
				break;
			}
			case "unsetvoice": {
				if (args.length < 1) {
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"use ::unsetvoice username, for example ::unsetvoice Zezima", null);
				}
				else {
					String username = Arrays.stream(args).reduce((a, b) -> a + " " + b).orElse(args[0]);
					voiceManager.resetForUsername(username);
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"All voices are removed for " + username, null);
				}
				break;
			}
			case "checkvoice": {
				String username;
				if (args.length < 1) {
					//					client.addChatMessage(ChatMessageType.CONSOLE, "",
					//						"use ::checkvoice username, for example ::checkvoice Zezima", null);
					username = AudioLineNames.LOCAL_USER;
				}
				else {
					username = Arrays.stream(args).reduce((a, b) -> a + " " + b).orElse(args[0]);
				}

				List<VoiceID> voiceIds = voiceManager.checkVoiceIDWithUsername(username);
				if (voiceIds == null) {
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"There are no voices set for " + username + ".", null);
				}
				else {
					String idStr = voiceIds.stream().map(VoiceID::toString).reduce((a, b) -> a + ", " + b)
						.orElse("No voice set");
					client.addChatMessage(ChatMessageType.CONSOLE, "", username + " voice is set to " + idStr, null);
				}
				break;
			}
		}
	}


}
