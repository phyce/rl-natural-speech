package dev.phyce.naturalspeech;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.downloader.DownloadTask;
import dev.phyce.naturalspeech.downloader.Downloader;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;

import java.io.*;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;

@Slf4j
public class ModelRepository {

	private final Downloader downloader;

	private final NaturalSpeechConfig config;

	@Getter
	private final HashSet<ModelURL> modelURLS;

	// Partially Serialized JSON Object
	@Data
	public static class ModelURL {
		// Part of JSON
		String fullName;
		String shortName;
		String onnxURL;
		String onnxMetadataURL;
		String metadataURL;

		// Not part of JSON
		// Whether local model files are available for immediate use
		boolean hasLocal;
	}

	// Not an JSON Object
	@Data
	@AllArgsConstructor
	public static class ModelLocal {
		String fullName;
		String shortName;
		File onnx;
		File onnx_metadata;
		Voice[] voices;
	}

	// Partially Serialized JSON Object
	@Data
	public static class Voice {
		// (Serialized in JSON) The Model ID from the data set
		int voiceID;
		// (Serialized in JSON) M, F, ...
		String gender;
		// (Serialzied in JSON) The speaker name from the model data set
		String name;

		// Model Short name
		String modelShortName;
		// Model Full name
		String modelFullName;
	}

	@Inject
	public ModelRepository(
			Downloader downloader,
			NaturalSpeechConfig config) throws IOException {
		this.downloader = downloader;
		this.config = config;

		try {
			InputStream is = Objects.requireNonNull(this.getClass().getResource(Settings.voiceRepositoryFilename)).openStream();
			// read dictionary index as name
			modelURLS = new Gson().fromJson(new InputStreamReader(is), new TypeToken<HashSet<ModelURL>>() {
			}.getType());

			// check for local files availability
			for (ModelURL modelURL : modelURLS) {
				modelURL.setHasLocal(hasLocalFiles(modelURL.fullName));
			}

			log.info(modelURLS.toString());

			log.info("Loaded voice repository with " + modelURLS.size() + " voices");

			// log voices
			for (ModelURL modelURL : modelURLS) {
				log.info("Voice: " + modelURL);
			}

		} catch (IOException e) {
			log.error("Could not read voice repository file: " + Settings.voiceRepositoryFilename);
			throw e;
		}
	}

	public ModelURL findPiperVoiceURL(String voice_name) {
		for (ModelURL modelURL : modelURLS) {
			if (modelURL.fullName.equals(voice_name)) {
				return modelURL;
			}
		}
		return null;
	}

	public boolean hasLocalFiles(String voice_name) throws IOException {
		// assume true
		boolean localVoiceValid = true;

		// First check if voice folder exist
		Path voiceFolder = Path.of(config.ttsEngine()).resolveSibling(Settings.voiceFolderName).resolve(voice_name);
		if (voiceFolder.toFile().exists()) {
			// Check voice is missing any files
			if (!voiceFolder.resolve(voice_name + ".onnx").toFile().exists()) {
				localVoiceValid = false;
			}
			// Check if onnx metadata exists
			if (!voiceFolder.resolve(voice_name + ".onnx.json").toFile().exists()) {
				localVoiceValid = false;
			}
			// Check if speakers metadata exists
			if (!voiceFolder.resolve(voice_name + ".metadata.json").toFile().exists()) {
				localVoiceValid = false;
			}
			// TODO(Louis) Check hash for files, right piper-voices doesn't offer hashes for download. Have to offer our own.
		} else { // voices folder don't exist, so no voices can exist
			localVoiceValid = false;
		}

		// if local voice files weren't valid, clear the folder and re-download.
		return localVoiceValid;
	}

	public ModelLocal downloadPiperVoice(String voice_name) throws IOException {

		// assume true
		boolean localVoiceValid = true;

		// First check if voice folder exist
		Path voiceFolder = Path.of(config.ttsEngine()).resolveSibling(Settings.voiceFolderName).resolve(voice_name);
		if (voiceFolder.toFile().exists()) {
			// Check voice is missing any files
			if (!voiceFolder.resolve(voice_name + ".onnx").toFile().exists()) {
				localVoiceValid = false;
			}
			// Check if onnx metadata exists
			if (!voiceFolder.resolve(voice_name + ".onnx.json").toFile().exists()) {
				localVoiceValid = false;
			}
			// Check if speakers metadata exists
			if (!voiceFolder.resolve(voice_name + ".metadata.json").toFile().exists()) {
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

		ModelURL modelURL = findPiperVoiceURL(voice_name);
		if (modelURL == null) {
			log.error("Voice not found in repository: " + voice_name);
			return null;
		}

		// if local voice files weren't valid, clear the folder and re-download.
		if (!localVoiceValid) {
			log.info("Local voice files are invalid, re-downloading.");

			// download voice files
			DownloadTask onnxTask = downloader.create(HttpUrl.get(modelURL.onnxURL), voiceFolder.resolve(voice_name + ".onnx"));
			DownloadTask onnxMetadataTask = downloader.create(HttpUrl.get(modelURL.onnxMetadataURL), voiceFolder.resolve(voice_name + ".onnx.json"));
			DownloadTask speakersTask = downloader.create(HttpUrl.get(modelURL.metadataURL), voiceFolder.resolve(voice_name + ".metadata.json"));

			// thread blocking download
			File onnx = onnxTask.get();
			File onnxMetadata = onnxMetadataTask.get();
			File speakers = speakersTask.get();

			if (!onnx.exists() || !onnxMetadata.exists() || !speakers.exists()) {
				// if any of the files doesn't exist after validation, throw
				throw new IOException("Voice files downloaded are missing.");
			}
		}

		// Read Speaker File into an HashSet of Array of Speaker
		try (FileInputStream fis = new FileInputStream(voiceFolder.resolve(voice_name + ".metadata.json").toFile())) {
			Voice[] voices = new Gson().fromJson(new InputStreamReader(fis), new TypeToken<Voice[]>() {
			}.getType());
			for (Voice voice : voices) {
				voice.setModelShortName(voice_name);
			}

			ModelLocal modelLocal = new ModelLocal(
					voice_name,
					modelURL.getShortName(),
					voiceFolder.resolve(voice_name + ".onnx").toFile(),
					voiceFolder.resolve(voice_name + ".onnx.json").toFile(),
					voices
			);
			return modelLocal;

		} catch (IOException e) {
			log.error("Failed to read speakers file, even after validation: " + e.getMessage());
			throw e;
		}
	}
}
