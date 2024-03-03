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
public class VoiceRepository {

	private final Downloader downloader;

	private final NaturalSpeechConfig config;

	@Getter
	private final HashSet<PiperVoiceURL> piperVoiceURLS;

	@Data
	public static class PiperVoiceURL {
		String name;
		String shortname;
		String onnx_url;
		String onnx_metadata_url;
		String metadata_url;
		boolean hasLocal;
	}

	@Data
	@AllArgsConstructor
	public static class PiperVoice {
		String name;
		String shortname;
		File onnx;
		File onnx_metadata;
		Speaker[] speakers;
	}

	@Data
	public static class Speaker {
		// The Model ID from the data set
		int voiceID;
		// M, F, ...
		String gender;
		// The speaker name from the model data set
		String name;
		// the name of the voice model
		String piperModelName;
	}

	@Inject
	public VoiceRepository(
			Downloader downloader,
			NaturalSpeechConfig config) throws IOException {
		this.downloader = downloader;
		this.config = config;

		try {
			InputStream is = Objects.requireNonNull(this.getClass().getResource(Settings.voiceRepositoryFilename)).openStream();
			// read dictionary index as name
			piperVoiceURLS = new Gson().fromJson(new InputStreamReader(is), new TypeToken<HashSet<PiperVoiceURL>>() {
			}.getType());

			// check for local files availability
			for (PiperVoiceURL piperVoiceURL : piperVoiceURLS) {
				piperVoiceURL.setHasLocal(hasLocalFiles(piperVoiceURL.name));
			}

			log.info(piperVoiceURLS.toString());

			log.info("Loaded voice repository with " + piperVoiceURLS.size() + " voices");

			// log voices
			for (PiperVoiceURL piperVoiceURL : piperVoiceURLS) {
				log.info("Voice: " + piperVoiceURL);
			}

		} catch (IOException e) {
			log.error("Could not read voice repository file: " + Settings.voiceRepositoryFilename);
			throw e;
		}
	}

	public PiperVoiceURL findPiperVoiceURL(String voice_name) {
		for (PiperVoiceURL piperVoiceURL : piperVoiceURLS) {
			if (piperVoiceURL.name.equals(voice_name)) {
				return piperVoiceURL;
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

	public PiperVoice downloadPiperVoice(String voice_name) throws IOException {

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

		PiperVoiceURL piperVoiceURL = findPiperVoiceURL(voice_name);
		if (piperVoiceURL == null) {
			log.error("Voice not found in repository: " + voice_name);
			return null;
		}

		// if local voice files weren't valid, clear the folder and re-download.
		if (!localVoiceValid) {
			log.info("Local voice files are invalid, re-downloading.");

			// download voice files
			DownloadTask onnxTask = downloader.create(HttpUrl.get(piperVoiceURL.onnx_url), voiceFolder.resolve(voice_name + ".onnx"));
			DownloadTask onnxMetadataTask = downloader.create(HttpUrl.get(piperVoiceURL.onnx_metadata_url), voiceFolder.resolve(voice_name + ".onnx.json"));
			DownloadTask speakersTask = downloader.create(HttpUrl.get(piperVoiceURL.metadata_url), voiceFolder.resolve(voice_name + ".metadata.json"));

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
			Speaker[] speakers = new Gson().fromJson(new InputStreamReader(fis), new TypeToken<Speaker[]>() {
			}.getType());
			for (Speaker speaker : speakers) {
				speaker.setPiperModelName(voice_name);
			}

			PiperVoice piperVoice = new PiperVoice(
					voice_name,
					piperVoiceURL.getShortname(),
					voiceFolder.resolve(voice_name + ".onnx").toFile(),
					voiceFolder.resolve(voice_name + ".onnx.json").toFile(),
					speakers
			);
			return piperVoice;

		} catch (IOException e) {
			log.error("Failed to read speakers file, even after validation: " + e.getMessage());
			throw e;
		}
	}
}
