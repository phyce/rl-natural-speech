package dev.phyce.naturalspeech;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.MODEL_FOLDER_NAME;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.MODEL_REPO_FILENAME;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.downloader.DownloadTask;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;

@Slf4j
public class ModelRepository {

	public final static String EXTENSION = ".onnx";
	public final static String MODEL_METADATA_EXTENSION = ".onnx.json";
	public final static String METADATA_EXTENSION = ".metadata.json";

	private final Downloader downloader;

	private final NaturalSpeechRuntimeConfig runtimeConfig;

	@Getter
	private final List<ModelURL> modelURLS;

	@Getter
	private final ScheduledExecutorService executor;

	private final List<ModelRepositoryListener> changeListeners = new ArrayList<>();

	private final Gson gson;

	@Inject
	public ModelRepository(Downloader downloader, NaturalSpeechRuntimeConfig runtimeConfig,
						   ScheduledExecutorService executor, Gson gson) throws IOException {
		this.executor = executor;
		this.gson = gson;

		this.downloader = downloader;
		this.runtimeConfig = runtimeConfig;

		try {
			InputStream is = Objects.requireNonNull(this.getClass().getResource(MODEL_REPO_FILENAME)).openStream();
			// read dictionary index as name
			modelURLS = gson.fromJson(new InputStreamReader(is), new TypeToken<List<ModelURL>>() {
			}.getType());

			log.info("Loaded voice repository with " + modelURLS.size() + " voices");
			modelURLS.forEach(modelURL -> log.info("Found: " + modelURL));

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

	public boolean hasModelLocal(VoiceID voiceID) throws IOException {
		return hasModelLocal(voiceID.modelName);
	}

	public boolean hasModelLocal(ModelURL modelURL) throws IOException {
		return hasModelLocal(modelURL.getModelName());
	}

	public boolean hasModelLocal(String modelName) throws IOException {
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
			// TODO(Louis) Check hash for files, right piper-voices doesn't offer hashes for download. Have to offer our own.
		}
		else { // voices folder don't exist, so no voices can exist
			localVoiceValid = false;
		}

		// if local voice files weren't valid, clear the folder and re-download.
		return localVoiceValid;
	}

	public ModelLocal loadModelLocal(VoiceID voiceID) throws IOException {
		return loadModelLocal(voiceID.modelName);
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
			triggerOnRepositoryChanged(modelLocal.getModelName());
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
			triggerOnRepositoryChanged(modelName);
		}

		// Read Speaker File into an HashSet of Array of Speaker
		try (FileInputStream fis = new FileInputStream(voiceFolder.resolve(modelName + METADATA_EXTENSION).toFile())) {
			VoiceMetadata[] voiceMetadatas =
				gson.fromJson(new InputStreamReader(fis), new TypeToken<VoiceMetadata[]>() {
				}.getType());

			for (VoiceMetadata voiceMetadata : voiceMetadatas) {
				voiceMetadata.setModelName(modelURL.getModelName());
			}

			return new ModelLocal(
				modelURL.getModelName(),
				voiceFolder.resolve(modelName + EXTENSION).toFile(),
				voiceFolder.resolve(modelName + MODEL_METADATA_EXTENSION).toFile(),
				voiceMetadatas
			);

		} catch (IOException e) {
			log.error("Failed to read speakers file, even after validation: " + e.getMessage());
			throw e;
		}
	}

	public void addRepositoryChangedListener(ModelRepositoryListener listener) {
		changeListeners.add(listener);
	}

	public void removeRepositoryChangedListener(ModelRepositoryListener listener) {
		changeListeners.remove(listener);
	}

	private void triggerOnRepositoryChanged(String modelName) {
		for (ModelRepositoryListener changeListener : changeListeners) {
			changeListener.onRepositoryChanged(modelName);
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
	public static class VoiceMetadata {
		// (Serialized in JSON) The speaker name from the model data set
		@Expose
		String name;
		// (Serialized in JSON) M, F, ...
		@Expose
		String gender;
		// (Serialized in JSON) The Model ID from the data set
		@Expose
		int piperVoiceID;

		// Model Full name, not serialized, manually set when loading metadata
		String modelName;

		public VoiceID toVoiceID() {
			return new VoiceID(modelName, piperVoiceID);
		}
	}

	// Not a Serialized JSON Object
	@Data
	@AllArgsConstructor
	public static class ModelLocal {
		String modelName;
		File onnx;
		File onnxMetadata;
		VoiceMetadata[] voiceMetadata;

		private static Map<Integer, List<Integer>> genderCategorizedVoices;

		private void categorizeVoicesByGender() {
			genderCategorizedVoices = new HashMap<>();
			for (VoiceMetadata voice : voiceMetadata) {
				int genderKey = voice.gender.equals("M")? 0: 1;
				genderCategorizedVoices.putIfAbsent(genderKey, new ArrayList<>());
				genderCategorizedVoices.get(genderKey).add(voice.piperVoiceID);
			}
		}

		public VoiceID calculateVoice(String username) {
			int hashCode = username.hashCode();
			return new VoiceID(modelName, Math.abs(hashCode) % voiceMetadata.length);
		}

		public VoiceID calculateGenderedVoice(String username, int gender) {
			if (genderCategorizedVoices == null) categorizeVoicesByGender();

			List<Integer> voiceIDs = genderCategorizedVoices.get(gender);
			if (voiceIDs == null || voiceIDs.isEmpty()) {
				throw new IllegalArgumentException("No voices available for the specified gender");
			}
			int hashCode = username.hashCode();
			int voiceIDIndex = Math.abs(hashCode) % voiceIDs.size();
			int selectedVoiceID = voiceIDs.get(voiceIDIndex);

			return new VoiceID(modelName, selectedVoiceID);
		}
	}

	public interface ModelRepositoryListener {
		default void onRepositoryChanged(String modelName) {}
	}
}
