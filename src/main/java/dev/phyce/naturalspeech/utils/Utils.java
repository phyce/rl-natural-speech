package dev.phyce.naturalspeech.utils;

import java.util.Arrays;
import java.util.List;

public class Utils {
	public static <T> boolean inArray(T needle, T[] haystack) {
		List<T> list = Arrays.asList(haystack);
		return list.contains(needle);
	}
}
