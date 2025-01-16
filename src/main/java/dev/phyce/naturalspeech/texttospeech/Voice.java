package dev.phyce.naturalspeech.texttospeech;

import com.google.common.collect.Range;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

@Value
@EqualsAndHashCode(onlyExplicitlyIncluded=true)
public class Voice {

	@EqualsAndHashCode.Include
	@NonNull
	VoiceID id;

	@NonNull
	Gender gender;

	@NonNull
	Range<Float> speed;

	@NonNull
	Range<Float> pitch;

	@NonNull
	public static Voice of(@NonNull VoiceID id, @NonNull Gender gender) {
		return new Voice(id, gender, Range.singleton(0f), Range.singleton(0f));
	}

	@NonNull
	public static Voice of(
		@NonNull VoiceID id,
		@NonNull Gender gender,
		@NonNull Range<Float> speed,
		@NonNull Range<Float> pitch
	) {
		return new Voice(id, gender, speed, pitch);
	}

	private Voice(
		@NonNull VoiceID id,
		@NonNull Gender gender,
		@NonNull Range<Float> speed,
		@NonNull Range<Float> pitch
	) {
		this.id = id;
		this.gender = gender;
		this.speed = speed;
		this.pitch = pitch;
	}
}
