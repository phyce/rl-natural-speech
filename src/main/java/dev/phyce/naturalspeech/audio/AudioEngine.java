package dev.phyce.naturalspeech.audio;

import dev.phyce.naturalspeech.singleton.PluginSingleton;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.CheckForNull;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginSingleton
public class AudioEngine {

	private final ConcurrentHashMap<String, DynamicLine> lines = new ConcurrentHashMap<>();
	private final Mixer mixer;
	private float masterGain = 0;


	public AudioEngine() {
		mixer = AudioSystem.getMixer(null); // null gets the default system mixer
	}

	public void update() {
		lines.forEachValue(Long.MAX_VALUE, DynamicLine::update);
	}

	public void play(
		@NonNull String lineName,
		@NonNull AudioInputStream audioStream,
		@NonNull Supplier<Float> gainSupplier
	) {
		DynamicLine line = getOrNewLine(lineName, audioStream.getFormat());
		if (line == null) {
			return;
		}

		byte[] bytes;
		try (audioStream) {
			bytes = audioStream.readAllBytes();
		} catch (IOException e) {
			log.error("Failed to read audio stream", e);
			return;
		}

		line.setGainSupplier(gainSupplier);

		log.trace("Buffering bytes into line {}", lineName);
		line.buffer(bytes);
	}

	@CheckForNull
	private DynamicLine getOrNewLine(@NonNull String lineName, AudioFormat format) {
		DynamicLine line = lines.get(lineName);

		// no existing line, new line
		if (line == null) {
			line = newLine(lineName, format);
			log.trace("New line {} for {}", line, lineName);
		}
		else {
			log.trace("Existing line {} for {}", line, lineName);
		}

		return line;
	}

	public void closeName(@NonNull String lineName) {
		DynamicLine line = lines.remove(lineName);
		if (line != null) {
			line.close();
		}
		else {
			log.trace("Attempted to close non-existent line {}", lineName);
		}
	}

	public void closeConditional(@NonNull Predicate<String> condition) {
		lines.forEach((lineName, line) -> {
			if (condition.test(lineName)) {
				closeName(lineName);
			}
		});
	}

	public void closeAll() {
		lines.forEachValue(Long.MAX_VALUE, DynamicLine::close);
		lines.clear();
	}

	public boolean pauseLine(@NonNull String lineName) {
		DynamicLine line = lines.get(lineName);
		if (line != null) {
			line.stop();
			return true;
		}
		return false;
	}

	public void pauseAll() {
		lines.forEach((name, line) -> line.stop());
	}

	public void setMasterGain(float gainDb) {
		masterGain = gainDb;
		for (DynamicLine line : lines.values()) {
			line.setMasterGain(masterGain);
		}
		update();
	}

	private DynamicLine newLine(@NonNull String lineName, AudioFormat format) {
		DynamicLine line;
		try {
			line = new DynamicLine((SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, format)));
			line.setMasterGain(masterGain);
			line.open(); // takes 2 millisecond on my machine
			line.start();

			// garbage collect line after buffering
			line.addDynamicLineListener((event) -> {
				if (event == DynamicLine.DynamicLineEvent.DONE_BUFFERING) {
					log.trace("Line done buffering, removing line {}", lineName);
					closeName(lineName);
				}
			});

		} catch (LineUnavailableException e) {
			log.error("Failed to create line {} of format {}", lineName, format, e);
			return null;
		}
		lines.put(lineName, line);
		return line;
	}


}
