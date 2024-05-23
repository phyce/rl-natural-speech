package dev.phyce.naturalspeech.clienteventhandlers;

import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.audio.VolumeManager;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.singleton.PluginSingletonScope;
import dev.phyce.naturalspeech.spamdetection.SpamDetection;
import dev.phyce.naturalspeech.statics.Names;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.utils.ClientHelper;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SpeechEventHandlerTest {

	private final PluginSingletonScope scope = new PluginSingletonScope();

	@Inject
	SpeechEventHandler speechEventHandler;

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
		BoundFieldModule boundFieldModule = BoundFieldModule.of(this);
		Injector injector = Guice.createInjector(boundFieldModule).createChildInjector(
			new AbstractModule() {
				@Override
				protected void configure() {
					bindScope(PluginSingleton.class, scope);
				}
			}
		);

		scope.enter();

		try {
			injector.injectMembers(this);
		} catch (ConfigurationException e) {
			throw new RuntimeException(e.getErrorMessages().toString());
		}

		// mock clientThread invoke
		doAnswer(a -> {
			final Runnable r = a.getArgument(0);
			r.run();
			return null;
		}).when(clientThread).invokeAtTickEnd(any(Runnable.class));


		// All green light to speak by default
		lenient().when(muteManager.isAllowed(any())).thenReturn(true);
		lenient().when(config.dialogEnabled()).thenReturn(true);
		lenient().when(speechManager.isStarted()).thenReturn(true);
	}

	@After
	public void after() {
		scope.exit();
	}

	@Test
	public void testDialogWidget_CorrectlySpoken_FormatTags() {
		// closing format tags

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
	public void testDialogWidget_CorrectlySpoken_LongDialogWithLineBreaks() {
		// really long dialog with <br> breaks

		// NPC EntityID(id=3225) dialog detected
		// headModelWidget:3225
		// textWidget:As you get better you'll find you will be able to smith<br>Mithril and eventually Adamantite and even Runite.<br>This can be very lucrative but very expensive on the<br>coal front.

		final String text =
			"As you get better you'll find you will be able to smith<br>Mithril and eventually Adamantite and even Runite.<br>This can be very lucrative but very expensive on the<br>coal front.";
		final String correctText = "As you get better you'll find you will be able to smith Mithril and eventually Adamantite and even Runite. This can be very lucrative but very expensive on the coal front.";
		final int headModelId = 3225;
		final int npcId = 3225;

		_testNPCDialogWidget_VerifySpoken(text, headModelId, npcId, correctText);
	}

	@Test
	public void testDialogWidget_CorrectlySpoken_UnclosedFormatTags() {
		// unclosed format tags

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
	public void testChatMessage0() {
		//  ChatMessage(
		//		messageNode=cn@192d2bbf,
		//		type=PUBLICCHAT,
		//		name=<img=41>Dawncore,
		//		message=Hello natural speech hello natural speech hello natural speech hello natural spe,
		//		sender=null,
		//		timestamp=1716428452
		//  )
	}

	@Test
	public void testChatMessage1() {
		//  ChatMessage(
		// 		messageNode=cn@2f00f8bf,
		// 		type=PUBLICCHAT,
		// 		name=<img=41>Dawncore,
		// 		message=@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@,
		// 		sender=null,
		// 		timestamp=1716428992
		//  )
	}

	@Test
	public void testChatMessage2() {
		//  ChatMessage(
		//  	messageNode=cn@114168b6,
		//  	type=PUBLICCHAT,
		//  	name=<img=41>Dawncore,
		//  	message=***** crazy way to talk,
		//  	sender=null,
		//  	timestamp=1716429084
		//  )
	}

	@Test
	public void testChatMessage3() {
		//  ChatMessage(
		//  	messageNode=cn@293ce2b,
		//  	type=PUBLICCHAT,
		//  	name=<img=41>Dawncore,
		//  	message=<lt>col=ffffff<gt>fake tags within player chat,
		//  	sender=null,
		//  	timestamp=1716429172
		//  )
	}

	@Test
	public void testChatMessage4() {
		//  ChatMessage(
		//  	messageNode=cn@e6a6d36,
		//  	type=PUBLICCHAT,
		//  	name=<img=41>Dawncore,
		//  	message=@@@words in sea of @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@,
		//  	sender=null,
		//  	timestamp=1716429268
		//  )
	}

	private void _testNPCDialogWidget_VerifySpoken(String text, int headModelId, int npcId, String correctText) {
		when(config.useNpcCustomAbbreviations()).thenReturn(false);

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
}
