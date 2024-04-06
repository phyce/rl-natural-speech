package dev.phyce.naturalspeech.tts;

import com.google.common.base.Supplier;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;

@Slf4j
@PluginSingleton
public class VolumeManager {

	public final static java.util.function.Supplier<Float> ZERO_GAIN = () -> 0f;
	private final NaturalSpeechConfig config;

	private static final float CHAT_DAMPEN = 10f;
	private static final float FRIEND_BOOST = 10f;

	@Inject
	public VolumeManager(
		NaturalSpeechConfig config
	) {
		this.config = config;
	}

	public Supplier<Float> attenuated(Actor actor) {
		return () -> {
			return 0f;
		};
	}

}
