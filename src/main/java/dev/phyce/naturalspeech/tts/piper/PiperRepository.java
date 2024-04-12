package dev.phyce.naturalspeech.tts.piper;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.PluginEventBus;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.downloader.DownloadTask;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.events.piper.PiperRepositoryChanged;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;

@Slf4j
@PluginSingleton
public class PiperRepository {

	public final static String EXTENSION = ".onnx";
	public final static String MODEL_METADATA_EXTENSION = ".onnx.json";
	public final static String METADATA_EXTENSION = ".metadata.json";
	public final static String MODEL_REPO_FILENAME = "model_repository.json";
	public final static String MODEL_FOLDER_NAME = "models";

	private final Downloader downloader;

	private final NaturalSpeechRuntimeConfig runtimeConfig;

	@Getter
	private final List<ModelURL> modelURLS;
	//private final List<ModelRepositoryListener> changeListeners = new ArrayList<>();

	@Getter
	private final ScheduledExecutorService executor;
	private final Gson gson;
	private final PluginEventBus pluginEventBus;

	@Inject
	public PiperRepository(
		Downloader downloader,
		NaturalSpeechRuntimeConfig runtimeConfig,
		ScheduledExecutorService executor,
		Gson gson,
		PluginEventBus pluginEventBus
	) throws IOException {
		this.executor = executor;
		this.gson = gson;

		this.downloader = downloader;
		this.runtimeConfig = runtimeConfig;
		this.pluginEventBus = pluginEventBus;

		pluginEventBus.register(this);

		try {
			InputStream is =
				Objects.requireNonNull(NaturalSpeechPlugin.class.getResource(MODEL_REPO_FILENAME)).openStream();
			// read dictionary index as name
			modelURLS = gson.fromJson(new InputStreamReader(is), new TypeToken<List<ModelURL>>() {
			}.getType());

			modelURLS.stream().map(ModelURL::getModelName).reduce((a, b) -> a + ", " + b)
				.ifPresent(s -> log.info("Loaded voice repository with {} voices. Found: {}", modelURLS.size(), s));

		} catch (IOException e) {
			log.error("Could not read voice repository file: " + MODEL_REPO_FILENAME);
			throw e;
		}
	}

	public ModelURL findModelURLFromModelName(String modelName) {
		for (ModelURL modelURL : modelURLS) {
			if (modelURL.modelName.equals(modelName)) {
				return modelURL;
			}
		}
		return null;
	}

	public boolean hasModelLocal(String modelName) {
		// assume true
		boolean localVoiceValid = true;

		// First check if voice folder exist
		Path voiceFolder = runtimeConfig.getPiperPath().resolveSibling(MODEL_FOLDER_NAME).resolve(modelName);
		if (voiceFolder.toFile().exists()) {
			// Check voice is missing any files
			if (!voiceFolder.resolve(modelName + EXTENSION).toFile().exists()) {
				localVoiceValid = false;
			}
			// Check if onnx metadata exists
			if (!voiceFolder.resolve(modelName + MODEL_METADATA_EXTENSION).toFile().exists()) {
				localVoiceValid = false;
			}
			// Check if speakers metadata exists
			if (!voiceFolder.resolve(modelName + METADATA_EXTENSION).toFile().exists()) {
				localVoiceValid = false;
			}
			// TODO(Louis) Check hash for files, piper-voices doesn't offer hashes for download. Have to offer our own.
		}
		else { // voices folder don't exist, so no voices can exist
			localVoiceValid = false;
		}

		// if local voice files weren't valid, clear the folder and re-download.
		return localVoiceValid;
	}

	public void deleteModelLocal(ModelLocal modelLocal) {
		Path voiceFolder =
			runtimeConfig.getPiperPath().resolveSibling(MODEL_FOLDER_NAME).resolve(modelLocal.getModelName());
		if (voiceFolder.toFile().exists()) {
			// Check voice is missing any files
			File onnxFile = voiceFolder.resolve(modelLocal.getModelName() + EXTENSION).toFile();
			if (!onnxFile.delete()) {
				log.error("Failed to delete onnx file: {}", onnxFile.getPath());
			}
			// Check if onnx metadata exists
			File onnxMeta = voiceFolder.resolve(modelLocal.getModelName() + MODEL_METADATA_EXTENSION).toFile();
			if (!onnxMeta.delete()) {
				log.error("Failed to delete onnx metadata file: {}", onnxMeta.getPath());
			}
			// Check if speakers metadata exists
			File voiceMeta = voiceFolder.resolve(modelLocal.getModelName() + METADATA_EXTENSION).toFile();
			if (!voiceMeta.delete()) {
				log.error("Failed to delete voice speakers file: {}", voiceMeta.getPath());
			}
			if (!voiceFolder.toFile().delete()) {
				log.error("Failed to delete voice folder: {}", voiceFolder);
			}
			pluginEventBus.post(new PiperRepositoryChanged(modelLocal.getModelName()));
		}
		log.info("ModalLocal Deleted {}", modelLocal.getModelName());
	}


