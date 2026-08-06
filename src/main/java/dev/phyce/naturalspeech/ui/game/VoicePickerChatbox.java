package dev.phyce.naturalspeech.ui.game;

import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import dev.phyce.naturalspeech.exceptions.VoiceSelectionOutOfOption;
import dev.phyce.naturalspeech.tts.ModelRepository;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoice;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoiceRepository;
import dev.phyce.naturalspeech.tts.nativespeech.NativeSpeechEngine;
import dev.phyce.naturalspeech.tts.nativespeech.NativeVoice;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextMenuInput;

/**
 * In-game voice picker for the right-click Configure option: pick an engine, then search that
 * engine's voices by name. No ids to memorise.
 * <p>
 * The engine step is a {@link ChatboxTextMenuInput}, which suits a handful of options. The voice step
 * is a {@link VoiceSearchChatbox} instead, because voice packs run to hundreds of entries and a menu
 * only binds keys 1-9.
 */
@Slf4j
public class VoicePickerChatbox {

	private static final SpeechEngine[] ENGINES =
		{SpeechEngine.PIPER, SpeechEngine.SYSTEM, SpeechEngine.ELEVENLABS};

	/** Reading a piper pack's metadata hits disk, and the menu asks for every engine on each open. */
	private final Map<SpeechEngine, List<VoiceSearchChatbox.Choice>> voiceCache =
		new EnumMap<>(SpeechEngine.class);

	private final ChatboxPanelManager chatboxPanelManager;
	private final ClientThread clientThread;
	private final Client client;
	private final VoiceManager voiceManager;
	private final TextToSpeech textToSpeech;
	private final ModelRepository modelRepository;
	private final ElevenLabsVoiceRepository elevenLabsVoiceRepository;
	private final Provider<VoiceConfigChatboxTextInput> textInputProvider;
	private final Provider<VoiceSearchChatbox> voiceSearchProvider;

	private NPC npc;
	private String standardActorName;

	@Inject
	public VoicePickerChatbox(
		ChatboxPanelManager chatboxPanelManager,
		ClientThread clientThread,
		Client client,
		VoiceManager voiceManager,
		TextToSpeech textToSpeech,
		ModelRepository modelRepository,
		ElevenLabsVoiceRepository elevenLabsVoiceRepository,
		Provider<VoiceConfigChatboxTextInput> textInputProvider,
		Provider<VoiceSearchChatbox> voiceSearchProvider) {
		this.chatboxPanelManager = chatboxPanelManager;
		this.clientThread = clientThread;
		this.client = client;
		this.voiceManager = voiceManager;
		this.textToSpeech = textToSpeech;
		this.modelRepository = modelRepository;
		this.elevenLabsVoiceRepository = elevenLabsVoiceRepository;
		this.textInputProvider = textInputProvider;
		this.voiceSearchProvider = voiceSearchProvider;
	}

	public VoicePickerChatbox configNPC(@Nullable NPC npc) {
		this.npc = npc;
		return this;
	}

	public VoicePickerChatbox configUsername(@NonNull String standardActorName) {
		this.standardActorName = standardActorName;
		return this;
	}

	/** The top level menu shown by the Configure option. */
	public void build() {
		chatboxPanelManager.openTextMenuInput("Voice for " + standardActorName)
			.option("Enter a voice id", () -> onClientThread(this::openTextInput))
			.option("Select engine", () -> onClientThread(this::openEngineMenu))
			.option("Clear voice", () -> onClientThread(this::clearVoice))
			.build();
	}

	/** Every engine is always listed, so none of them look missing when nothing is loaded. */
	private void openEngineMenu() {
		ChatboxTextMenuInput menu = chatboxPanelManager.openTextMenuInput("Select engine");

		for (SpeechEngine engine : ENGINES) {
			// Say why an engine is empty instead of hiding it, which reads as the option not existing
			String label = voicesFor(engine).isEmpty() ? engine + " (unavailable)" : engine.toString();

			menu.option(label, () -> onClientThread(() -> openVoiceSearch(engine)));
		}

		menu.option("Back", () -> onClientThread(this::build));
		menu.build();
	}

	private void openVoiceSearch(SpeechEngine engine) {
		List<VoiceSearchChatbox.Choice> voices = voicesFor(engine);

		if (voices.isEmpty()) {
			feedback(unavailableReason(engine));
			openEngineMenu();
			return;
		}

		voiceSearchProvider.get()
			.voices(voices)
			.title(engine + " voice for " + standardActorName)
			.onSelected(voiceID -> onClientThread(() -> applyVoice(voiceID)))
			// Closing without picking steps back to the engine list rather than dumping the player out
			.onBack(() -> onClientThread(this::openEngineMenu))
			.build();
	}

