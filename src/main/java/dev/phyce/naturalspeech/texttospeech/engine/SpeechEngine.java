package dev.phyce.naturalspeech.texttospeech.engine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.phyce.naturalspeech.texttospeech.Voice;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.utils.Result;
import dev.phyce.naturalspeech.utils.StreamableFuture;
import java.util.List;
import java.util.function.Predicate;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

public interface SpeechEngine {

	@NonNull
	Result<@NonNull StreamableFuture<Audio>, @NonNull Rejection> generate(
		@NonNull VoiceID voiceID,
		@NonNull String text,
		@NonNull String line
	);

	boolean isAlive();

	ImmutableSet<Voice> getVoices();

	ImmutableSet<VoiceID> getVoiceIDs();

	@NonNull
	EngineType getEngineType();

	@NonNull
	String getEngineName();

	/**
	 * Cancels queued and processing speak tasks. Cancel should not try to silence ongoing AudioEngine lines.
	 * For silencing + canceling, use {@link SpeechManager#silence(Predicate)}
	 */
	void silence(Predicate<String> lineCondition);

	void silenceAll();

	@EqualsAndHashCode(callSuper=true)
	@Value
	class Rejection extends Throwable {

		public enum Reason {
			MULTIPLE,
			REJECT,
			DEAD;
		}

		@NonNull
		public SpeechEngine engine;
		@NonNull
		public Reason reason;

		@NonNull
		public ImmutableList<Rejection> childs;

		public <T extends SpeechEngine> Rejection(@NonNull T engine, @NonNull Reason reason) {
			super("");
			this.engine = engine;
			this.reason = reason;
			this.childs = ImmutableList.of();
		}

		public <T extends SpeechEngine> Rejection(@NonNull T engine, @NonNull List<Rejection> rejections) {
			super("");
			this.engine = engine;
			this.reason = Reason.MULTIPLE;
			this.childs = ImmutableList.copyOf(rejections);

		}

		public static Rejection MULTIPLE(@NonNull SpeechEngine engine, @NonNull List<Rejection> rejections) {
			return new Rejection(engine, rejections);
		}

		public static Rejection REJECT(@NonNull SpeechEngine engine) {
			return new Rejection(engine, Reason.REJECT);
		}

		public static Rejection DEAD(@NonNull SpeechEngine engine) {
			return new Rejection(engine, Reason.DEAD);
		}
	}

	enum EngineType {
		MANAGER, BUILTIN_OS, EXTERNAL_DEPENDENCY,
		// NETWORKED (some day in the future?)
	}
}
