package dev.phyce.naturalspeech.tts.wsapi4;

import com.google.common.collect.ImmutableBiMap;
import javax.annotation.CheckForNull;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
enum SAPI4ModelCache {
	SAM("Sam", "sam", 100, 50, 200, 150, 30, 450),
	MARY("Mary", "mary", 169, 84, 338, 170, 30, 510),
	MARY_PHONE("Mary (for Telephone)", "maryphone", 169, 84, 338, 170, 30, 510),
	MARY_HALL("Mary in Hall", "maryhall", 169, 84, 338, 170, 30, 510),
	MARY_STADIUM("Mary in Stadium", "marystadium", 169, 84, 338, 170, 30, 510),
	MARY_SPACE("Mary in Space", "maryspace", 169, 84, 338, 170, 30, 510),
	MIKE("Mike", "mike", 113, 56, 226, 170, 30, 510),
	MIKE_PHONE("Mike (for Telephone)", "mikephone", 113, 56, 226, 170, 30, 510),
	MIKE_HALL("Mike in Hall", "mikehall", 113, 56, 226, 170, 30, 510),
	MIKE_STADIUM("Mike in Stadium", "mikestadium", 113, 56, 226, 170, 30, 510),
	MIKE_SPACE("Mike in Space", "mikespace", 113, 56, 226, 170, 30, 510),
	ROBO1("RoboSoft One", "robo1", 75, 37, 150, 130, 30, 390),
	ROBO2("RoboSoft Two", "robo2", 120, 60, 240, 130, 30, 390),
	ROBO3("RoboSoft Three", "robo3", 113, 56, 226, 170, 30, 510),
	ROBO4("RoboSoft Four", "robo4", 169, 84, 338, 170, 30, 510),
	ROBO5("RoboSoft Five", "robo5", 120, 60, 240, 130, 30, 390),
	ROBO6("RoboSoft Six", "robo6", 100, 50, 200, 130, 30, 390),
	WHISPER_FEMALE("Female Whisper", "whisperfemale", 169, 84, 338, 170, 30, 510),
	WHISPER_MALE("Male Whisper", "whispermale", 113, 56, 226, 170, 30, 510)
	;

	final String sapiName;
	final String modelName;
	final int defaultSpeed;
	final int minSpeed;
	final int maxSpeed;
	final int defaultPitch;
	final int minPitch;
	final int maxPitch;

	public final static ImmutableBiMap<String, String> sapiToModelName;
	public final static ImmutableBiMap<String, String> modelToSapiName;

	static {
		ImmutableBiMap.Builder<String, String> sapiToModelNameBuilder = ImmutableBiMap.builder();
		for (SAPI4ModelCache model : SAPI4ModelCache.values()) {
			sapiToModelNameBuilder.put(model.sapiName, model.modelName);
		}
		sapiToModelName = sapiToModelNameBuilder.build();
		modelToSapiName = sapiToModelName.inverse();
	}

	@CheckForNull
	public static SAPI4ModelCache findSapiName(@NonNull String sapiName) {
		for (SAPI4ModelCache cached : SAPI4ModelCache.values()) {
			if (cached.sapiName.equals(sapiName)) {
				return cached;
			}
		}
		return null;
	}

	public static boolean isCached(String sapiName) {
		return sapiToModelName.containsKey(sapiName);
	}

	@Override
	public String toString() {
		return String.format(
			"%s(sapiName:%s modelName:%s speed:%d pitch:%d)",
			this.name(),
			sapiName,
			modelName,
			defaultSpeed,
			defaultPitch
		);
	}
}
