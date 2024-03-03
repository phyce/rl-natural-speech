package dev.phyce.naturalspeech;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Strings {
	public static String parseMessage(String message, Map<String, String> phrases) {
		List<String> tokens = tokenize(message);
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

	public static List<String> tokenize(String message) {
		List<String> tokens = new ArrayList<>();
		Matcher matcher = Pattern.compile("[\\w']+|\\p{Punct}").matcher(message);

		while (matcher.find()) tokens.add(matcher.group());

		return tokens;
	}

	public static String escape(String message) {
		return message.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\b", "\\b")
				.replace("\f", "\\f")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	public static String generateJson(String message, int voiceId) {
		message = escape(message);
		return String.format("{\"text\":\"%s\", \"speaker_id\":%d}", message, voiceId);
	}
}
