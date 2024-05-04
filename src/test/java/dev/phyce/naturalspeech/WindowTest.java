package dev.phyce.naturalspeech;

import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi5.SAPI5Process;
import java.io.ByteArrayInputStream;
import java.util.List;
import javax.sound.sampled.AudioInputStream;
import org.junit.Test;

public class WindowTest {
	@Test
	public void testWSAPI5() throws InterruptedException {
		SAPI5Process wsapi5 = SAPI5Process.start();
		assert wsapi5 != null;

		List<SAPI5Process.SAPI5Voice> availableVoices = wsapi5.getAvailableVoices();
		System.out.println(availableVoices);

		AudioEngine audioEngine = new AudioEngine();


		for (SAPI5Process.SAPI5Voice availableVoice : availableVoices) {
			wsapi5.generateAudio(availableVoice.getName(), "Hello, Natural Speech",
				(audio) -> {
					AudioInputStream stream =
						new AudioInputStream(new ByteArrayInputStream(audio), SAPI5Process.AUDIO_FORMAT, audio.length);
					audioEngine.play(availableVoice.getName(), stream, () -> 0f);
				});
		}
		Thread.sleep(3000L);
	}
}
