package dev.phyce.naturalspeech.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Texts {

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
		return segments.stream().map(s -> "[" + s + "]").reduce("", (a, b) -> a + b);
	}

	public static String renderReplacements(String text, Map<String, String> replacements) {
		// instead of tokenizing, we do a find-and-replace
		// this supports space separated targets to be replaced, for example "multiple words"="OK"

		// special characteristic: find target requires to either be start of line or preceded by ' ' space
		// example: "replace me"="OK"

		// replace me -> OK
		// ^ start of line

		// dont_replace me -> dont_replace me
		//     ^ not space, not start of line

		// filler_word replace me -> filler_word OK
		//            ^ space

		// "replace me" -> "replace me"
		// ^ not space, not start of line

		for (Map.Entry<String, String> entry : replacements.entrySet()) {

			StringBuilder result = new StringBuilder();
			String original = entry.getKey();
			String replaced = entry.getValue();

			int start = 0;
			int end = text.indexOf(entry.getKey());

			while (end != -1) {

				result.append(text, start, end);
				result.append(start > 0 && text.charAt(start) == ' ' ? original : replaced);

				start = end + original.length();
				end = text.indexOf(original, start);
			}

			if (start < text.length()) {
				result.append(text, start, text.length());
			}

//			log.info("\nREPLACE\t{}\nTEXT\t{}\nRESULT\t{}\n", entry, text, result);
			text = result.toString();
		}

		return text.trim();
	}

	public static String renderLargeNumbers(String text) {
		text = text.replaceAll("(?i)(\\d{1,3})(,)(\\d{3})", "$1$3");
		text = text.replaceAll("(?i)(\\d)(,)(\\d{3})(\\.\\d+)?", "$1$3$4");

		text = text.replaceAll("(?i)(\\d+)\\s?k\\b", "$1 thousand");
		text = text.replaceAll("(?i)(\\d+)\\s?m\\b", "$1 million");
		text = text.replaceAll("(?i)(\\d+)\\s?b\\b", "$1 billion");
		text = text.replaceAll("(?i)(\\d+)\\s?t\\b", "$1 trillion");
		return text;
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
		if (voiceId == -1) {return String.format("{\"text\":\"%s\"}", text);}
		else {return String.format("{\"text\":\"%s\", \"speaker_id\":%d}", text, voiceId);}
	}

	private static final Pattern patternTargetWithLevel = Pattern.compile("(.+)  \\(level-\\d+\\)");

	/*
	 * For MenuEntry menuTarget name.
	 * Keeps tag information, removes level information.
	 * example: Guard (level-32) -> Guard
	 * @return
	 */
	public static String removeLevelFromTargetName(String menuTarget) {
		Matcher matcher = patternTargetWithLevel.matcher(menuTarget);
		if (matcher.matches()) menuTarget = matcher.group(1);
		return menuTarget;
	}


}
