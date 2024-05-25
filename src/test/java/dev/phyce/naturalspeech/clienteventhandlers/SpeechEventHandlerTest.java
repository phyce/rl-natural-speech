package dev.phyce.naturalspeech.clienteventhandlers;

import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.audio.VolumeManager;
import dev.phyce.naturalspeech.configs.json.ReplacementsJSON;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.singleton.PluginSingletonScope;
import dev.phyce.naturalspeech.spamdetection.SpamDetection;
import dev.phyce.naturalspeech.statics.Names;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.utils.ChatHelper;
import dev.phyce.naturalspeech.utils.ClientHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings("SpellCheckingInspection")
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class SpeechEventHandlerTest {

	private final PluginSingletonScope scope = new PluginSingletonScope();

	@Inject
	SpeechEventHandler speechEventHandler;

	@Inject
	ChatHelper chatHelper;

	//////////////////////////////////////////

	@Mock
	@Bind
	Client client;

	@Mock
	@Bind
	ClientThread clientThread;

	//////////////////////////////////////////

	@Mock
	@Bind
	NaturalSpeechConfig config;

	@Mock
	@Bind
	SpamDetection spamDetection;

	@Mock
	@Bind
	ClientHelper clientHelper;

	@Mock
	@Bind
	SpeechManager speechManager;

	@Mock
	@Bind
	VolumeManager volumeManager;

	@Mock
	@Bind
	VoiceManager voiceManager;

	@Mock
	@Bind
	MuteManager muteManager;


	@Before
	public void before() {
		scope.enter();
		try {
			Guice.createInjector(BoundFieldModule.of(this))
				.createChildInjector(
					new AbstractModule() {
						@Override
						protected void configure() {
							bindScope(PluginSingleton.class, scope);
						}
					})
				.injectMembers(this);
		} catch (ConfigurationException e) {
			throw new RuntimeException(e.getErrorMessages().toString());
		}

		// localPlayer clientThread invoke
		doAnswer(a -> {
			final Runnable r = a.getArgument(0);
			r.run();
			return null;
		}).when(clientThread).invokeAtTickEnd(any(Runnable.class));


		// All green light configuration to speak everything by default
		lenient().when(speechManager.isStarted()).thenReturn(true);
		lenient().when(muteManager.isAllowed(any())).thenReturn(true);
		lenient().when(clientHelper.getLevel(any())).thenReturn(999);
		lenient().when(spamDetection.isSpam(any(), any())).thenReturn(false);
		lenient().when(config.dialogEnabled()).thenReturn(true);
		lenient().when(config.publicChatEnabled()).thenReturn(true);
		lenient().when(config.privateChatEnabled()).thenReturn(true);
		lenient().when(config.privateOutChatEnabled()).thenReturn(true);
		lenient().when(config.clanChatEnabled()).thenReturn(true);
		lenient().when(config.clanGuestChatEnabled()).thenReturn(true);
		lenient().when(config.groupIronmanChatEnabled()).thenReturn(true);
		lenient().when(config.examineChatEnabled()).thenReturn(true);
		lenient().when(config.systemMesagesEnabled()).thenReturn(true);
		lenient().when(config.requestsEnabled()).thenReturn(true);
		lenient().when(config.muteCrowds()).thenReturn(0);
		lenient().when(config.muteGrandExchange()).thenReturn(false);

		Player localPlayer = mock(Player.class);
		lenient().when(client.getLocalPlayer()).thenReturn(localPlayer);
		// localPlayer mocked at (0,0,0)
		lenient().when(localPlayer.getWorldLocation()).thenReturn(new WorldPoint(0, 0, 0));

		List<Player> mockPlayers = new ArrayList<>();

		// 50 fake players at (10,0,0) worldpoint
		for (int i = 0; i < 50; i++) {
			Player closePlayer = mock(Player.class);
			lenient().when(closePlayer.getWorldLocation()).thenReturn(new WorldPoint(10, 0, 0));
			mockPlayers.add(closePlayer);
		}

		// 50 fake players at (100,0,0) worldpoint
		for (int i = 0; i < 50; i++) {
			Player farPlayer = mock(Player.class);
			lenient().when(farPlayer.getWorldLocation()).thenReturn(new WorldPoint(100, 0, 0));
			mockPlayers.add(farPlayer);
		}

		lenient().when(client.getPlayers()).thenReturn(mockPlayers);
	}

	@After
	public void after() {
		scope.exit();
	}

	@Test
	public void testDialogWidget_Spoken_FormatTags() {
		// closing format tags
		// GitHub Issue: https://github.com/phyce/rl-natural-speech/issues/6

		// NPC EntityID(id=11470) dialog detected
		// headModelWidget:11560
		// textWidget:Kovac give commission already. Go make me <col=ef1020>Narrow<br><col=ef1020>Spiked</col> sword.

		final String text =
			"Kovac give commission already. Go make me <col=ef1020>Narrow<br><col=ef1020>Spiked</col> sword.";
		final String correctText = "Kovac give commission already. Go make me Narrow Spiked sword.";
		final int headModelId = 11560;
		final int npcId = 11470;

		_testNPCDialogWidget_VerifySpoken(text, headModelId, npcId, correctText);
	}


	@Test
	public void testDialogWidget_Spoken_LongDialogWithLineBreaks() {
		// really long dialog with <br> breaks
		// GitHub Issue: https://github.com/phyce/rl-natural-speech/issues/6

		// NPC EntityID(id=3225) dialog detected
		// headModelWidget:3225
		// textWidget:As you get better you'll find you will be able to smith<br>Mithril and eventually Adamantite and even Runite.<br>This can be very lucrative but very expensive on the<br>coal front.

		final String text =
			"As you get better you'll find you will be able to smith<br>Mithril and eventually Adamantite and even Runite.<br>This can be very lucrative but very expensive on the<br>coal front.";
		final String correctText =
			"As you get better you'll find you will be able to smith Mithril and eventually Adamantite and even Runite. This can be very lucrative but very expensive on the coal front.";
		final int headModelId = 3225;
		final int npcId = 3225;

		_testNPCDialogWidget_VerifySpoken(text, headModelId, npcId, correctText);
	}

	@Test
	public void testDialogWidget_Spoken_UnclosedFormatTags() {
		// unclosed format tags
		// GitHub Issue: https://github.com/phyce/rl-natural-speech/issues/6

		// NPC EntityID(id=4572) dialog detected
		// headModelWidget:4572
		// textWidget:<col=0000ff>~The cat glowers back at you.

		final String text = "<col=0000ff>~The cat glowers back at you.";
		final String correctText = "~The cat glowers back at you.";
		final int headModelId = 4572;
		final int npcId = 4572;

		_testNPCDialogWidget_VerifySpoken(text, headModelId, npcId, correctText);
	}

	@Test
	public void testDialogWidget_NeverSpoken_SpeechManagerNotStarted() {
		lenient().when(speechManager.isStarted()).thenReturn(false); // engine not running

		WidgetLoaded event = new WidgetLoaded();
		event.setGroupId(InterfaceID.DIALOG_NPC);

		speechEventHandler.onWidgetLoaded(event);

		// assert never tried to speak
		verify(speechManager, never()).speak(any(), any(), any(), any());
	}

	@Test
	public void testDialogWidget_NeverSpoken_MutedNPC() {
		lenient().when(muteManager.isAllowed(any())).thenReturn(false); // muted

		mock_NPCDialogWidget("never spoken", -1, -2);

		WidgetLoaded event = new WidgetLoaded();
		event.setGroupId(InterfaceID.DIALOG_NPC);

		speechEventHandler.onWidgetLoaded(event);

		// assert never tried to speak
		verify(speechManager, never()).speak(any(), any(), any(), any());
	}

	@Test
	public void testDialogWidget_NeverSpoken_DialogDisabledInConfig() {
		lenient().when(config.dialogEnabled()).thenReturn(false); // disabled

		WidgetLoaded event = new WidgetLoaded();
		event.setGroupId(InterfaceID.DIALOG_NPC);

		speechEventHandler.onWidgetLoaded(event);

		// assert never tried to speak
		verify(speechManager, never()).speak(any(), any(), any(), any());
	}


	@Test
	public void testChatMessage_Spoken_Plain() {
		//  ChatMessage(
		//		messageNode=cn@192d2bbf,
		//		type=PUBLICCHAT,
		//		name=<img=41>Dawncore,
		//		message=Hello natural speech hello natural speech hello natural speech hello natural,
		//		sender=null,
		//		timestamp=1716428452
		//  )
		String userName = "<img=41>Dawncore";
		String text = "Hello natural speech hello natural speech hello natural speech hello natural";
		ChatMessage chatMessage
			= new ChatMessage(null, ChatMessageType.PUBLICCHAT, userName, text, null, 0);

		EntityID eid = EntityID.name(userName);
		VoiceID voiceID = new VoiceID("mock", "player voice");
		Supplier<Float> gainSupplier = () -> 0f;

		lenient().when(voiceManager.resolve(eid)).thenReturn(voiceID);
		lenient().when(volumeManager.chat(ChatHelper.ChatType.OtherPlayers, eid)).thenReturn(gainSupplier);

		speechEventHandler.onChatMessage(chatMessage);

		verify(speechManager).speak(eq(voiceID), eq(text), eq(gainSupplier), any());
	}

	@Test
	public void testChatMessage_Spoken_DontSanitizeTags() {
		//  ChatMessage(
		//  	messageNode=cn@293ce2b,
		//  	type=PUBLICCHAT,
		//  	name=<img=41>Dawncore,
		//  	message=<lt>col=ffffff<gt>fake tags within player chat,
		//  	sender=null,
		//  	timestamp=1716429172
		//  )

		String userName = "<img=41>Dawncore";

		// since it's public chat, tags should not be sanitized, since it's manually typed.
		// For spam protection, could consider removing all unspeakable symbols for public chat in the future.
		// TODO(Louis): Special HTML Characters in Public Chat GitHub Issue: https://github.com/phyce/rl-natural-speech/issues/12
		// However, for text replacement, user expects the character unspeakable '<', which the client replaces with "<lt>"
		// with `Text.escapeJagex(String)`
		// to be replaced back into spoken phrase.
		String text = "<lt>col=ffffff<gt>fake tags within player chat";

		ChatMessage chatMessage
			= new ChatMessage(null, ChatMessageType.PUBLICCHAT, userName, text, null, 0);

		EntityID eid = EntityID.name(userName);
		VoiceID voiceID = new VoiceID("mock", "player voice");
		Supplier<Float> gainSupplier = () -> 0f;

		lenient().when(voiceManager.resolve(eid)).thenReturn(voiceID);
		lenient().when(volumeManager.chat(ChatHelper.ChatType.OtherPlayers, eid)).thenReturn(gainSupplier);

		speechEventHandler.onChatMessage(chatMessage);

		verify(speechManager).speak(eq(voiceID), eq(text), eq(gainSupplier), any());
	}

	@Test
	public void testChatMessage_CustomReplace_SpecialCharacters() {
		String userName = "<img=41>Dawncore";
		String text =
			"don't replace me @ " +
				"testing replace_me <lt>3 ()[]{}<lt><gt> dräpare multiple words 1 multiple words 2 =_= *blush* delete me" +
				" @ dont_replace_me";
		String correctText =
			"don't replace me @ " +
				"OK1 OK2 OK3 OK4 OK5 OK6 MULTIPLE OK7 OK8 OK9 " +
				" @ dont_replace_me";

		String customTextReplacements = "testing=OK1\n" +
			"replace_me=OK2\n" +
			"<3=OK3\n" +
			"()[]{}<>=OK4\n" +
			"dräpare=OK5\n" +
			"multiple words 1=OK6\n" +
			"multiple words 2=MULTIPLE OK7\n" +
			"=_==OK8\n" +
			"*blush*=OK9\n" +
			"=MISSING_PATTERN\n" +
			"=\n" + // just empty pattern both sides
			"delete me=\n";

		_testCustomReplacements(customTextReplacements, userName, text, correctText);
	}

	@Test
	public void testChatMessage_CustomReplace_EndOfLine() {
		String userName = "<img=41>Dawncore";
		String text = "FAKE_OK0 OK0";
		String correctText = "FAKE_OK0 WEGOOD";
		String customTextReplacements = "OK0=OK1\n" +
			"OK1=OK2\n" +
			"OK2=OK3\n" +
			"OK3=WEGOOD";

		_testCustomReplacements(customTextReplacements, userName, text, correctText);
	}

	@Test
	public void testChatMessage_CustomReplace_StartOfLine() {
		String userName = "<img=41>Dawncore";
		String text = "FAKE_OK0 OK0";
		String correctText = "FAKE_OK0 WEGOOD";
		String customTextReplacements = "OK0=OK1\n" +
			"OK1=OK2\n" +
			"OK2=OK3\n" +
			"OK3=WEGOOD";

		_testCustomReplacements(customTextReplacements, userName, text, correctText);
	}

	@Test
	public void testChatMessage_CustomReplace_Fakes1() {
		String userName = "<img=41>Dawncore";
		String text = "FAKE_OK0 FAKE_OK0";
		String correctText = "FAKE_OK0 FAKE_OK0";
		String customTextReplacements = "OK0=OK1\n" +
			"OK1=OK2\n" +
			"OK2=OK3\n" +
			"OK3=WEGOOD";

		_testCustomReplacements(customTextReplacements, userName, text, correctText);
	}

	@Test
	public void testChatMessage_CustomReplace_Fakes2() {
		String userName = "<img=41>Dawncore";
		String text = "OK0_FAKE OK0_FAKE";
		String correctText = "OK0_FAKE OK0_FAKE";
		String customTextReplacements = "OK0=OK1\n" +
			"OK1=OK2\n" +
			"OK2=OK3\n" +
			"OK3=WEGOOD";

		_testCustomReplacements(customTextReplacements, userName, text, correctText);
	}

	@Test
	public void testChatMessage_CustomReplace_Chain() {

		String userName = "<img=41>Dawncore";
		String text = "OK0 OK0 FAKE_OK0";
		String correctText = "WEGOOD WEGOOD FAKE_OK0";
		String customTextReplacements = "OK0=OK1\n" +
			"OK1=OK2\n" +
			"OK2=OK3\n" +
			"OK3=WEGOOD";

		_testCustomReplacements(customTextReplacements, userName, text, correctText);
	}

	@Test
	public void testChatMessage_BuiltInReplace_ConfigDisabled() {

	}

	@Test
	public void testChatMessage_NeverSpoken_AllSymbols() {
		//  ChatMessage(
		// 		messageNode=cn@2f00f8bf,
		// 		type=PUBLICCHAT,
		// 		name=<img=41>Dawncore,
		// 		message=@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@,
		// 		sender=null,
		// 		timestamp=1716428992
		//  )
		String userName = "<img=41>Dawncore";
		String text = "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@";
		ChatMessage chatMessage
			= new ChatMessage(null, ChatMessageType.PUBLICCHAT, userName, text, null, 0);

		EntityID eid = EntityID.name(userName);
		VoiceID voiceID = new VoiceID("mock", "player voice");
		Supplier<Float> gainSupplier = () -> 0f;

		lenient().when(voiceManager.resolve(eid)).thenReturn(voiceID);
		lenient().when(volumeManager.chat(ChatHelper.ChatType.OtherPlayers, eid)).thenReturn(gainSupplier);

		speechEventHandler.onChatMessage(chatMessage);

		verify(speechManager, never()).speak(eq(voiceID), eq(text), eq(gainSupplier), any());
	}


	private void _testNPCDialogWidget_VerifySpoken(String text, int headModelId, int npcId, String correctText) {
		when(config.enableDialogTextReplacements()).thenReturn(false);

		mock_NPCDialogWidget(text, headModelId, npcId);

		VoiceID mockVoice = new VoiceID("mock", "voice");
		// assert found the correct npcId using headModelId
		when(voiceManager.resolve(EntityID.id(npcId))).thenReturn(mockVoice);

		Supplier<Float> mockGainSupplier = () -> 0f;
		when(volumeManager.dialog()).thenReturn(mockGainSupplier);

		WidgetLoaded event = mock(WidgetLoaded.class);
		when(event.getGroupId()).thenReturn(InterfaceID.DIALOG_NPC);
		speechEventHandler.onWidgetLoaded(event);

		///////
		String lineName = Names.DIALOG;
		verify(speechManager).silence(assertArg(condition -> {
			assert condition.test(lineName); // correctly silenced line
		}));

		verify(speechManager).speak(
			mockVoice, // found correct voice
			correctText, // correct sanitization
			mockGainSupplier, // found correct gain supplier
			lineName // correct line
		);
	}

	private void mock_NPCDialogWidget(String text, int headModelId, int npcId) {
		Widget textWidget = mock(Widget.class);
		when(textWidget.getText()).thenReturn(text);
		when(client.getWidget(ComponentID.DIALOG_NPC_TEXT)).thenReturn(textWidget);

		Widget headModelWidget = mock(Widget.class);
		when(headModelWidget.getModelId()).thenReturn(headModelId);
		when(client.getWidget(ComponentID.DIALOG_NPC_HEAD_MODEL)).thenReturn(headModelWidget);

		when(clientHelper.widgetModelIdToNpcId(headModelId)).thenReturn(npcId);
	}

	private void _testBuiltInReplacements(
		ReplacementsJSON[] builtInTextReplacements,
		String userName,
		String text,
		String correctText
	) {
		// disable built-in
		when(config.useBuiltInReplacements()).thenReturn(true);
		when(config.customTextReplacements()).thenReturn("");
		chatHelper.loadCustomReplacements();
		chatHelper.loadBuiltInReplacement(builtInTextReplacements);

		ChatMessage chatMessage
			= new ChatMessage(null, ChatMessageType.PUBLICCHAT, userName, text, null, 0);

		EntityID eid = EntityID.name(userName);
		VoiceID voiceID = new VoiceID("mock", "player voice");
		Supplier<Float> gainSupplier = () -> 0f;

		lenient().when(voiceManager.resolve(eid)).thenReturn(voiceID);
		lenient().when(volumeManager.chat(ChatHelper.ChatType.OtherPlayers, eid)).thenReturn(gainSupplier);

		speechEventHandler.onChatMessage(chatMessage);

		verify(speechManager).speak(
			eq(voiceID),
			//			eq(correctText),
			ArgumentMatchers.assertArg((Consumer<String>) actual -> {
				if (!actual.equals(correctText)) {
					log.error("Built-in Replacement Mismatch!\nExpect:\t{}\nActual:\t{}", correctText, actual);
					throw new AssertionError();
				}
			}),
			eq(gainSupplier),
			any()
		);

		clearInvocations(speechManager);

		// disable built-in
		when(config.useBuiltInReplacements()).thenReturn(false);
		speechEventHandler.onChatMessage(chatMessage);

		verify(speechManager).speak(
			eq(voiceID),
			//			eq(correctText),
			ArgumentMatchers.assertArg((Consumer<String>) actual -> {
				if (!actual.equals(text)) {
					log.error("Replacement even when built-in disabled!\nExpect:\t{}\nActual:\t{}", text, actual);
					throw new AssertionError();
				}
			}),
			eq(gainSupplier),
			any()
		);

	}

	private void _testCustomReplacements(
		String customTextReplacements,
		String userName,
		String text,
		String correctText
	) {
		// disable built-in
		when(config.useBuiltInReplacements()).thenReturn(false);
		when(config.customTextReplacements()).thenReturn(customTextReplacements);
		chatHelper.loadCustomReplacements();

		ChatMessage chatMessage
			= new ChatMessage(null, ChatMessageType.PUBLICCHAT, userName, text, null, 0);

		EntityID eid = EntityID.name(userName);
		VoiceID voiceID = new VoiceID("mock", "player voice");
		Supplier<Float> gainSupplier = () -> 0f;

		lenient().when(voiceManager.resolve(eid)).thenReturn(voiceID);
		lenient().when(volumeManager.chat(ChatHelper.ChatType.OtherPlayers, eid)).thenReturn(gainSupplier);

		speechEventHandler.onChatMessage(chatMessage);

		verify(speechManager).speak(
			eq(voiceID),
			ArgumentMatchers.assertArg((Consumer<String>) actual -> {
				if (!actual.equals(correctText)) {
					log.error("Custom Replacement Mismatch!\nExpect:\t{}\nActual:\t{}", correctText, actual);
					throw new AssertionError();
				}
			}),
			eq(gainSupplier),
			any()
		);

		clearInvocations(speechManager);

		// reload empty customs
		when(config.customTextReplacements()).thenReturn("");
		chatHelper.loadCustomReplacements();
		speechEventHandler.onChatMessage(chatMessage);

		verify(speechManager).speak(
			eq(voiceID),
			ArgumentMatchers.assertArg((Consumer<String>) actual -> {
				if (!actual.equals(text)) {
					log.error("Replacement occured after loading empty custom replacements!\nExpect:\t{}\nActual:\t{}",
						text, actual);
					throw new AssertionError();
				}
			}),
			eq(gainSupplier),
			any()
		);

	}
}
