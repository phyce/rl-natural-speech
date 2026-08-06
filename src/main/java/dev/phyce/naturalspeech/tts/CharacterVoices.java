package dev.phyce.naturalspeech.tts;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.enums.Gender;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.CheckForNull;
import net.runelite.client.config.ConfigManager;

/**
 * The characters listed in the Custom Characters tab, plus the gender configured for each.
 * <p>
 * Only the list and the gender live here. The voice itself goes into {@link VoiceConfig} — the same
 * store the in-game right-click Configure option writes — so the panel and the game menu can never
 * disagree about who sounds like what. Gender needs a home of its own because {@link VoiceConfig}
 * has no slot for it; it narrows automatic voice assignment when no explicit voice is pinned.
 * <p>
 * A character only appears once explicitly added, so this doubles as a way to keep a curated list
 * rather than every name ever configured.
 */
@Singleton
public class CharacterVoices {

	private static final String LIST_KEY = "customCharacters";
	private static final String GENDER_PREFIX = "characterGender_";
	private static final String SEP = "\n";

	private final ConfigManager configManager;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	@Inject
	public CharacterVoices(ConfigManager configManager) {
		this.configManager = configManager;
	}

	/**
	 * Notified when a character is added or removed, so an open panel reflects characters configured
	 * elsewhere — ex the in-game right-click Configure option.
	 * <p>
	 * Deliberately not fired for gender or voice edits: those do not change the list, and rebuilding
	 * rows underneath someone mid-edit would steal their focus.
	 */
	public void addListener(Runnable listener) {
		listeners.add(listener);
	}

	public void removeListener(Runnable listener) {
		listeners.remove(listener);
	}

	/**
	 * Fires the listeners without changing the list. Used when a voice is edited outside the panel,
	 * ex the in-game Configure option: adding an already-listed character is a no-op, so without this
	 * the panel would keep showing the old voice until the tab was reopened.
	 * <p>
	 * The panel's own field edits deliberately do not call this — rebuilding rows mid-keystroke would
	 * take the player's focus away.
	 */
	public void notifyChanged() {
		fireChanged();
	}

	private void fireChanged() {
		for (Runnable listener : listeners) {
			listener.run();
		}
	}

	/** Whether a character with this name has been added. */
	public boolean contains(String name) {
		String normalized = normalize(name);
		for (String existing : listNames()) {
			if (normalize(existing).equals(normalized)) return true;
		}
		return false;
	}

	/** The display names of all configured characters, in insertion order. */
	public List<String> listNames() {
		String raw = configManager.getConfiguration(CONFIG_GROUP, LIST_KEY);
		List<String> names = new ArrayList<>();

		if (raw != null && !raw.isEmpty()) {
			for (String name : raw.split(SEP)) {
				if (!name.trim().isEmpty()) names.add(name);
			}
		}

		return names;
	}

	/** Adds a character. No-op when already present. */
	public void add(String name) {
		if (name == null || name.trim().isEmpty()) return;
		if (contains(name)) return;

		List<String> names = listNames();
		names.add(name.trim());
		saveList(names);

		fireChanged();
	}

	/** Removes a character and clears its gender. The voice itself lives in {@link VoiceConfig}. */
	public void remove(String name) {
		String normalized = normalize(name);

		List<String> names = listNames();
		names.removeIf(existing -> normalize(existing).equals(normalized));
		saveList(names);

		configManager.unsetConfiguration(CONFIG_GROUP, GENDER_PREFIX + normalized);

		fireChanged();
	}

	/** The configured gender, or null when left on Auto. */
	@CheckForNull
	public Gender getGender(String name) {
		String value = configManager.getConfiguration(CONFIG_GROUP, GENDER_PREFIX + normalize(name));
		if (value == null) return null;

		try {
			Gender gender = Gender.valueOf(value);
			return gender == Gender.OTHER ? null : gender;
		}
		catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public void setGender(String name, @CheckForNull Gender gender) {
		String key = GENDER_PREFIX + normalize(name);

		if (gender == null || gender == Gender.OTHER) {
			configManager.unsetConfiguration(CONFIG_GROUP, key);
		}
		else {
			configManager.setConfiguration(CONFIG_GROUP, key, gender.name());
		}
	}

	private void saveList(List<String> names) {
		if (names.isEmpty()) {
			configManager.unsetConfiguration(CONFIG_GROUP, LIST_KEY);
		}
		else {
			configManager.setConfiguration(CONFIG_GROUP, LIST_KEY, String.join(SEP, names));
		}
	}

	/** Matches how the rest of the plugin standardizes names, so entries line up with speakers. */
	public static String normalize(String name) {
		return name == null ? "" : name.toLowerCase().trim().replaceAll("\\s+", " ");
	}
}
