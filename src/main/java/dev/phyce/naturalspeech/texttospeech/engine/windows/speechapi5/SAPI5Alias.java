package dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi5;

import com.google.common.collect.ImmutableBiMap;
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
	SUSAN(mobile("Susan"), "susan"),
	SEAN(mobile("Sean"), "sean"),
	HEERA(mobile("Heera"), "heera"),
	RAVI(mobile("Ravi"), "ravi"),
	MARK(mobile("Mark"), "mark"),
	KANGKANG(mobile("Kangkang"), "kang"),
	YAOYAO(mobile("Yaoyao"), "yao"),

	HAZELM(mobile("Hazel"), "hazelm"),
	DAVIDM(mobile("David"), "davidm"),
	ZIRAM(mobile("Zira"), "ziram"),
	HUIHUIM(mobile("Huihui"), "huim"),

	// Cortana Voices
	// HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Speech_OneCore\CortanaVoices
	MATILDA(mobile("Matilda"), "matilda"),
	EVACA(mobile("Eva(Canada)"), "evaca"),
	EVA(mobile("Eva"), "eva"),

	;
	public final String sapiName;
	public final String modelName;

	private static String desktop(String name) {return "Microsoft " + name + " Desktop";}
	private static String mobile(String name) {return "Microsoft " + name;}


	public final static ImmutableBiMap<String, String> sapiToModelName;
	public final static ImmutableBiMap<String, String> modelToSapiName;

	static {
		ImmutableBiMap.Builder<String, String> sapiToModelNameBuilder = ImmutableBiMap.builder();
		for (SAPI5Alias model : SAPI5Alias.values()) {
			sapiToModelNameBuilder.put(model.sapiName, model.modelName);
		}
		sapiToModelName = sapiToModelNameBuilder.build();
		modelToSapiName = sapiToModelName.inverse();
	}
}