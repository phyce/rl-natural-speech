package dev.phyce.naturalspeech;

import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SpeechEventHandlerTest {

	private static SpeechEventHandler handler(SpeechEngine systemMessages, SpeechEngine clanChat, SpeechEngine loginLogout) {
		return handler(new NaturalSpeechConfig() {
			@Override
			public SpeechEngine systemMessages() {
				return systemMessages;
			}

			@Override
			public SpeechEngine loginLogout() {
				return loginLogout;
			}

			@Override
			public SpeechEngine clanChat() {
				return clanChat;
			}

			@Override
			public SpeechEngine clanGuestChat() {
				return clanChat;
			}

			@Override
			public SpeechEngine groupIronmanChat() {
				return clanChat;
			}
		});
	}

	private static SpeechEventHandler handler(NaturalSpeechConfig config) {
		return new SpeechEventHandler(null, null, config, null, null, null, null, null);
	}

	private static ChatMessage message(ChatMessageType type) {
		ChatMessage message = new ChatMessage();
		message.setType(type);
		message.setName("");
		message.setMessage("Zezima has logged in.");
		return message;
	}

	@Test
	public void everySystemVoiceTypeFollowsTheSystemMessagesSetting() {
		SpeechEventHandler muted = handler(SpeechEngine.OFF, SpeechEngine.PIPER, SpeechEngine.PIPER);
		SpeechEventHandler unmuted = handler(SpeechEngine.PIPER, SpeechEngine.PIPER, SpeechEngine.PIPER);

		for (ChatMessageType type : ChatMessageType.values()) {
			if (!SpeechEventHandler.isChatSystemVoice(type) || SpeechEventHandler.isAlwaysMuted(type)) continue;

			assertTrue(type + " should be muted", muted.isMessageTypeDisabledInConfig(message(type)));
			assertFalse(type + " should be spoken", unmuted.isMessageTypeDisabledInConfig(message(type)));
		}
	}

	@Test
	public void alwaysMutedTypesAreNeverSpoken() {
		SpeechEventHandler handler = handler(SpeechEngine.PIPER, SpeechEngine.PIPER, SpeechEngine.PIPER);

		for (ChatMessageType type : new ChatMessageType[] {
			ChatMessageType.ENGINE,
			ChatMessageType.BROADCAST,
			ChatMessageType.IGNORENOTIFICATION,
			ChatMessageType.TRADE,
			ChatMessageType.PLAYERRELATED,
			ChatMessageType.TENSECTIMEOUT,
			ChatMessageType.CLAN_CREATION_INVITATION,
			ChatMessageType.CLAN_GIM_FORM_GROUP,
			ChatMessageType.CLAN_GIM_GROUP_WITH
		}) {
			assertTrue(type + " should be muted", handler.isChatMessageMuted(message(type)));
		}

		assertFalse(SpeechEventHandler.isAlwaysMuted(ChatMessageType.WELCOME));
		assertFalse(SpeechEventHandler.isAlwaysMuted(ChatMessageType.GAMEMESSAGE));
		assertFalse(SpeechEventHandler.isAlwaysMuted(ChatMessageType.CLAN_MESSAGE));
		assertFalse(SpeechEventHandler.isAlwaysMuted(ChatMessageType.LOGINLOGOUTNOTIFICATION));
		assertFalse(SpeechEventHandler.isAlwaysMuted(ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void playerChatIgnoresTheSystemMessagesSetting() {
		SpeechEventHandler handler = handler(SpeechEngine.OFF, SpeechEngine.PIPER, SpeechEngine.PIPER);

		assertFalse(handler.isMessageTypeDisabledInConfig(message(ChatMessageType.PUBLICCHAT)));
		assertFalse(handler.isMessageTypeDisabledInConfig(message(ChatMessageType.CLAN_CHAT)));
	}

	@Test
	public void eachMessageTypeReadsItsOwnEngineSetting() {
		SpeechEventHandler handler = handler(new NaturalSpeechConfig() {
			@Override
			public SpeechEngine publicChat() {
				return SpeechEngine.SYSTEM;
			}

			@Override
			public SpeechEngine clanChat() {
				return SpeechEngine.PIPER;
			}

			@Override
			public SpeechEngine privateChat() {
				return SpeechEngine.OFF;
			}
		});

		assertEquals(SpeechEngine.SYSTEM, handler.engineFor(message(ChatMessageType.PUBLICCHAT)));
		assertEquals(SpeechEngine.PIPER, handler.engineFor(message(ChatMessageType.CLAN_CHAT)));
		assertEquals(SpeechEngine.OFF, handler.engineFor(message(ChatMessageType.PRIVATECHAT)));
		assertTrue(handler.isMessageTypeDisabledInConfig(message(ChatMessageType.PRIVATECHAT)));
	}

	@Test
	public void loginLogoutHasItsOwnEngineBehindSystemMessages() {
		SpeechEventHandler silenced = handler(SpeechEngine.PIPER, SpeechEngine.PIPER, SpeechEngine.OFF);
		assertTrue(silenced.isMessageTypeDisabledInConfig(message(ChatMessageType.LOGINLOGOUTNOTIFICATION)));
		assertFalse(silenced.isMessageTypeDisabledInConfig(message(ChatMessageType.GAMEMESSAGE)));

		SpeechEventHandler system = handler(SpeechEngine.PIPER, SpeechEngine.PIPER, SpeechEngine.SYSTEM);
		assertEquals(SpeechEngine.SYSTEM, system.engineFor(message(ChatMessageType.LOGINLOGOUTNOTIFICATION)));
		assertEquals(SpeechEngine.PIPER, system.engineFor(message(ChatMessageType.GAMEMESSAGE)));
	}

	@Test
	public void clanBroadcastsNeedBothClanChatAndSystemMessages() {
		ChatMessage broadcast = message(ChatMessageType.CLAN_MESSAGE);
		broadcast.setSender("Druid Kings");
		broadcast.setMessage("ItsNipp has achieved a new Chambers of Xeric Challenge Mode personal best: 29:57.60");

		assertTrue(handler(SpeechEngine.PIPER, SpeechEngine.OFF, SpeechEngine.PIPER).isMessageTypeDisabledInConfig(broadcast));
		assertEquals(SpeechEngine.SYSTEM, handler(SpeechEngine.SYSTEM, SpeechEngine.PIPER, SpeechEngine.PIPER).engineFor(broadcast));
	}

	@Test
	public void everyMessageTypeResolvesToSomeEngine() {
		SpeechEventHandler handler = handler(SpeechEngine.PIPER, SpeechEngine.PIPER, SpeechEngine.PIPER);

		for (ChatMessageType type : ChatMessageType.values()) {
			assertNotNull(type + " does not resolve to an engine", handler.engineFor(message(type)));
		}
	}
}
