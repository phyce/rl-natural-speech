package dev.phyce.naturalspeech.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TextUtil {
	private static final Pattern sentenceSplitter = Pattern.compile("(?<=[.!?,])\\s+|(?<=[.!?,])$");
	public static List<String> splitSentence(String sentence) {

		List<String> fragments = Arrays.stream(sentenceSplitter.split(sentence))
				.filter(s -> !s.isBlank()) // remove blanks
				.map(String::trim) // trim spaces
				.collect(Collectors.toList());

		// add period to the last segment
		if (fragments.size() > 1) {
			fragments.set(fragments.size() - 1, fragments.get(fragments.size() - 1) + ".");
		}

		return fragments;
	}
	public static String expandShortenedPhrases(String text, Map<String, String> phrases) {
		List<String> tokens = tokenize(text);
		StringBuilder parsedMessage = new StringBuilder();

		for (String token : tokens) {
			// Remove punctuation from the token for lookup
			String key = token.replaceAll("\\p{Punct}", "").toLowerCase();

			// Replace abbreviation if present
			String replacement = phrases.getOrDefault(key, token);
			parsedMessage.append(replacement.equals(token) ? token : replacement).append(" ");
		}

		return parsedMessage.toString().trim();
	}

	public static List<String> tokenize(String text) {
		List<String> tokens = new ArrayList<>();
		Matcher matcher = Pattern.compile("[\\w']+|\\p{Punct}").matcher(text);

		while (matcher.find()) tokens.add(matcher.group());

		return tokens;
	}

	public static String escape(String text) {
		return text.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\b", "\\b")
				.replace("\f", "\\f")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	public static String generateJson(String text, int voiceId) {
		text = escape(text);
		return String.format("{\"text\":\"%s\", \"speaker_id\":%d}", text, voiceId);
	}
}
