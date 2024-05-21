package dev.phyce.naturalspeech.spamdetection;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpamDetection {

	private final ChatFilterPluglet chatFilterPluglet;
	private final SpamFilterPluglet spamFilterPluglet;

	@Inject
	private SpamDetection(
		ChatFilterPluglet chatFilterPluglet, SpamFilterPluglet spamFilterPluglet
	) {
		this.chatFilterPluglet = chatFilterPluglet;
		this.spamFilterPluglet = spamFilterPluglet;

	}

	public boolean isSpam(String username, String text) {
		return spamFilterPluglet.isSpam(text) || chatFilterPluglet.isSpam(username, text);
	}


}
