package dev.phyce.naturalspeech.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {

	public static List<String> splitSentence(String sentence) {
		final int softLimit = 40;
		final int hardLimit = 80;
		List<String> fragments = new ArrayList<>();
		StringBuilder currentFragment = new StringBuilder();

		List<String> tokens = tokenize(sentence);

		for (String token : tokens) {
			if (currentFragment.length() + token.length() <= hardLimit) {
				currentFragment.append(token);

				if (token.matches(".*[.!?]$")) {
					fragments.add(currentFragment.toString().trim());
					currentFragment.setLength(0);
					continue;
				}

				if (token.matches(".*[,-;/]$") && currentFragment.length() > softLimit) {
					fragments.add(currentFragment.toString().trim());
					currentFragment.setLength(0);
					continue;
				}
			}
			else {
				int lastBreakPoint = findLastBreakPoint(currentFragment.toString(), softLimit, hardLimit);
				if (lastBreakPoint > 0) {
					fragments.add(currentFragment.substring(0, lastBreakPoint).trim());
					currentFragment = new StringBuilder(currentFragment.substring(lastBreakPoint).trim());
				}
				else {
					fragments.add(currentFragment.toString().trim());
					currentFragment.setLength(0);
				}
				currentFragment.append(token);
			}

			if (!token.matches("\\p{Punct}")) currentFragment.append(" ");
		}

		if (currentFragment.length() > 0) fragments.add(currentFragment.toString().trim());

		return fragments;
	}

	private static int findLastBreakPoint(String fragment, int softLimit, int hardLimit) {
		int lastSpace = -1;

		for (int i = 0; i < fragment.length(); i++) {
			if (fragment.charAt(i) == ' ' || fragment.charAt(i) == ',') {lastSpace = i + 1;}
			else if (fragment.charAt(i) == '.' || fragment.charAt(i) == '!' || fragment.charAt(i) == '?') return i + 1;
		}
		return lastSpace;
	}

	public static String expandShortenedPhrases(String text, Map<String, String> phrases) {
		List<String> tokens = tokenize(text);
		StringBuilder parsedMessage = new StringBuilder();

		for (String token : tokens) {
			String key = token.replaceAll("\\p{Punct}", "").toLowerCase();

			String replacement = phrases.getOrDefault(key, token);
			parsedMessage.append(replacement.equals(token)? token: replacement).append(" ");
		}

		return parsedMessage.toString().trim();
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
	/**
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

	public static String removeTags(String input) {
		return input.replaceAll("<[^>]+>", "");
	}
}
