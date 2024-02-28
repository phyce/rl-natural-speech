package dev.phyce.naturalspeech;

import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashSet;

@Slf4j
public class VoiceRepository {

    @Value
    public static class Voice {
        String name;
        String onnx;
        String onnx_metadata;
        String speakers_metadata;
    }

    public static class Speaker {
        String speaker_id;
        String gender;
        String name;

    }

    public VoiceRepository()
    {
        try {
            InputStream is = this.getClass().getResource(Settings.voiceRepositoryFilename).openStream();
            // read dictionary index as name
            HashSet<Voice> voices = new Gson().fromJson(new InputStreamReader(is), new TypeToken<HashSet<Voice>>(){}.getType());
            log.info("Loaded voice repository with " + voices.size() + " voices");

            // log voices
            for (Voice voice : voices) {
                log.info("Voice: " + voice);
            }

        } catch (IOException e) {
            log.error("Could not read voice repository file: " + Settings.voiceRepositoryFilename);
        }

    }
}
