package dev.phyce.naturalspeech.tts.nativespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

/**
 * Windows names its voices too long to type into a voice setting, so they are shortened to the
 * given name. Pure string work, so this runs everywhere unlike the rest of the Windows tests.
 */
public class WindowsVoiceIDTest {

	@Test
	public void shortensTheVoicesEveryWindowsInstallHas() {
		assertEquals("david", WindowsSpeechProcess.shortID("Microsoft David Desktop"));
		assertEquals("zira", WindowsSpeechProcess.shortID("Microsoft Zira Desktop"));
		assertEquals("hazel", WindowsSpeechProcess.shortID("Microsoft Hazel Desktop"));
	}

	@Test
	public void dropsTheLanguageSuffix() {
		// how the OneCore voices report themselves
		assertEquals("zira", WindowsSpeechProcess.shortID("Microsoft Zira Desktop - English (United States)"));
		assertEquals("george", WindowsSpeechProcess.shortID("Microsoft George - English (United Kingdom)"));
	}

	@Test
	public void keepsThirdPartyVoicesUsable() {
		// not every installed voice is a Microsoft one
		assertEquals("ivona-brian", WindowsSpeechProcess.shortID("IVONA Brian"));
	}

	@Test
	public void neverProducesSomethingAVoiceSettingCannotHold() {
		for (String systemName : new String[] {
			"Microsoft David Desktop",
			"Microsoft Zira Desktop - English (United States)",
			"IVONA Brian",
			"Microsoft Desktop",
			"weird: name",
		}) {
			String id = WindowsSpeechProcess.shortID(systemName);

			assertFalse(systemName + " shortened to nothing", id.isEmpty());
			// a voice setting is "model:id", split on the first colon
			assertFalse(systemName + " shortened to something with a colon", id.contains(":"));
			assertFalse(systemName + " shortened to something with a space", id.contains(" "));
		}
	}
}
