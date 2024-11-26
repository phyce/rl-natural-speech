package dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi4;

import com.google.common.collect.ImmutableBiMap;
import dev.phyce.naturalspeech.texttospeech.Gender;
import javax.annotation.CheckForNull;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public enum SAPI4Cache {
	SAM("Sam", "sam", Gender.MALE, 100, 50, 200, 150, 30, 450),

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
	WHISPER_MALE("Male Whisper", "whispermale", Gender.MALE, 113, 56, 226, 170, 30, 510),

	TRUEVOICE_MALE1("Adult Male #1, American English (TruVoice)", "trueman1", Gender.MALE, 85, 50, 400, 150, 50, 250),
	TRUEVOICE_MALE2("Adult Male #2, American English (TruVoice)", "trueman2", Gender.MALE, 50, 50, 400, 150, 50, 250),
	TRUEVOICE_MALE3("Adult Male #3, American English (TruVoice)", "trueman3", Gender.MALE, 125, 50, 400, 150, 50, 250),
	TRUEVOICE_MALE4("Adult Male #4, American English (TruVoice)", "trueman4", Gender.MALE, 73, 50, 400, 150, 50, 250),
	TRUEVOICE_MALE5("Adult Male #5, American English (TruVoice)", "trueman5", Gender.MALE, 129, 50, 400, 150, 50, 250),
	TRUEVOICE_MALE6("Adult Male #6, American English (TruVoice)", "trueman6", Gender.MALE, 89, 50, 400, 120, 50, 250),
	TRUEVOICE_MALE7("Adult Male #7, American English (TruVoice)", "trueman7", Gender.MALE, 117, 50, 400, 150, 50, 250),
	TRUEVOICE_MALE8("Adult Male #8, American English (TruVoice)", "trueman8", Gender.MALE, 203, 50, 400, 150, 50, 250),

	TRUEVOICE_FEMALE1("Adult Female #1, American English (TruVoice)", "truefemale1", Gender.FEMALE, 208, 50, 400, 150,
		50, 250),
	TRUEVOICE_FEMALE2("Adult Female #2, American English (TruVoice)", "truefemale2", Gender.FEMALE, 152, 50, 400, 150,
		50, 250),
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
		for (SAPI4Cache model : SAPI4Cache.values()) {
			sapiToModelNameBuilder.put(model.sapiName, model.modelName);
		}
		sapiToVoiceName = sapiToModelNameBuilder.build();
		voiceToSapiName = sapiToVoiceName.inverse();
	}


	@CheckForNull
	public static SAPI4Cache findSapiName(@NonNull String sapiName) {
		for (SAPI4Cache cached : SAPI4Cache.values()) {
			if (cached.sapiName.equals(sapiName)) {
				return cached;
			}
		}
		return null;
	}

	@CheckForNull
	public static SAPI4Cache findVoiceName(@NonNull String voiceName) {
		for (SAPI4Cache cached : SAPI4Cache.values()) {
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
