package dev.phyce.naturalspeech.texttospeech.engine;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.sun.jna.Pointer;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.texttospeech.Gender;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.texttospeech.Voice;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.avfaudio.AVAudioBuffer;
import dev.phyce.naturalspeech.texttospeech.engine.macos.avfaudio.AVAudioCommonFormat;
import dev.phyce.naturalspeech.texttospeech.engine.macos.avfaudio.AVAudioFormat;
import dev.phyce.naturalspeech.texttospeech.engine.macos.avfaudio.AVAudioPCMBuffer;
import dev.phyce.naturalspeech.texttospeech.engine.macos.avfoundation.AVSpeechSynthesisVoice;
import dev.phyce.naturalspeech.texttospeech.engine.macos.avfoundation.AVSpeechSynthesizer;
import dev.phyce.naturalspeech.texttospeech.engine.macos.avfoundation.AVSpeechSynthesizerBufferCallback;
import dev.phyce.naturalspeech.texttospeech.engine.macos.avfoundation.AVSpeechUtterance;
import dev.phyce.naturalspeech.texttospeech.engine.macos.javautil.NSAutoRelease;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.Block;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.objc.LibObjC;
import dev.phyce.naturalspeech.utils.PlatformUtil;
import dev.phyce.naturalspeech.utils.Result;
import static dev.phyce.naturalspeech.utils.Result.Error;
import static dev.phyce.naturalspeech.utils.Result.Ok;
import static dev.phyce.naturalspeech.utils.Result.ResultFutures.immediateError;
import static dev.phyce.naturalspeech.utils.Result.ResultFutures.immediateOk;
import dev.phyce.naturalspeech.utils.StreamableFuture;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Vector;
import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@PluginSingleton
public class MacSpeechEngine extends ManagedSpeechEngine {
	// need lombok to expose secret lock because future thread needs to synchronize on the lock
	public static final String MACOS_MODEL_NAME = "mac";

	private final NaturalSpeechConfig config;

	@Nullable
	private final ID avSynthesizer = avSynthesizer(this);

	@Getter
	@NonNull
	private final ImmutableMap<VoiceID, ID> nativeVoices = nativeVoices();

	@Getter // @Override
	@NonNull
	private final ImmutableSet<VoiceID> voiceIDs = voiceIDs(nativeVoices);

	@Getter // @Override
	@NonNull
	private final ImmutableSet<Voice> voices = voices(nativeVoices);

	@Inject
	public MacSpeechEngine(NaturalSpeechConfig config) {
		this.config = config;
	}

	@Override
	public @NonNull Result<StreamableFuture<Audio>, Rejection> generate(
			@NonNull VoiceID voiceID,
			@NonNull String text
	) {
		if (!isAlive()) return Error(Rejection.DEAD(this));
		if (!voiceIDs.contains(voiceID)) return Error(Rejection.REJECT(this));

		ID nativeVoice = nativeVoices.get(voiceID);
		ID utterance = AVSpeechUtterance.getSpeechUtteranceWithString(text);
		AVSpeechUtterance.setVoice(utterance, nativeVoice);

		BufferCallback callback = new BufferCallback();
		Block bufferBlock = Block.alloc(callback);
		AVSpeechSynthesizer.writeUtteranceToBufferCallback(avSynthesizer, utterance, bufferBlock);
		Block.release(bufferBlock);

		StreamableFuture<Audio> future = StreamableFuture.singular(callback.onComplete());

		return Ok(future);
	}

	@Override
	@NonNull
	ListenableFuture<Result<Void, EngineError>> startup() {
		if (!PlatformUtil.IS_MAC) {
			log.trace("Not MacOS, skipping");
			return immediateError(EngineError.NO_RUNTIME(this));
		}

		if (this.nativeVoices.isEmpty()) {
			return immediateError(EngineError.NO_MODEL(this));
		}

		// array to arraylist
		//		nativeVoices.forEach((voiceID, nativeVoice) -> {
		//			Gender gender = Gender.fromAVGender(AVSpeechSynthesisVoice.getGender(nativeVoice));
		//			voiceManager.register(voiceID, gender);
		//		});

		return immediateOk();
	}

