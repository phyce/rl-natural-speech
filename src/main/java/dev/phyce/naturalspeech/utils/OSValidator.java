package dev.phyce.naturalspeech.utils;

public final class OSValidator {

	private static final String OS = System.getProperty("os.name").toLowerCase();
	public static final boolean IS_WINDOWS = (OS.contains("win"));
	public static final boolean IS_MAC = (OS.contains("mac"));
	public static final boolean IS_UNIX = (OS.contains("nix") || OS.contains("nux") || OS.indexOf("aix") > 0);

}