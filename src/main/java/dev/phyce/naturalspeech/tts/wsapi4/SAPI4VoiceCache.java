package dev.phyce.naturalspeech.tts.wsapi4;

import com.google.common.collect.ImmutableBiMap;
import dev.phyce.naturalspeech.enums.Gender;
import javax.annotation.CheckForNull;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public enum SAPI4VoiceCache {
	SAM("Sam", "sam", Gender.MALE,100, 50, 200, 150, 30, 450),

	MARY("Mary", "mary", Gender.FEMALE, 169, 84, 338, 170, 30, 510),
	MARY_PHONE("Mary (for Telephone)", "maryphone", Gender.FEMALE, 169, 84, 338, 170, 30, 510),
	MARY_HALL("Mary in Hall", "maryhall", Gender.FEMALE, 169, 84, 338, 170, 30, 510),
	MARY_STADIUM("Mary in Stadium", "marystadium", Gender.FEMALE, 169, 84, 338, 170, 30, 510),
	MARY_SPACE("Mary in Space", "maryspace", Gender.FEMALE, 169, 84, 338, 170, 30, 510),

	MIKE("Mike", "mike", Gender.MALE, 113, 56, 226, 170, 30, 510),
	MIKE_PHONE("Mike (for Telephone)", "mikephone", Gender.MALE, 113, 56, 226, 170, 30, 510),
	MIKE_HALL("Mike in Hall", "mikehall", Gender.MALE, 113, 56, 226, 170, 30, 510),
	MIKE_STADIUM("Mike in Stadium", "mikestadium", Gender.MALE, 113, 56, 226, 170, 30, 510),
	MIKE_SPACE("Mike in Space", "mikespace", Gender.MALE, 113, 56, 226, 170, 30, 510),

	ROBO1("RoboSoft One", "robo1", Gender.OTHER, 75, 37, 150, 130, 30, 390),
	ROBO2("RoboSoft Two", "robo2", Gender.OTHER, 120, 60, 240, 130, 30, 390),
	ROBO3("RoboSoft Three", "robo3", Gender.OTHER, 113, 56, 226, 170, 30, 510),
	ROBO4("RoboSoft Four", "robo4", Gender.OTHER, 169, 84, 338, 170, 30, 510),
	ROBO5("RoboSoft Five", "robo5", Gender.OTHER, 120, 60, 240, 130, 30, 390),
	ROBO6("RoboSoft Six", "robo6", Gender.OTHER, 100, 50, 200, 130, 30, 390),

	WHISPER_FEMALE("Female Whisper", "whisperfemale", Gender.FEMALE, 169, 84, 338, 170, 30, 510),
	WHISPER_MALE("Male Whisper", "whispermale", Gender.MALE, 113, 56, 226, 170, 30, 510)
	;

	public final String sapiName;
	public final String modelName;
	public final Gender gender;
	public final int defaultSpeed;
	public final int minSpeed;
	public final int maxSpeed;
	public final int defaultPitch;
	public final int minPitch;
	public final int maxPitch;

	public final static ImmutableBiMap<String, String> sapiToVoiceName;
	public final static ImmutableBiMap<String, String> voiceToSapiName;

	static {
		ImmutableBiMap.Builder<String, String> sapiToModelNameBuilder = ImmutableBiMap.builder();
		for (SAPI4VoiceCache model : SAPI4VoiceCache.values()) {
			sapiToModelNameBuilder.put(model.sapiName, model.modelName);
		}
		sapiToVoiceName = sapiToModelNameBuilder.build();
		voiceToSapiName = sapiToVoiceName.inverse();
	}


	@CheckForNull
	public static SAPI4VoiceCache findSapiName(@NonNull String sapiName) {
		for (SAPI4VoiceCache cached : SAPI4VoiceCache.values()) {
			if (cached.sapiName.equals(sapiName)) {
				return cached;
			}
		}
		return null;
	}

	@CheckForNull
	public static SAPI4VoiceCache findVoiceName(@NonNull String voiceName) {
		for (SAPI4VoiceCache cached : SAPI4VoiceCache.values()) {
			if (cached.modelName.equals(voiceName)) {
				return cached;
			}
		}
		return null;
	}

	public static boolean isCached(String sapiName) {
		return sapiToVoiceName.containsKey(sapiName);
	}

	@Override
	public String toString() {
		return String.format(
			"%s(sapiName:%s voiceName:%s speed:%d pitch:%d)",
			this.name(),
			sapiName,
			modelName,
			defaultSpeed,
			defaultPitch
		);
	}
}
