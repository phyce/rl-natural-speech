package dev.phyce.naturalspeech.texttospeech.engine.macos;

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.sun.jna.Pointer;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechEngine;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfaudio.AVAudioBuffer;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfaudio.AVAudioCommonFormat;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfaudio.AVAudioFormat;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfaudio.AVAudioPCMBuffer;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfoundation.AVSpeechSynthesisVoice;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfoundation.AVSpeechSynthesizer;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfoundation.AVSpeechSynthesizerBufferCallback;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.avfoundation.AVSpeechUtterance;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.javautil.NSAutoRelease;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.Block;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.ID;
import dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.LibObjC;
import dev.phyce.naturalspeech.utils.Platforms;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@PluginSingleton
public class MacSpeechEngine implements SpeechEngine {
	// need lombok to expose secret lock because future thread needs to synchronize on the lock
	private final Object lock = new Object[0];

	public static final String MACOS_MODEL_NAME = "mac";

	private final AudioEngine audioEngine;
	private final VoiceManager voiceManager;

	@Getter
	private final Map<VoiceID, ID> nativeVoices = new HashMap<>();

	private final ID avSynthesizer;

	@Getter
	private boolean started = false;

	@Inject
	public MacSpeechEngine(
		AudioEngine audioEngine,
		VoiceManager voiceManager
	) {
		this.audioEngine = audioEngine;
		this.voiceManager = voiceManager;

		if (!Platforms.IS_MAC || NaturalSpeechPlugin._SIMULATE_NO_TTS) {
			this.avSynthesizer = null;
			return;
		}
		else {
			this.avSynthesizer = AVSpeechSynthesizer.alloc();
			log.info("Allocated AVSpeechSynthesizer@{}", avSynthesizer);
			// we retain until this-object GC
			NSAutoRelease.register(this, avSynthesizer);
		}

		// array to arraylist
		ID[] nativeVoices = AVSpeechSynthesisVoice.getSpeechVoices();
		for (ID voice : nativeVoices) {
			String name = StringUtils.stripAccents(AVSpeechSynthesisVoice.getName(voice))
				.toLowerCase()
				.replace(" ", "");
			VoiceID voiceID = new VoiceID(MACOS_MODEL_NAME, name);
			this.nativeVoices.put(voiceID, voice);
		}

	}

	@Override
	public @NonNull SpeakStatus speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName) {
		if (!nativeVoices.containsKey(voiceID)) {
			return SpeakStatus.REJECT;
		}

		ID nativeVoice = nativeVoices.get(voiceID);
		ID utterance = AVSpeechUtterance.getSpeechUtteranceWithString(text);
		AVSpeechUtterance.setVoice(utterance, nativeVoice);

		Block bufferBlock = Block.alloc(new BufferPlaybackCallback(gainSupplier, lineName));
		AVSpeechSynthesizer.writeUtteranceToBufferCallback(avSynthesizer, utterance, bufferBlock);
		Block.release(bufferBlock);

		return SpeakStatus.ACCEPT;
	}

	@Override
	public ListenableFuture<StartResult> start() {
		if (!Platforms.IS_MAC) {
			log.trace("Not MacOS, skipping");
			return Futures.immediateFuture(StartResult.NOT_INSTALLED);
		}

		nativeVoices.forEach((voiceID, nativeVoice) -> {
			Gender gender = Gender.fromAVGender(AVSpeechSynthesisVoice.getGender(nativeVoice));
			voiceManager.register(voiceID, gender);
		});

		started = true;

		return Futures.immediateFuture(StartResult.SUCCESS);
	}

	@Override
	public void stop() {
		started = false;

		for (VoiceID voiceID : nativeVoices.keySet()) {
			voiceManager.unregister(voiceID);
		}
	}

	@Override
	public boolean contains(VoiceID voiceID) {
		return nativeVoices.containsKey(voiceID);
	}

	@Override
	public void silence(Predicate<String> lineCondition) {
		audioEngine.closeConditional(lineCondition);
	}

	@Override
	public void silenceAll() {
		audioEngine.closeAll();
	}

	@Override
	public @NonNull EngineType getEngineType() {
		return EngineType.BUILTIN_OS;
	}

	@Override
	public @NonNull String getEngineName() {
		return "mac";
	}

	private class BufferPlaybackCallback implements AVSpeechSynthesizerBufferCallback {

		private AudioFormat detectedFormat = null;

		private final String lineName;
		private final Supplier<Float> gainSupplier;

		private final Vector<short[]> int16AudioSegments;

		public BufferPlaybackCallback(Supplier<Float> gainSupplier, String lineName) {
			this.lineName = lineName;
			this.gainSupplier = gainSupplier;
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
					AudioInputStream stream = new AudioInputStream(
						new ByteArrayInputStream(audioData), detectedFormat, audioData.length
					);

					audioEngine.play(lineName, stream, gainSupplier);
				}
			} catch (Exception e) {
				log.error("ErrorResult in buffer callback", e);
			}

		}
	}
}
