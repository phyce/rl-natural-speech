package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.spamdetection.ChatFilterPluglet;
import dev.phyce.naturalspeech.spamdetection.SpamFilterPluglet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpamDetection {

	private final ChatFilterPluglet chatFilterPluglet;
	private final SpamFilterPluglet spamFilterPluglet;

	@Inject
	private SpamDetection(ChatFilterPluglet chatFilterPluglet, SpamFilterPluglet spamFilterPluglet
	) {
		this.chatFilterPluglet = chatFilterPluglet;
		this.spamFilterPluglet = spamFilterPluglet;

	}

	public boolean isSpam(String username, String text) {
		return spamFilterPluglet.isSpam(text) || chatFilterPluglet.isSpam(username, text);
	}


}
