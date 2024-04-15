package dev.phyce.naturalspeech.tts.wsapi5;

import com.google.common.collect.ImmutableBiMap;
import dev.phyce.naturalspeech.tts.wsapi4.SAPI4VoiceCache;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum SAPI5Alias {


	// Desktop Voices
	// HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Speech\Voices\
	DAVID(desktop("David"), "david"),
	HAZEL(desktop("Hazel"), "hazel"),
	ZIRA(desktop("Zira"), "zira"),
	HUIHUI(desktop("Huihui"), "hui"),

	// Mobile Voices
	// HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Speech_OneCore\Voices\
	CATHY(mobile("Catherine"), "cathy"),
	JAMES(mobile("James"), "james"),
	LINDA(mobile("Linda"), "linda"),
	RICHARD(mobile("Richard"), "richard"),
	GEORGE(mobile("George"), "george"),
	HAZELM(mobile("Hazel"), "hazelm"),
	SUSAN(mobile("Susan"), "susan"),
	SEAN(mobile("Sean"), "sean"),
	HEERA(mobile("Heera"), "heera"),
	RAVI(mobile("Ravi"), "ravi"),
	DAVIDM(mobile("David"), "david"),
	MARK(mobile("Mark"), "mark"),
	ZIRAM(mobile("Zira"), "ziram"),
	HUIHUIM(mobile("Huihui"), "huim"),
	KANGKANG(mobile("Kangkang"), "kang"),
	YAOYAO(mobile("Yaoyao"), "yao"),

	// Cortana Voices
	// HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Speech_OneCore\CortanaVoices
	MATILDA(mobile("Matilda"), "matilda"),
	EVACA(mobile("Eva(Canada)"), "evaca"),
	EVA(mobile("Eva"), "eva"),

	;

	private static String desktop(String name) {return "Microsoft " + name + " Desktop";}
	private static String mobile(String name) {return "Microsoft " + name;}

	public final String sapiName;
	public final String modelName;

	public final static ImmutableBiMap<String, String> sapiToVoiceName;
	public final static ImmutableBiMap<String, String> modelToSapiName;

	static {
		ImmutableBiMap.Builder<String, String> sapiToModelNameBuilder = ImmutableBiMap.builder();
		for (SAPI4VoiceCache model : SAPI4VoiceCache.values()) {
			sapiToModelNameBuilder.put(model.sapiName, model.modelName);
		}
		sapiToVoiceName = sapiToModelNameBuilder.build();
		modelToSapiName = sapiToVoiceName.inverse();
	}
}