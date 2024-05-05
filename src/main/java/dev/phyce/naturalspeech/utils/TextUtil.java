package dev.phyce.naturalspeech.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TextUtil {

	public static List<String> splitSentence(String text) {
		// https://www.baeldung.com/java-split-string-keep-delimiters
		// This regex splits: "Hello, NaturalSpeech?" Into ["Hello,", "NaturalSpeech?"]
		// By using a positive-lookbehind delimiter matcher
		String[] segments = text.split("((?<=[.,!?:;]))");
		return Arrays.stream(segments)
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());
	}

	public static String sentenceSegmentPrettyPrint(List<String> segments) {
		return segments.stream().map(s -> "[" + s + "]").reduce("", (a, b) ->  a + b);
	}

	public static String expandAbbreviations(String text, Map<String, String> phrases) {
		text = preprocessNumbers(text);
		List<String> tokens = tokenize(text);
		StringBuilder parsedMessage = new StringBuilder();

		for (String token : tokens) {
			String key = token.replaceAll("\\p{Punct}", "").toLowerCase();

			String replacement = phrases.getOrDefault(key, token);
			parsedMessage.append(replacement.equals(token) ? token : replacement).append(" ");
		}

		return parsedMessage.toString().trim();
	}

	private static String preprocessNumbers(String text) {
		text = text.replaceAll("(?i)(\\d{1,3})(,)(\\d{3})", "$1$3");
		text = text.replaceAll("(?i)(\\d)(,)(\\d{3})(\\.\\d+)?", "$1$3$4");

		text = text.replaceAll("(?i)(\\d+)\\s?k\\b", "$1 thousand");
		text = text.replaceAll("(?i)(\\d+)\\s?m\\b", "$1 million");
		text = text.replaceAll("(?i)(\\d+)\\s?b\\b", "$1 billion");
		text = text.replaceAll("(?i)(\\d+)\\s?t\\b", "$1 trillion");
		return text;
	}

	public static List<String> tokenize(String text) {
		List<String> tokens = new ArrayList<>();

		Matcher matcher = Pattern.compile("[\\w']+(?:[.,;!?]+|\\.\\.\\.)?|\\p{Punct}").matcher(text);
		while (matcher.find()) {tokens.add(matcher.group());}

		return tokens;
	}

	public static final Pattern patternAnyAlphaNumericChar = Pattern.compile(".*\\w.*");
	public static boolean containAlphaNumeric(String text) {
		return patternAnyAlphaNumericChar.matcher(text).matches();
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
		if(voiceId == -1) return String.format("{\"text\":\"%s\"}", text);
		else return String.format("{\"text\":\"%s\", \"speaker_id\":%d}", text, voiceId);
	}

	private static final Pattern patternTargetWithLevel = Pattern.compile("(.+)  \\(level-\\d+\\)");
	/*
	 * For MenuEntry menuTarget name.
	 * Keeps tag information, removes level information.
	 * For example <col=ffffff>Guard</col>  (level-32) -> Guard
	 * @return
	 */
	public static String removeLevelFromTargetName(String menuTarget) {
		Matcher matcher = patternTargetWithLevel.matcher(menuTarget);
		if (matcher.matches()) menuTarget = matcher.group(1);
		return menuTarget;
	}

}
