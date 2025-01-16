package dev.phyce.naturalspeech.audio;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.PluginModule;
import dev.phyce.naturalspeech.eventbus.PluginEventBus;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.utils.Result;
import static dev.phyce.naturalspeech.utils.Result.Error;
import static dev.phyce.naturalspeech.utils.Result.Ok;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

@Slf4j
@PluginSingleton
public class AudioEngine implements PluginModule {
	private final PluginExecutorService pluginExecutorService;
	private final NaturalSpeechConfig config;
	private final ConfigManager configManager;

	private final ConcurrentHashMap<String, DynamicLine> lines = new ConcurrentHashMap<>();
	private final Mixer mixer;
	private final AtomicBoolean masterMute = new AtomicBoolean(false);
	private float masterGain;


	@Value(staticConstructor="of")
	public static class AudioEngineChanged {
		public enum Events {
			MASTER_GAIN, MASTER_MUTE
		}

		Events event;
	}


	@Inject
	public AudioEngine(
			PluginExecutorService pluginExecutorService,
			NaturalSpeechConfig config,
			ConfigManager configManager,
			PluginEventBus pluginEventBus
	) {
		this.configManager = configManager;
		this.pluginExecutorService = pluginExecutorService;
		this.config = config;
		this.mixer = AudioSystem.getMixer(null); // null gets the default system mixer
	}

	@Override
	public void startUp() {
		pluginExecutorService
				.scheduleAtFixedRate(this::update, 0, 600 / 8, TimeUnit.MILLISECONDS);

		setMasterVolume(config.masterVolume());
		setMute(config.masterMute());
	}

	@Override
	public void shutDown() {
	}

	public void update() {
		lines.forEachValue(Long.MAX_VALUE, DynamicLine::update);
	}

	public boolean isMuted() {
		return masterMute.get();
	}

	public void setMute(boolean mute) {
		masterMute.set(mute);
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event) {
		switch (event.getKey()) {
			case ConfigKeys.MASTER_VOLUME:
				break;
		}
	}

	public void setMasterVolume(int volume100) {
		setMasterGain(volumeToGain(volume100));
	}

	public void setMasterGain(float gainDb) {
		this.masterGain = gainDb;
		lines.values().forEach(line -> line.setMasterGain(masterGain));
		update();
	}


	public void play(
			@NonNull String lineName,
			@NonNull AudioInputStream audioStream,
			@NonNull Supplier<Float> gainSupplier
	) {
		Result<DynamicLine, LineUnavailableException> result = getOrNewLine(lineName, audioStream.getFormat());

		result.ifError((error) -> log.error("Failed to get or create line {}", lineName, error));

		result.ifOk((line) -> {
			byte[] bytes;
			try (audioStream) {
				bytes = audioStream.readAllBytes();
			} catch (IOException e) {
				log.error("Failed to read audio stream", e);
				return;
			}

			line.setGainSupplier(augmentMute(gainSupplier));
			log.trace("Buffering bytes into line {}", lineName);
			line.buffer(bytes);
		});
	}

	@NonNull
	private Result<DynamicLine, LineUnavailableException> getOrNewLine(@NonNull String lineName, AudioFormat format) {
		DynamicLine line = lines.get(lineName);

		// no existing line, new line
		if (line == null) {
			log.trace("New line for {}", lineName);
			return newLine(lineName, format);
		}

		if (!line.getFormat().matches(format)) {
			log.trace("Existing line has different format for {}, making new line.", lineName);
			return newLine(lineName, format);
		}

		log.trace("Existing line {} for {}", line, lineName);
		return Ok(line);

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


	@NonNull
	private Result<DynamicLine, LineUnavailableException> newLine(@NonNull String lineName, AudioFormat format) {
		DynamicLine line;
		try {
			line = new DynamicLine((SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, format)));
			line.setMasterGain(masterGain);
			line.open();
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
			return Error(e);
		}
		lines.put(lineName, line);
		return Ok(line);
	}

	@NonNull
	private Supplier<Float> augmentMute(Supplier<Float> gainSupplier) {
		return () -> {
			if (masterMute.get()) {
				return -80f;
			}
			return gainSupplier.get();
		};
	}

	/**
	 * lossy conversion from volume[0,100] -> Decibels
	 */
	public static float volumeToGain(int volume100) {
		// range[NOISE_FLOOR, 0]
		float gainDB;

		// Graph of the function
		// https://www.desmos.com/calculator/wdhsfbxgeo

		// clamp to 0-100
		float volume = Math.min(100, volume100);
		// convert linear volume 0-100 to log control
		if (volume <= 0.1) {
			gainDB = VolumeManager.NOISE_FLOOR;
		}
		else {
			gainDB = (float) (10 * (Math.log(volume / 100)));
		}

		return gainDB;
	}
}
