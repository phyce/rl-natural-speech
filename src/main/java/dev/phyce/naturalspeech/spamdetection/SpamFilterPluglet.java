package dev.phyce.naturalspeech.spamdetection;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

// A "pluglet" is defined as a small or simplified version of a plugin.
// Only things needed (as a bare minimum) to re-implement a single function
// Hub rules disallows reflection, so Natural Speech re-implements the code, but uses Spam Filters' user configs.
@Slf4j
@PluginSingleton
public class SpamFilterPluglet {

	private final PluginManager pluginManager;
	private final ConfigManager configManager;

	private static final String SPAM_FILTER_GROUP_NAME = "spamfilter";
	private static final String SPAM_FILTER_CONFIG_KEY_THRESHOLD = "threshold";
	private static final String SPAM_FILTER_MARK_SPAM_OPTION = "Mark spam";
	private static final String SPAM_FILTER_MARK_HAM_OPTION = "Mark ham";

	private boolean goodCorpusDirty = false;
	private boolean badCorpusDirty = false;

	private int threshold = 100;


	private final List<String> builtinGoodCorpus = new ArrayList<>();
	private final List<String> builtinBadCorpus = new ArrayList<>();
	private final List<String> userGoodCorpus = new ArrayList<>();
	private final List<String> userBadCorpus = new ArrayList<>();

	private final Map<String, Integer> goodCounts = new HashMap<>();
	private final Map<String, Integer> badCounts = new HashMap<>();

	private static final String FILE_NAME_GOOD_CORPUS = "spamfilter_good_corpus.txt";
	private static final String FILE_NAME_BAD_CORPUS = "spamfilter_bad_corpus.txt";

	private boolean isPluginEnabled;

	@Inject
	public SpamFilterPluglet(PluginManager pluginManager, ConfigManager configManager) {
		this.pluginManager = pluginManager;
		this.configManager = configManager;

		// look for spam filter if it's already loaded
		Plugin spamFilterPlugin = null;
		for (Plugin plugin : pluginManager.getPlugins()) {
			if (plugin.getClass().getSimpleName().equals("SpamFilterPlugin")) {
				log.info("Spam Filter Plugin Detected.");
				spamFilterPlugin = plugin;
			}
		}
		isPluginEnabled = spamFilterPlugin != null && pluginManager.isPluginEnabled(spamFilterPlugin);

		// built-in corpus is copied into Natural Speech's resource folder
		// Must be loaded first beauce loadUserCorpus implicitly counts
		// but load Built-in does not. This is to avoid reading the built-in over and over again.
		loadBuiltinCorpus();

		// spam-filter saves user corupus in .runelite/spam-filter/user_good_corpus.txt
		loadUserCorpus();

		// try load threshold
		loadThreshold();
	}

	public boolean isSpam(String text) {
		if (!isPluginEnabled) {
			return false;
		}

		if (goodCorpusDirty) {
			//			log.trace("Reloading good corpus, due to dirty flag...");
			loadUserGoodCorpus();
			goodCorpusDirty = false;
		}
		if (badCorpusDirty) {
			//			log.trace("Reloading bad corpus, due to dirty flag...");
			loadUserBadCorpus();
			badCorpusDirty = false;

		}

		float spamScore = pMessageBad(text);
		return spamScore > threshold / 100f;
	}

	private void loadThreshold() {
		String result = configManager.getConfiguration(SPAM_FILTER_GROUP_NAME, SPAM_FILTER_CONFIG_KEY_THRESHOLD);
		if (result == null) {
			log.debug("{}.{} config did not exist in configManager",
				SPAM_FILTER_GROUP_NAME, SPAM_FILTER_CONFIG_KEY_THRESHOLD);
			return;
		}
		try {
			threshold = Integer.parseInt(result);
		} catch (NumberFormatException e) {
			log.error("ErrorResult parsing threshold value from spam filters' config", e);
		}
	}

	private void loadUserCorpus() {
		loadUserGoodCorpus();
		loadUserBadCorpus();
	}

	private void loadUserBadCorpus() {
		File configDir = new File(RuneLite.RUNELITE_DIR, "spam-filter");
		if (!configDir.exists()) {
			log.trace("No {} found", configDir);
			return;
		}
		File userBadCorpusFile = new File(configDir, "user_bad_corpus.txt");
		if (userBadCorpusFile.exists()) {
			try {
				userBadCorpus.clear();
				userBadCorpus.addAll(Files.readAllLines(userBadCorpusFile.toPath()));
				log.trace("Loaded user bad corpus of {} lines", userBadCorpus.size());

				badCounts.clear();
				countTokens(badCounts, builtinBadCorpus);
				countTokens(badCounts, userBadCorpus);
			} catch (IOException e) {
				log.error("ErrorResult reading {}", userBadCorpusFile);
			}
		}
		else {
			log.trace("No {} found", userBadCorpusFile);
		}
	}

