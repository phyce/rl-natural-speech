package dev.phyce.naturalspeech.utils;

import java.util.Objects;
import javax.annotation.CheckForNull;
import net.runelite.api.Actor;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Standardize this, standardize that, standardize everything! All in one package!
 */
public final class Standardize {
	@CheckForNull
	public static String getStandardName(@Nullable String name) {
		if (name == null) {
			return null;
		}
		return Text.standardize(Text.removeTags(name));
	}

	@CheckForNull
	public static String getStandardName(@Nullable Actor actor) {
		if (actor == null) {
			return null;
		}
		return getStandardName(actor.getName());
	}

	@NonNull
	public static String getStandardName(@NonNull ChatMessage message) {
		return Objects.requireNonNull(getStandardName(message.getName()));
	 }

	public static boolean equals(@Nullable Actor a, @Nullable Actor b) {
		if (a == null && b == null) {
			return true;
		} else if (a == null || b == null) {
			return false;
		} else {
			return equals(a.getName(), b.getName());
		}
	}

	public static boolean equals(@Nullable String name, @Nullable Actor actor) {
		if (actor == null && name == null) {
			return true;
		} else if (actor == null || name == null) {
			return false;
		} else {
			return equals(actor.getName(), name);
		}
	}

	public static boolean equals(@Nullable Actor actor, @Nullable String name) {
		return equals(name, actor);
	}

	public static boolean equals(@Nullable String nameA, @Nullable String nameB) {
		// we cannot standardize null strings
		if (nameA == null && nameB == null) {
			return true;
		} else if (nameA == null || nameB == null) {
			return false;
		} else {
			return Text.standardize(nameA).equals(Text.standardize(nameB));
		}
	}
}