	@Override
	void shutdown() {
	}

	@NonNull
	public static ImmutableSet<VoiceID> voiceIDs(@NonNull ImmutableMap<VoiceID, ID> nativeVoices) {
		return nativeVoices.keySet();
	}

	@NonNull
	public static ImmutableSet<Voice> voices(@NonNull ImmutableMap<VoiceID, ID> nativeVoices) {
		ImmutableSet.Builder<Voice> builder = ImmutableSet.builder();
		nativeVoices.forEach((voiceId, nativeVoice) -> {
			Gender gender = Gender.fromAVGender(AVSpeechSynthesisVoice.getGender(nativeVoice));
			builder.add(Voice.of(voiceId, gender));
		});
		return builder.build();
	}

	@NonNull
	private static ImmutableMap<VoiceID, ID> nativeVoices() {

		if (!PlatformUtil.IS_MAC) return ImmutableMap.of();

		ID[] result = AVSpeechSynthesisVoice.getSpeechVoices();
		var builder = ImmutableMap.<VoiceID, ID>builder();
		for (ID voice : result) {
			String name = StringUtils.stripAccents(AVSpeechSynthesisVoice.getName(voice))
					.toLowerCase()
					.replace(" ", "");
			VoiceID voiceID = VoiceID.of(MACOS_MODEL_NAME, name);
			builder.put(voiceID, voice);
		}
		return builder.build();
	}

	@Nullable
	private static ID avSynthesizer(@NonNull MacSpeechEngine self) {
		if (!PlatformUtil.IS_MAC) {
			return null;
		}
		else {
			ID avSynthesizer = AVSpeechSynthesizer.alloc();
			log.info("Allocated AVSpeechSynthesizer@{}", avSynthesizer);
			// we retain until this-object GC
			NSAutoRelease.register(self, avSynthesizer);
			return avSynthesizer;
		}
	}


	@Override
	public boolean isAlive() {
		return !nativeVoices.isEmpty() && !config.simulateNoEngine();
	}

	@Override
	public @NonNull EngineType getEngineType() {
		return EngineType.BUILTIN_OS;
	}

	@Override
	public @NonNull String getEngineName() {
		return "mac";
	}

	private static class BufferCallback implements AVSpeechSynthesizerBufferCallback {

		private AudioFormat detectedFormat = null;
		private final Vector<short[]> int16AudioSegments;

		private final SettableFuture<Audio> onComplete = SettableFuture.create();

		public ListenableFuture<Audio> onComplete() {
			return onComplete;
		}

		public BufferCallback() {
			int16AudioSegments = new Vector<>();
		}

