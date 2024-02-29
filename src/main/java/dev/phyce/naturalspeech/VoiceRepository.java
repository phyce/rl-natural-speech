package dev.phyce.naturalspeech;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.downloader.DownloadTask;
import dev.phyce.naturalspeech.downloader.Downloader;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;

import java.io.*;
import java.nio.file.Path;
import java.util.HashSet;

@Slf4j
public class VoiceRepository {

    @Inject
    private Downloader downloader;

    @Inject
    private NaturalSpeechConfig config;

    private final HashSet<PiperVoiceURL> piperVoiceURLS;

    @Value
    public static class PiperVoiceURL {
        String name;
        String onnx_url;
        String onnx_metadata_url;
        String speakers_metadata_url;
    }

    @Data
    @AllArgsConstructor
    public static class PiperVoice {
        String name;
        File onnx;
        File onnx_metadata;
        Speaker[] speakers;
    }

    @Value
    public static class Speaker {
        int speaker_id;
        int piper_id;
        String gender;
        String name;
    }

    public VoiceRepository() throws IOException {
        try {
            InputStream is = this.getClass().getResource(Settings.voiceRepositoryFilename).openStream();
            // read dictionary index as name
            piperVoiceURLS = new Gson().fromJson(new InputStreamReader(is), new TypeToken<HashSet<PiperVoiceURL>>(){}.getType());
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

    public PiperVoice loadPiperVoice(String voice_name) throws IOException {

        // assume true
        boolean localVoiceValid = true;

        // First check if voice folder exist
        Path voiceFolder = Path.of(config.ttsEngine()).resolveSibling(Settings.voiceFolderName).resolve(voice_name);
        if (voiceFolder.toFile().exists())
        {
            // Check voice is missing any files
            if (!voiceFolder.resolve(voice_name + ".onnx").toFile().exists()) {
                localVoiceValid = false;
            }
            // Check if onnx metadata exists
            if (!voiceFolder.resolve(voice_name + ".onnx.json").toFile().exists()) {
                localVoiceValid = false;
            }
            // Check if speakers metadata exists
            if (!voiceFolder.resolve(voice_name + ".speakers.json").toFile().exists()) {
                localVoiceValid = false;
            }
            // TODO Check hash for files, right piper-voices doesn't offer hashes for download. Have to offer our own.
        } else { // voices folder don't exist, so no voices can exist
            localVoiceValid = false;
            // create the folder
            if (!voiceFolder.toFile().mkdirs()) {
                // if we fail to create the folder, just toss an error
                throw new IOException("Failed to create voice folder.");
            }
        }

        // if local voice files weren't valid, clear the folder and re-download.
        if (!localVoiceValid) {
            log.info("Local voice files are invalid, re-downloading.");
            PiperVoiceURL piperVoiceURL = findPiperVoiceURL(voice_name);
            if (piperVoiceURL == null) {
                log.error("Voice not found in repository: " + voice_name);
                return null;
            }

            // download voice files
            DownloadTask onnxTask = downloader.create(HttpUrl.get(piperVoiceURL.onnx_url), voiceFolder.resolve(voice_name + ".onnx"));
            DownloadTask onnxMetadataTask = downloader.create(HttpUrl.get(piperVoiceURL.onnx_metadata_url), voiceFolder.resolve(voice_name + ".onnx.json"));
            DownloadTask speakersTask = downloader.create(HttpUrl.get(piperVoiceURL.speakers_metadata_url), voiceFolder.resolve(voice_name + ".speakers.json"));

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
        try (FileInputStream fis = new FileInputStream(voiceFolder.resolve(voice_name + ".speakers.json").toFile())) {
            Speaker[] speakers = new Gson().fromJson(new InputStreamReader(fis), new TypeToken<Speaker[]>(){}.getType());
            PiperVoice piperVoice = new PiperVoice(
                    voice_name,
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