	private static String unavailableReason(SpeechEngine engine) {
		switch (engine) {
			case SYSTEM:
				return "System voices are not running. Start them in the Natural Speech panel.";
			case ELEVENLABS:
				return "No ElevenLabs voices loaded. Set an API key, then press Check key in the panel.";
			default:
				return "No piper voice packs are running. Download one and press Start in the panel.";
		}
	}

	private List<VoiceSearchChatbox.Choice> voicesFor(SpeechEngine engine) {
		return voiceCache.computeIfAbsent(engine, this::loadVoicesFor);
	}

	private List<VoiceSearchChatbox.Choice> loadVoicesFor(SpeechEngine engine) {
		List<VoiceSearchChatbox.Choice> choices = new ArrayList<>();

		switch (engine) {
			case SYSTEM:
				for (NativeVoice voice : nativeVoices()) {
					choices.add(VoiceSearchChatbox.Choice.of(
						label(voice.getSystemName(), voice.getGender()), voice.toVoiceID()));
				}
				break;

			case ELEVENLABS:
				for (ElevenLabsVoice voice : elevenLabsVoiceRepository.getVoices()) {
					choices.add(VoiceSearchChatbox.Choice.of(
						label(voice.getName(), voice.getGender()), voice.toVoiceID()));
				}
				break;

			default:
				// Every running voice pack at once, with the pack in the label so typing "libritts"
				// narrows to it. A separate model step would just be one more thing to click through.
				for (String model : activePiperModels()) {
					try {
						ModelRepository.ModelLocal local = modelRepository.loadModelLocal(model);
						for (ModelRepository.VoiceMetadata voice : local.getVoiceMetadata()) {
							choices.add(VoiceSearchChatbox.Choice.of(
								model + " " + label(voice.getName(), voice.getGender()), voice.toVoiceID()));
						}
					}
					catch (IOException e) {
						log.error("Could not read the voices for {}", model, e);
					}
				}
				break;
		}

		choices.sort(Comparator.comparing(choice -> choice.getLabel().toLowerCase()));
		return choices;
	}

	private static String label(String name, Gender gender) {
		if (gender == Gender.MALE) return name + " (M)";
		if (gender == Gender.FEMALE) return name + " (F)";
		return name;
	}

	private List<NativeVoice> nativeVoices() {
		NativeSpeechEngine engine = textToSpeech.getNativeSpeechEngine();
		return engine == null ? new ArrayList<>() : engine.getVoices();
	}

	private List<String> activePiperModels() {
		List<String> models = new ArrayList<>();

		for (ModelRepository.ModelURL modelURL : modelRepository.getModelURLS()) {
			if (textToSpeech.isModelActive(modelURL.getModelName())) {
				models.add(modelURL.getModelName());
			}
		}

		return models;
	}

	private void applyVoice(VoiceID voiceID) {
		voiceManager.applyConfiguredVoice(npc, standardActorName, voiceID);
		feedback(standardActorName + " set to " + voiceID + ", added to Custom Characters.");
	}

	private void clearVoice() {
		voiceManager.clearConfiguredVoice(npc, standardActorName);
		feedback("Cleared the voice for " + standardActorName + ".");
	}

	private void openTextInput() {
		textInputProvider.get()
			.configNPC(npc)
			.configUsername(standardActorName)
			.value(currentVoiceIDString())
			.build();
	}

	private String currentVoiceIDString() {
		try {
			VoiceID voiceID = npc != null
				? voiceManager.getVoiceIDFromNPCId(npc.getId(), npc.getName())
				: voiceManager.getVoiceIDFromUsername(standardActorName);
			return voiceID.toVoiceIDString();
		}
		catch (VoiceSelectionOutOfOption | RuntimeException e) {
			return "";
		}
	}

	/**
	 * Menu callbacks arrive on the client thread when clicked but on the AWT thread when picked with a
	 * number key, and applying a voice reads NPC state that asserts the client thread. Hop always.
	 */
	private void onClientThread(Runnable runnable) {
		clientThread.invokeLater(runnable);
	}

	private void feedback(String message) {
		// CONSOLE rather than GAMEMESSAGE: the plugin mutes console messages for text-to-speech,
		// so this shows up without being read aloud
		client.addChatMessage(ChatMessageType.CONSOLE, "", "Natural Speech: " + message, null);
	}
}