	private void loadUserGoodCorpus() {
		File configDir = new File(RuneLite.RUNELITE_DIR, "spam-filter");

		if (!configDir.exists()) {
			log.trace("No {} found", configDir);
			return;
		}

		File userGoodCorpusFile = new File(configDir, "user_good_corpus.txt");
		if (userGoodCorpusFile.exists()) {
			try {
				userGoodCorpus.clear();
				userGoodCorpus.addAll(Files.readAllLines(userGoodCorpusFile.toPath()));
				log.trace("Loaded user good corpus of {} lines", userGoodCorpus.size());

				goodCounts.clear();
				countTokens(goodCounts, builtinGoodCorpus);
				countTokens(goodCounts, userGoodCorpus);
			} catch (IOException e) {
				log.error("ErrorResult reading {}", userGoodCorpusFile);
			}
		}
		else {
			log.trace("No {} found", userGoodCorpusFile);
		}

	}

	private void loadBuiltinCorpus() {
		InputStream goodCorpusRes = this.getClass().getResourceAsStream(FILE_NAME_GOOD_CORPUS);
		if (goodCorpusRes != null) {
			BufferedReader goodCorpusReader =
				new BufferedReader(new InputStreamReader(goodCorpusRes, StandardCharsets.UTF_8));
			goodCorpusReader.lines().forEach(builtinGoodCorpus::add);
			try {
				goodCorpusReader.close();
			} catch (IOException e) {
				log.error("ErrorResult reading SpamFilter file from {}.", FILE_NAME_GOOD_CORPUS);
			}
		}

		InputStream badCorpusRes = SpamFilterPluglet.class.getResourceAsStream(FILE_NAME_BAD_CORPUS);
		if (badCorpusRes != null) {
			BufferedReader badCorpusReader =
				new BufferedReader(new InputStreamReader(badCorpusRes, StandardCharsets.UTF_8));
			badCorpusReader.lines().forEach(builtinBadCorpus::add);
			try {
				badCorpusReader.close();
			} catch (IOException e) {
				log.error("ErrorResult reading SpamFilter file from {}.", FILE_NAME_BAD_CORPUS);
			}
		}
	}

	private float pTokenBad(String token) {
		int goodCount = goodCounts.getOrDefault(token, 0);
		int badCount = badCounts.getOrDefault(token, 0);
		if (goodCount + badCount == 0) {
			return 0.4f;
		}
		float rawProbability = (float) badCount / (float) (goodCount + badCount);
		float clampUpperBound = Math.min(rawProbability, 0.99f);
		//noinspection UnnecessaryLocalVariable
		float clampLowerBound = Math.max(clampUpperBound, 0.01f);

		return clampLowerBound;
	}

	private float pMessageBad(String message) {
		String msg = message.toLowerCase();
		String[] tokens = msg.split("\\s+");
		if (tokens.length == 1 && !message.startsWith("!")) {
			// single-word messages easily induce false positives so we ignore them.
			// however, messages starting with "!" are still processed since they are often commands
			// for gambling bots (e.g. "!w")
			return 0.0f;
		}
		Set<String> tokensUnique = new HashSet<>(Arrays.asList(tokens));
		float pPredictorsCorrect = 1f;
		float pPredictorsIncorrect = 1f;
		for (String token : tokensUnique) {
			float p = pTokenBad(token);
			pPredictorsCorrect *= p;
			pPredictorsIncorrect *= (1 - p);
		}
		return pPredictorsCorrect / (pPredictorsCorrect + pPredictorsIncorrect);
	}

	private static void countTokens(Map<String, Integer> out_result, List<String> corpus) {
		for (String message : corpus) {
			message = message.toLowerCase();
			String[] tokens = message.split("\\s");
			for (String token : tokens) {
				out_result.put(token, out_result.getOrDefault(token, 0) + 1);
			}
		}
	}

	@Subscribe
	private void onPluginChanged(PluginChanged event) {
		// if spam filter was installed after runelite session started
		if (event.getPlugin().getClass().getSimpleName().equals("SpamFilterPlugin")) {
			if (event.isLoaded()) {
				log.trace("Detected SpamFilter plugin activated.");
				loadUserCorpus();
				loadThreshold();
				isPluginEnabled = true;
			}
			else {
				isPluginEnabled = false;
				log.trace("Detected SpamFilter plugin deactivated.");
			}
		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event) {
		if (!isPluginEnabled) return;

		if (event.getGroup().equals(SPAM_FILTER_GROUP_NAME)
			&& event.getKey().equals(SPAM_FILTER_CONFIG_KEY_THRESHOLD)) {
			loadThreshold();
			log.trace("Detected SpamFilter {} change to {}", SPAM_FILTER_CONFIG_KEY_THRESHOLD, event.getNewValue());
		}
	}

	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked event) {
		if (!isPluginEnabled) return;

		MenuEntry menuEntry = event.getMenuEntry();
		if (menuEntry.getType() == MenuAction.RUNELITE) {
			if (menuEntry.getOption().equals(SPAM_FILTER_MARK_HAM_OPTION)) {
				goodCorpusDirty = true;
				log.trace("Detected SpamFilter Mark ham. Marking goodCorpus file dirty.");
			}
			if (menuEntry.getOption().equals(SPAM_FILTER_MARK_SPAM_OPTION)) {
				badCorpusDirty = true;
				log.trace("Detected SpamFilter Mark spam. Marking badCorpus file dirty.");
			}
		}
	}
}