		@Override
		public void invoke(Pointer block, Pointer pAVAudioBuffer) {
			try {
				ID avAudioBuffer = new ID(pAVAudioBuffer);

				if (!LibObjC.object_getClass(avAudioBuffer).equals(AVAudioPCMBuffer.idClass)) {
					log.error("AVAudioBuffer is not a AVAudioPCMBuffer");
					return;
				}
				int frameLength = Ints.checkedCast(AVAudioPCMBuffer.getFrameLength(avAudioBuffer));
				int frameCapacity = Ints.checkedCast(AVAudioPCMBuffer.getFrameCapacity(avAudioBuffer));
				int stride = Ints.checkedCast(AVAudioPCMBuffer.getStride(avAudioBuffer));

				if (frameLength != 0 && frameLength != 1) {
					ID avFormat = AVAudioBuffer.getFormat(avAudioBuffer);

					AVAudioCommonFormat format = AVAudioFormat.getCommonFormat(avFormat);
					boolean standard = AVAudioFormat.getIsStandard(avFormat);
					float sampleRate = (float) AVAudioFormat.getSampleRate(avFormat);
					int channelCount = Ints.checkedCast(AVAudioFormat.getChannelCount(avFormat));
					log.trace("AVAudioFormat: format({}) standard({}) sampleRate({}) channelCount({})", format,
							standard, sampleRate, channelCount);

					// determine format
					if (detectedFormat == null) {
						switch (format) {
							case AVAudioPCMFormatInt16:
							case AVAudioPCMFormatInt32:
							case AVAudioPCMFormatFloat32:
								detectedFormat = new AudioFormat(
										AudioFormat.Encoding.PCM_SIGNED,
										sampleRate,
										16,
										channelCount,
										2,
										sampleRate,
										false
								);
								break;
							case AVAudioPCMFormatFloat64:
							case AVAudioOtherFormat:
							default:
								// can't do anything
								log.warn("Unsupported AVAudioCommonFormat.");
								break;
						}
					}

					switch (format) {
						case AVAudioPCMFormatInt16: {
							Pointer int16ChannelData =
									AVAudioPCMBuffer.getInt16ChannelData(avAudioBuffer).getPointer(0);
							log.trace("Int16 Buffer Frame Length: {}, Frame Capacity: {}, Stride: {}",
									frameLength, frameCapacity, stride);

							short[] shortData = int16ChannelData.getShortArray(0L, frameLength);
							int16AudioSegments.add(shortData);
							break;
						}
						case AVAudioPCMFormatFloat32: {
							Pointer float32ChannelData =
									AVAudioPCMBuffer.getFloatChannelData(avAudioBuffer).getPointer(0);
							log.trace("Float32 Buffer Frame Length: {}, Frame Capacity: {}, Stride: {}",
									frameLength, frameCapacity, stride);

							float[] floatData = float32ChannelData.getFloatArray(0L, frameLength);

							// reformat into playable format
							short[] shortData = new short[frameLength];
							for (int i = 0; i < floatData.length; i++) {
								shortData[i] = (short) (floatData[i] * Short.MAX_VALUE);
							}

							int16AudioSegments.add(shortData);

							break;
						}
						case AVAudioPCMFormatInt32: {
							Pointer int32ChannelData =
									AVAudioPCMBuffer.getInt32ChannelData(avAudioBuffer).getPointer(0);
							log.trace("Int32 Buffer Frame Length: {}, Frame Capacity: {}, Stride: {}",
									frameLength, frameCapacity, stride);

							int[] intData = int32ChannelData.getIntArray(0L, frameLength);

							// reformat into to float
							float[] floatData = new float[frameLength];
							for (int i = 0; i < intData.length; i++) {
								floatData[i] = ((float) intData[i]) / Integer.MAX_VALUE;
							}

							// reformat into playable format
							short[] shortData = new short[frameLength];
							for (int i = 0; i < floatData.length; i++) {
								shortData[i] = (short) (floatData[i] * Short.MAX_VALUE);
							}

							int16AudioSegments.add(shortData);
							break;
						}
						case AVAudioPCMFormatFloat64:
						case AVAudioOtherFormat:
						default:
							break;
					}
				}
				else {

					if (detectedFormat == null) {
						log.error("Did not detect valid format, skipping");
						return;
					}

					// combine all audio data
					int shortLength = int16AudioSegments.stream().mapToInt(segment -> segment.length).sum();

					byte[] audioData = new byte[shortLength * 2];
					ShortBuffer shortBuilder =
							ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

					int offset = 0;
					for (short[] data : int16AudioSegments) {
						shortBuilder.position(offset).put(data);
						offset += data.length;
					}

					log.trace("Completed Callback, total byte size: {}", audioData.length);

					onComplete.set(Audio.of(audioData, detectedFormat));
				}
			} catch (Exception e) {
				log.error("ErrorResult in buffer callback", e);
			}

		}
	}
}
