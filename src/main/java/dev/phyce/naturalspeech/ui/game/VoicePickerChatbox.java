package dev.phyce.naturalspeech.ui.game;

import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.phyce.naturalspeech.enums.ElevenLabsModel;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import dev.phyce.naturalspeech.tts.ModelRepository;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.tts.VoiceSource;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoice;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoiceRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextMenuInput;

@Slf4j
public class VoicePickerChatbox {
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
	private String value = "";

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

	public VoicePickerChatbox value(@NonNull String value) {
		this.value = value;
		return this;
	}

	public void build() {
		VoiceConfigChatboxTextInput input = textInputProvider.get();
		input.onChooseVoice(() -> clientThread.invokeLater(() -> chooseVoice(input)))
			.onSubmit(text -> submit(input, text))
			.prompt("Voice for " + standardActorName)
			.value(value)
			.build();
	}

	private boolean submit(VoiceConfigChatboxTextInput input, String text) {
		if (text.isBlank()) {
			clientThread.invokeLater(() -> {
				if (hasAssignedVoice()) confirmClear();
			});
			return true;
		}

		VoiceID voiceID = VoiceID.fromIDString(text.trim());
		if (voiceID == null) {
			input.error("Not a valid voice ID, example: libritts:120");
			return false;
		}

		clientThread.invokeLater(() -> applyVoice(voiceID));
		return true;
	}

	private boolean hasAssignedVoice() {
		if (npc != null) return voiceManager.containsNPC(npc.getId(), standardActorName);
		return voiceManager.containsUsername(standardActorName);
	}

	private void confirmClear() {
		chatboxPanelManager.openTextMenuInput("Clear the voice for " + standardActorName + "?")
			.option("Yes", () -> clientThread.invokeLater(this::clearVoice))
			.option("No", () -> clientThread.invokeLater(this::build))
			.build();
	}

	private void chooseVoice(VoiceConfigChatboxTextInput input) {
		if (availableEngines().isEmpty()) {
			input.error("No voices available, start an engine first");
			return;
		}
		openEngineMenu();
	}

	private List<SpeechEngine> availableEngines() {
		return Arrays.stream(SpeechEngine.values())
			.filter(engine -> !activeModels(engine).isEmpty())
			.collect(Collectors.toList());
	}

	private void openEngineMenu() {
		List<SpeechEngine> engines = availableEngines();
		if (engines.isEmpty()) {
			build();
			return;
		}
		if (engines.size() == 1) {
			openModelMenu(engines.get(0), this::build);
			return;
		}

		ChatboxTextMenuInput menu = chatboxPanelManager.openTextMenuInput("Choose an engine");
		for (SpeechEngine engine : engines) {
			menu.option(engine.toString(),
				() -> clientThread.invokeLater(() -> openModelMenu(engine, this::openEngineMenu)));
		}
		menu.option("Back", () -> clientThread.invokeLater(this::build));
		menu.build();
	}

	private void openModelMenu(SpeechEngine engine, Runnable back) {
		List<VoiceSource> models = activeModels(engine);
		if (models.isEmpty()) {
			back.run();
			return;
		}
		if (models.size() == 1) {
			openVoiceSearch(models.get(0), back);
			return;
		}

		ChatboxTextMenuInput menu = chatboxPanelManager.openTextMenuInput("Choose a " + engine + " model");
		for (VoiceSource model : models) {
			menu.option(model.getModelLabel(),
				() -> clientThread.invokeLater(() -> openVoiceSearch(model, () -> openModelMenu(engine, back))));
		}
		menu.option("Back", () -> clientThread.invokeLater(back));
		menu.build();
	}

	private List<VoiceSource> activeModels(SpeechEngine engine) {
		List<VoiceSource> models = new ArrayList<>();
		if (engine == SpeechEngine.ELEVENLABS) {
			if (elevenLabsVoiceRepository.getVoices().isEmpty()) return models;

			for (ElevenLabsModel model : elevenLabsVoiceRepository.getEnabledModels()) {
				models.add(VoiceSource.ofModel(model.getId()));
			}
		}
		else {
			for (ModelRepository.ModelURL modelURL : modelRepository.getModelURLS()) {
				models.add(VoiceSource.ofModel(modelURL.getModelName()));
			}
		}
		models.removeIf(model -> !textToSpeech.isModelActive(model.getModelName()));
		return models;
	}

	private void openVoiceSearch(VoiceSource model, Runnable back) {
		List<VoiceSearchChatbox.Choice> voices = voicesFor(model.getModelName());
		if (voices.isEmpty()) {
			feedback(model + " has no voices available");
			back.run();
			return;
		}

		voiceSearchProvider.get()
			.voices(voices)
			.title(model.getModelLabel() + " voice for " + standardActorName)
			.onSelected(voiceID -> clientThread.invokeLater(() -> applyVoice(voiceID)))
			.onBack(() -> clientThread.invokeLater(back))
			.build();
	}

	private List<VoiceSearchChatbox.Choice> voicesFor(String modelName) {
		List<VoiceSearchChatbox.Choice> choices = new ArrayList<>();

		ElevenLabsModel elevenLabsModel = ElevenLabsModel.fromId(modelName);
		if (elevenLabsModel != null) {
			for (ElevenLabsVoice voice : elevenLabsVoiceRepository.getVoices()) {
				choices.add(VoiceSearchChatbox.Choice.of(label(voice.getName(), voice.getGender()),
					voice.toVoiceID(elevenLabsModel)));
			}
		}
		else {
			try {
				for (ModelRepository.VoiceMetadata voice : modelRepository.loadModelLocal(modelName).getVoiceMetadata()) {
					choices.add(VoiceSearchChatbox.Choice.of(label(voice.getName(), voice.getGender()), voice.toVoiceID()));
				}
			} catch (IOException e) {
				log.error("Failed to load the voices for {}", modelName, e);
			}
		}

		choices.sort(Comparator.comparing(choice -> choice.getLabel().toLowerCase()));
		return choices;
	}

	private static String label(String name, Gender gender) {
		if (gender == Gender.MALE) return name + " (M)";
		if (gender == Gender.FEMALE) return name + " (F)";
		return name;
	}

	private void applyVoice(VoiceID voiceID) {
		voiceManager.applyConfiguredVoice(npc, standardActorName, voiceID);
		feedback(standardActorName + " set to " + voiceID);
	}

	private void clearVoice() {
		voiceManager.clearConfiguredVoice(npc, standardActorName);
		feedback("Cleared the voice for " + standardActorName);
	}

	private void feedback(String message) {
		client.addChatMessage(ChatMessageType.CONSOLE, "", "Natural Speech: " + message, null);
	}
}
