package dev.phyce.naturalspeech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.downloader.DownloadTask;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;

import java.io.*;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;

import static dev.phyce.naturalspeech.NaturalSpeechPlugin.MODEL_FOLDER_NAME;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.MODEL_REPO_FILENAME;

@Slf4j
public class ModelRepository {

	public final static String EXTENSION = ".onnx";
	public final static String MODEL_METADATA_EXTENSION = ".onnx.json";
	public final static String METADATA_EXTENSION = ".metadata.json";

	private final Downloader downloader;

	private final NaturalSpeechRuntimeConfig runtimeConfig;

	@Getter
	private final HashSet<ModelURL> modelURLS;

	private final Gson gson;

	@Inject
	public ModelRepository(Downloader downloader, NaturalSpeechRuntimeConfig runtimeConfig) throws IOException {

		this.gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

		this.downloader = downloader;
		this.runtimeConfig = runtimeConfig;

		try {
			InputStream is = Objects.requireNonNull(this.getClass().getResource(MODEL_REPO_FILENAME)).openStream();
			// read dictionary index as name
			modelURLS = gson.fromJson(new InputStreamReader(is), new TypeToken<HashSet<ModelURL>>() {
			}.getType());

			// check for local files availability
			for (ModelURL modelURL : modelURLS) {
				modelURL.setLocalFileAvailable(hasModelLocal(modelURL.modelName));
			}

			log.info(modelURLS.toString());

			log.info("Loaded voice repository with " + modelURLS.size() + " voices");

			// log voices
			for (ModelURL modelURL : modelURLS) {
				log.info("Voice: " + modelURL);
			}

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
		} else { // voices folder don't exist, so no voices can exist
			localVoiceValid = false;
		}

		// if local voice files weren't valid, clear the folder and re-download.
		return localVoiceValid;
	}

	public ModelLocal getModelLocal(VoiceID voiceID) throws IOException {
		return getModelLocal(voiceID.modelName);
	}

	public ModelLocal getModelLocal(String modelName) throws IOException {
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
		} else { // voices folder don't exist, so no voices can exist
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
			log.info("Local voice files are invalid, re-downloading.");

			// download voice files
			DownloadTask onnxTask = downloader.create(HttpUrl.get(modelURL.onnxURL), voiceFolder.resolve(modelName + EXTENSION));
			DownloadTask onnxMetadataTask = downloader.create(HttpUrl.get(modelURL.onnxMetadataURL), voiceFolder.resolve(modelName + MODEL_METADATA_EXTENSION));
			DownloadTask speakersTask = downloader.create(HttpUrl.get(modelURL.metadataURL), voiceFolder.resolve(modelName + METADATA_EXTENSION));
			log.info("modelURL.metadataURL");
			log.info(modelURL.metadataURL);

			// thread blocking download
			File onnx = onnxTask.get();
			File onnxMetadata = onnxMetadataTask.get();
			log.info("22222222222222222222222222222");
			File speakers = speakersTask.get();
			log.info("33333333333333333333333333333");

			if (!onnx.exists() || !onnxMetadata.exists() || !speakers.exists()) {
				// if any of the files doesn't exist after validation, throw
				throw new IOException("Voice files downloaded are missing.");
			}
		}

		// Read Speaker File into an HashSet of Array of Speaker
		try (FileInputStream fis = new FileInputStream(voiceFolder.resolve(modelName + METADATA_EXTENSION).toFile())) {
			VoiceMetadata[] voiceMetadatas = gson.fromJson(new InputStreamReader(fis), new TypeToken<VoiceMetadata[]>() {
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

		// Not part of JSON
		// Whether local model files are available for immediate use
		boolean localFileAvailable;
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
	}
}
