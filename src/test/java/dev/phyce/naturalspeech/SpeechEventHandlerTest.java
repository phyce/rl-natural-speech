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

	/**
	 * Every channel toggle is on, so the system messages toggle is the only thing under test.
	 */
	private static SpeechEventHandler handler(boolean systemMessagesEnabled) {
		NaturalSpeechConfig config = new NaturalSpeechConfig() {
			@Override
			public SpeechEngine systemMessages() {
				return systemMessagesEnabled ? SpeechEngine.PIPER : SpeechEngine.OFF;
			}

			@Override
			public SpeechEngine clanChat() {
				return SpeechEngine.PIPER;
			}

			@Override
			public SpeechEngine clanGuestChat() {
				return SpeechEngine.PIPER;
			}

			@Override
			public SpeechEngine groupIronmanChat() {
				return SpeechEngine.PIPER;
			}
		};

		// isMessageTypeDisabledInConfig only reads config, the rest of the graph is not involved
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
	public void friendLoginIsMutedWhenSystemMessagesDisabled() {
		// the message type in the original report - a friend logging in
		assertTrue(handler(false).isMessageTypeDisabledInConfig(message(ChatMessageType.LOGINLOGOUTNOTIFICATION)));
	}

	@Test
	public void friendLoginIsSpokenWhenSystemMessagesEnabled() {
		assertFalse(handler(true).isMessageTypeDisabledInConfig(message(ChatMessageType.LOGINLOGOUTNOTIFICATION)));
	}

	@Test
	public void everySystemVoiceTypeFollowsTheSystemMessagesToggle() {
		SpeechEventHandler muted = handler(false);
		SpeechEventHandler unmuted = handler(true);

		for (ChatMessageType type : ChatMessageType.values()) {
			if (!SpeechEventHandler.isChatSystemVoice(type)) continue;
			// never spoken at all, so they have no toggle to follow
			if (SpeechEventHandler.isAlwaysMuted(type)) continue;

			assertTrue("expected " + type + " to be muted when system messages are disabled",
				muted.isMessageTypeDisabledInConfig(message(type)));
			assertFalse("expected " + type + " to be spoken when system messages are enabled",
				unmuted.isMessageTypeDisabledInConfig(message(type)));
		}
	}

	@Test
	public void alwaysMutedTypesAreNeverSpoken() {
		// every toggle on - these must still be silent
		SpeechEventHandler handler = handler(true);

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
			assertTrue(type + " should be in the always-muted set", SpeechEventHandler.isAlwaysMuted(type));
			// the check runs before any dependency is touched, so nulls are fine here
			assertTrue("expected " + type + " to be muted with every toggle on",
				handler.isChatMessageMuted(message(type)));
		}
	}

	@Test
	public void messagesPeopleActuallyWantAreNotAlwaysMuted() {
		assertFalse(SpeechEventHandler.isAlwaysMuted(ChatMessageType.WELCOME));
		assertFalse(SpeechEventHandler.isAlwaysMuted(ChatMessageType.GAMEMESSAGE));
		assertFalse(SpeechEventHandler.isAlwaysMuted(ChatMessageType.CLAN_MESSAGE));
		assertFalse(SpeechEventHandler.isAlwaysMuted(ChatMessageType.LOGINLOGOUTNOTIFICATION));
		assertFalse(SpeechEventHandler.isAlwaysMuted(ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void playerChatIsUnaffectedByTheSystemMessagesToggle() {
		SpeechEventHandler muted = handler(false);

		assertFalse(muted.isMessageTypeDisabledInConfig(message(ChatMessageType.PUBLICCHAT)));
		assertFalse(muted.isMessageTypeDisabledInConfig(message(ChatMessageType.CLAN_CHAT)));
	}

	@Test
	public void loginLogoutCanBeSilencedWhileOtherSystemMessagesStillSpeak() {
		NaturalSpeechConfig systemOnLoginOff = new NaturalSpeechConfig() {
			@Override
			public SpeechEngine systemMessages() {
				return SpeechEngine.PIPER;
			}

			@Override
			public SpeechEngine loginLogout() {
				return SpeechEngine.OFF;
			}
		};
		SpeechEventHandler handler = new SpeechEventHandler(null, null, systemOnLoginOff, null, null, null, null, null);

		assertTrue(handler.isMessageTypeDisabledInConfig(message(ChatMessageType.LOGINLOGOUTNOTIFICATION)));
		assertFalse(handler.isMessageTypeDisabledInConfig(message(ChatMessageType.GAMEMESSAGE)));
	}

	@Test
	public void clanSystemMessagesStillRespectTheirChannelToggle() {
		NaturalSpeechConfig systemOnClanOff = new NaturalSpeechConfig() {
			@Override
			public SpeechEngine systemMessages() {
				return SpeechEngine.PIPER;
			}

			@Override
			public SpeechEngine clanChat() {
				return SpeechEngine.OFF;
			}
		};
		SpeechEventHandler handler = new SpeechEventHandler(null, null, systemOnClanOff, null, null, null, null, null);

		// the clan broadcast from the bug report - system messages on, clan chat off, still spoken
		ChatMessage broadcast = new ChatMessage();
		broadcast.setType(ChatMessageType.CLAN_MESSAGE);
		broadcast.setName("");
		broadcast.setSender("Druid Kings");
		broadcast.setMessage("ItsNipp has achieved a new Chambers of Xeric Challenge Mode " +
			"(Team Size: 4 players) personal best: 29:57.60");

		assertTrue(handler.isMessageTypeDisabledInConfig(broadcast));
		assertTrue(handler.isMessageTypeDisabledInConfig(message(ChatMessageType.CLAN_MESSAGE)));
	}

	private static SpeechEventHandler handler(NaturalSpeechConfig config) {
		return new SpeechEventHandler(null, null, config, null, null, null, null, null);
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
	public void clanBroadcastsFollowTheSystemEngineButStillNeedTheClanToggle() {
		SpeechEventHandler handler = handler(new NaturalSpeechConfig() {
			@Override
			public SpeechEngine systemMessages() {
				return SpeechEngine.SYSTEM;
			}

			@Override
			public SpeechEngine clanChat() {
				return SpeechEngine.PIPER;
			}
		});

		assertEquals(SpeechEngine.SYSTEM, handler.engineFor(message(ChatMessageType.CLAN_MESSAGE)));
		assertEquals(SpeechEngine.PIPER, handler.engineFor(message(ChatMessageType.CLAN_CHAT)));
	}

	@Test
	public void loginLogoutPicksItsOwnEngineOnceSystemMessagesAreOn() {
		SpeechEventHandler handler = handler(new NaturalSpeechConfig() {
			@Override
			public SpeechEngine systemMessages() {
				return SpeechEngine.PIPER;
			}

			@Override
			public SpeechEngine loginLogout() {
				return SpeechEngine.SYSTEM;
			}
		});

		assertEquals(SpeechEngine.SYSTEM, handler.engineFor(message(ChatMessageType.LOGINLOGOUTNOTIFICATION)));
		assertEquals(SpeechEngine.PIPER, handler.engineFor(message(ChatMessageType.GAMEMESSAGE)));
	}

	@Test
	public void everyMessageTypeResolvesToSomeEngine() {
		SpeechEventHandler handler = handler(true);

		for (ChatMessageType type : ChatMessageType.values()) {
			assertNotNull(type + " does not resolve to an engine", handler.engineFor(message(type)));
		}
	}
}
