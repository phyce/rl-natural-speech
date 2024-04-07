package dev.phyce.naturalspeech.audio;

import dev.phyce.naturalspeech.guice.PluginSingleton;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.CheckForNull;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginSingleton
public class AudioEngine {

	private final ConcurrentHashMap<String, DynamicLine> lines = new ConcurrentHashMap<>();
	private final Mixer mixer;
	private float masterGain = 0;

	@Getter
	private static final AudioFormat format =
		new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
			22050.0F, // Sample Rate (per second)
			16, // Sample Size (bits)
			1, // Channels
			2, // Frame Size (bytes)
			22050.0F, // Frame Rate (same as sample rate because PCM is 1 sample per 1 frame)
			false
		); // Little Endian

	private static final SourceDataLine.Info sourceDataLineInfo = new DataLine.Info(SourceDataLine.class, format);

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
		DynamicLine line = getOrNewLine(lineName);
		if (line == null) {
			return;
		}

		// value comparison, because AudioFormat didn't implement Object.equals(), this is close enough
		if (!audioStream.getFormat().toString().equals(format.toString())) {
			log.error("Unsupported audio format {}. Current supported format {}", audioStream.getFormat(), format);
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
	private DynamicLine getOrNewLine(String lineName) {
		DynamicLine line = lines.get(lineName);

		// no existing line, new line
		if (line == null) {
			line = newLine(lineName);
			log.trace("New line {} for {}", line, lineName);
		} else {
			log.trace("Existing line {} for {}", line, lineName);
		}

		return line;
	}

	public void closeLineName(String lineName) {
		DynamicLine line = lines.remove(lineName);
		if (line != null) {
			line.close();
		}
		else {
			log.trace("Attempted to close non-existent line {}", lineName);
		}
	}

	public void closeLineConditional(Predicate<String> condition) {
		lines.forEach((lineName, line) -> {
			if (condition.test(lineName)) {
				closeLineName(lineName);
			}
		});
	}

	public boolean pauseLine(String lineName) {
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
	}

	public void closeAll() {
		lines.forEachValue(Long.MAX_VALUE, DynamicLine::close);
		lines.clear();
	}

	private DynamicLine newLine(String lineName) {
		DynamicLine line;
		try {
			line = new DynamicLine((SourceDataLine) mixer.getLine(sourceDataLineInfo));
			line.setMasterGain(masterGain);
			line.open(); // takes 2 millisecond on my machine
			line.start();

			// garbage collect line after buffering
			line.addDynamicLineListener((event) -> {
				if (event == DynamicLine.DynamicLineEvent.DONE_BUFFERING) {
					log.trace("Line done buffering, removing line {}", lineName);
					closeLineName(lineName);
				}
			});

		} catch (LineUnavailableException e) {
			log.error("Failed to create line {}", lineName, e);
			return null;
		}
		lines.put(lineName, line);
		return line;
	}



}
