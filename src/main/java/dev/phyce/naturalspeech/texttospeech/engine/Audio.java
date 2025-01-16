package dev.phyce.naturalspeech.texttospeech.engine;

import com.google.common.base.Preconditions;
import java.io.ByteArrayInputStream;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor="of")
public class Audio {
	byte[] audioStream;
	AudioFormat audioFormat;

	public static Audio join(List<Audio> list) {

		AudioFormat audioFormat = list.get(0).audioFormat;

		int totalLength = list.stream().mapToInt(audio -> audio.audioStream.length).sum();
		byte[] result = new byte[totalLength];
		int offset = 0;
		for (Audio audio : list) {
			// TODO: This can be supported, but it's not necessary.
			Preconditions.checkState(audio.audioFormat.matches(audioFormat),
					"Concatenating Audio with non-matching formats %s != %s.", audioFormat, audio.audioFormat);

			System.arraycopy(audio.audioStream, 0, result, offset, audio.audioStream.length);
			offset += audio.audioStream.length;
		}

		return Audio.of(result, audioFormat);
	}

	@NonNull
	public AudioInputStream toInputStream() {
		return new AudioInputStream(
				new ByteArrayInputStream(this.audioStream),
				this.audioFormat,
				this.audioStream.length);
	}
}