	public ModelLocal loadModelLocal(String modelName) throws IOException {
		boolean localVoiceValid = true;

		// First check if voice folder exist
		Path voiceFolder = runtimeConfig.getPiperPath().resolveSibling(MODEL_FOLDER_NAME).resolve(modelName);
		if (voiceFolder.toFile().exists()) {
			// Check voice is missing any files
			if (!voiceFolder.resolve(modelName + EXTENSION).toFile().exists()) {
				localVoiceValid = false;
			}
			// Check if onnx metadata exists
			if (!voiceFolder.resolve(modelName + MODEL_METADATA_EXTENSION).toFile().exists()) {
				localVoiceValid = false;
			}
			// Check if speakers metadata exists
			if (!voiceFolder.resolve(modelName + METADATA_EXTENSION).toFile().exists()) {
				localVoiceValid = false;
			}
			// TODO(Louis) Check hash for files, right piper-voices doesn't offer hashes for download. Have to offer our own.
		}
		else { // voices folder don't exist, so no voices can exist
			localVoiceValid = false;
			// create the folder
			if (!voiceFolder.toFile().mkdirs()) {
				// if we fail to create the folder, just toss an error
				throw new IOException("Failed to create voice folder.");
			}
		}

		ModelURL modelURL = findModelURLFromModelName(modelName);
		if (modelURL == null) {
			log.error("Voice not found in repository: " + modelName);
			return null;
		}

		// if local voice files weren't valid, clear the folder and re-download.
		if (!localVoiceValid) {
			log.info("downloading... {}", modelName);

			// download voice files
			DownloadTask onnxTask =
				downloader.create(HttpUrl.get(modelURL.onnxURL), voiceFolder.resolve(modelName + EXTENSION));
			DownloadTask onnxMetadataTask = downloader.create(HttpUrl.get(modelURL.onnxMetadataURL),
				voiceFolder.resolve(modelName + MODEL_METADATA_EXTENSION));
			DownloadTask speakersTask = downloader.create(HttpUrl.get(modelURL.metadataURL),
				voiceFolder.resolve(modelName + METADATA_EXTENSION));

			// thread blocking download
			File onnx = onnxTask.get();
			File onnxMetadata = onnxMetadataTask.get();
			File speakers = speakersTask.get();

			if (!onnx.exists() || !onnxMetadata.exists() || !speakers.exists()) {
				// if any of the files doesn't exist after validation, throw
				throw new IOException("Voice files downloaded are missing.");
			}

			log.info("done... {}", modelName);
			pluginEventBus.post(new PiperRepositoryChanged(modelName));
		}

		// Read Speaker File into an HashSet of Array of Speaker
		try (FileInputStream fis = new FileInputStream(voiceFolder.resolve(modelName + METADATA_EXTENSION).toFile())) {
			PiperVoiceMetadata[] metadatas =
				gson.fromJson(new InputStreamReader(fis), new TypeToken<PiperVoiceMetadata[]>() {
				}.getType());

			for (PiperVoiceMetadata metadata : metadatas) {
				metadata.setModelName(modelURL.getModelName());
			}

			return new ModelLocal(
				modelURL.getModelName(),
				voiceFolder.resolve(modelName + EXTENSION).toFile(),
				voiceFolder.resolve(modelName + MODEL_METADATA_EXTENSION).toFile(),
				metadatas
			);

		} catch (IOException e) {
			log.error("Failed to read speakers file, even after validation: " + e.getMessage());
			throw e;
		}
	}

	// Partially Serialized JSON Object
	@Data
	public static class ModelURL {
		// Part of JSON
		@Expose
		String modelName;
		@Expose
		String onnxURL;
		@Expose
		String onnxMetadataURL;
		@Expose
		String metadataURL;
		@Expose
		String description;
		@Expose
		String memorySize;
	}


	// Partially Serialized JSON Object
	@Data
	public static class PiperVoiceMetadata {
		// (Serialized in JSON) The speaker name from the model data set
		@Expose
		String name;
		// (Serialized in JSON) M, F, ...
		@JsonAdapter(GenderStringSerializer.class)
		Gender gender;
		// (Serialized in JSON) The Model ID from the data set
		@Expose
		int piperVoiceID;

		// Model Full name, not serialized, manually set when loading metadata
		String modelName;

		public VoiceID toVoiceID() {
			return new VoiceID(modelName, Integer.toString(piperVoiceID));
		}
	}

	private static class GenderStringSerializer implements JsonSerializer<Gender>, JsonDeserializer<Gender> {
		@Override
		public JsonElement serialize(Gender gender, Type type, JsonSerializationContext jsonSerializationContext) {
			if (gender == Gender.MALE) {
				return jsonSerializationContext.serialize("M");
			}
			else if (gender == Gender.FEMALE) {
				return jsonSerializationContext.serialize("F");
			}
			else {
				return jsonSerializationContext.serialize("OTHER");
			}
		}

		@Override
		public Gender deserialize(JsonElement jsonElement, Type type,
								  JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
			if (jsonElement.getAsString().equals("M")) {
				return Gender.MALE;
			}
			else if (jsonElement.getAsString().equals("F")) {
				return Gender.FEMALE;
			}
			else {
				return Gender.OTHER;
			}
		}
	}

	// Not a Serialized JSON Object
	@Data
	@AllArgsConstructor
	public static class ModelLocal {
		String modelName;
		File onnx;
		File onnxMetadata;
		PiperVoiceMetadata[] piperVoiceMetadata;
	}

}
