package dev.phyce.naturalspeech.tts;

import com.google.common.base.Supplier;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginSingleton
public class VolumeManager {

	private final NaturalSpeechConfig config;

	private static final float CHAT_DAMPEN = 10f;
	private static final float FRIEND_BOOST = 10f;

	@Inject
	public VolumeManager(
		NaturalSpeechConfig config
	) {
		this.config = config;
	}

	public Supplier<Float> getUsername(String username) {
		boolean isFriend = PluginHelper.isFriend(username);
		return () -> {
			return 0f;
		};
	}

}
