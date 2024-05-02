package dev.phyce.naturalspeech;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.platform.mac.CoreFoundation;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.jna.macos.avfaudio.AVAudioBuffer;
import dev.phyce.naturalspeech.jna.macos.avfaudio.AVAudioCommonFormat;
import dev.phyce.naturalspeech.jna.macos.avfaudio.AVAudioFormat;
import dev.phyce.naturalspeech.jna.macos.avfaudio.AVAudioPCMBuffer;
import dev.phyce.naturalspeech.jna.macos.avfoundation.AVSpeechSynthesisVoice;
import dev.phyce.naturalspeech.jna.macos.avfoundation.AVSpeechSynthesisVoiceGender;
import dev.phyce.naturalspeech.jna.macos.avfoundation.AVSpeechSynthesizer;
import dev.phyce.naturalspeech.jna.macos.avfoundation.AVSpeechSynthesizerBufferCallback;
import dev.phyce.naturalspeech.jna.macos.avfoundation.AVSpeechUtterance;
import dev.phyce.naturalspeech.jna.macos.javautil.AutoRelease;
import dev.phyce.naturalspeech.jna.macos.objc.BlockLiteral;
import dev.phyce.naturalspeech.jna.macos.objc.ID;
import dev.phyce.naturalspeech.jna.macos.foundation.NSObject;
import dev.phyce.naturalspeech.jna.macos.foundation.NSString;
import dev.phyce.naturalspeech.jna.macos.objc.LibObjC;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Vector;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import org.junit.Test;

public class MacTest {

	@Test
	public void testJNA() {
		ID hello = NSString.alloc("Hello, ");

		// ARC testing
		NSObject.retain(hello);
		System.out.println("hello ref count: " + NSObject.getRetainCount(hello));
		NSObject.release(hello);
		System.out.println("hello ref count: " + NSObject.getRetainCount(hello));
		// if we release again, it will be deallocated, and SEGFAULT will occur when we try to access it
		// NSObject.release(hello);
		// NSObject.getRetainCount(hello); <- SEGFAULT

		// Modify the NSString
		ID world = NSString.alloc("World!");
		ID helloworld = NSString.allocStringByAppendingString(hello, world);

		// Read the string
		System.out.println("String: " + NSString.getJavaString(helloworld));

		// Release the NSString
		NSObject.release(hello);
		NSObject.release(world);
		NSObject.release(helloworld);
	}

	@Test
	public void testAVSpeechSynthesisVoice() {
		ID[] voices = AVSpeechSynthesisVoice.getSpeechVoices();
		Arrays.sort(voices, Comparator.comparing(AVSpeechSynthesisVoice::getName));
		for (ID voice : voices) {
			String name = AVSpeechSynthesisVoice.getName(voice);
			AVSpeechSynthesisVoiceGender gender = AVSpeechSynthesisVoice.getGender(voice);
			String identifier = AVSpeechSynthesisVoice.getIdentifier(voice);
			String language = AVSpeechSynthesisVoice.getLanguage(voice);
			System.out.printf("%-10s %-10s %-10s %50s\n", name, language, gender, identifier);
		}
	}

	@Test
	public void testAVSpeechUtterance() {
		ID utterance = AVSpeechUtterance.allocSpeechUtteranceWithString("Hello, Natural Speech!");
		AutoRelease.register(utterance);
		ID voice = AVSpeechUtterance.getVoice(utterance);
		ID speechString = AVSpeechUtterance.getSpeechString(utterance);
		String voiceName = AVSpeechSynthesisVoice.getName(voice);

		System.out.println("Voice: " + voiceName);
		System.out.println("Speech: " + NSString.getJavaString(speechString));

		ID synthesizer = AVSpeechSynthesizer.alloc();
		AutoRelease.register(synthesizer);

		AVSpeechSynthesizer.speakUtterance(synthesizer, utterance);
		try {
			Thread.sleep(2500L);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testAVSpeechUtteranceCallback() {

		AudioEngine audioEngine = new AudioEngine();

		final AudioFormat AUDIO_FORMAT =
			new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
				22050.0F, // Sample Rate (per second)
				16, // Sample Size (bits)
				1, // Channels
				2, // Frame Size (bytes)
				22050.0F, // Frame Rate (same as sample rate because PCM is 1 sample per 1 frame)
				false
			);

		Vector<byte[]> audioData = new Vector<>();

		ID utterance = AVSpeechUtterance.allocSpeechUtteranceWithString("Hello Natural Speech!");
		AutoRelease.register(utterance);
		ID voice = AVSpeechUtterance.getVoice(utterance);
		ID speechString = AVSpeechUtterance.getSpeechString(utterance);
		String voiceName = AVSpeechSynthesisVoice.getName(voice);

		System.out.println("Voice: " + voiceName);
		System.out.println("Speech: " + NSString.getJavaString(speechString));

		ID synthesizer = AVSpeechSynthesizer.alloc();
		AutoRelease.register(synthesizer);


		AVSpeechSynthesizerBufferCallback invoke = new AVSpeechSynthesizerBufferCallback() {

			Vector<byte[]> audioData = new Vector<>();

			@Override
			public void invoke(Pointer block, Pointer pAVAudioBuffer) {
				ID avAudioBuffer = new ID(pAVAudioBuffer);

				if (!LibObjC.object_getClass(avAudioBuffer).equals(AVAudioPCMBuffer.idClass)) {
					System.err.println("AVAudioBuffer is not a AVAudioPCMBuffer");
					return;
				}
				long frameLength = AVAudioPCMBuffer.getFrameLength(avAudioBuffer);
				long frameCapacity = AVAudioPCMBuffer.getFrameCapacity(avAudioBuffer);
				long stride = AVAudioPCMBuffer.getStride(avAudioBuffer);

				if (frameLength == 0) {
					// done

					byte[] byteArray = audioData.stream().reduce(new byte[0], (a, b) -> {
						byte[] result = new byte[a.length + b.length];
						System.arraycopy(a, 0, result, 0, a.length);
						System.arraycopy(b, 0, result, a.length, b.length);
						return result;
					});

					AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(byteArray), AUDIO_FORMAT, byteArray.length);
					audioEngine.play(voiceName, stream, () -> 0f);
					return;
				}

				ID avFormat = AVAudioBuffer.getFormat(avAudioBuffer);

				AVAudioCommonFormat format = AVAudioFormat.getCommonFormat(avFormat);
				boolean standard = AVAudioFormat.getIsStandard(avFormat);
				double sampleRate = AVAudioFormat.getSampleRate(avFormat);
				int channelCount = AVAudioFormat.getChannelCount(avFormat);
				System.out.printf("AVAudioFormat: format(%s) standard(%s) sampleRate(%f) channelCount(%s)\n", format, standard, sampleRate, channelCount);

				// Get the buffer
				Pointer int16ChannelData = AVAudioPCMBuffer.getInt16ChannelData(avAudioBuffer).getPointer(0);

				System.out.printf("Frame Length: %d, Frame Capacity: %d, Stride: %d\n", frameLength, frameCapacity, stride);

				byte[] byteArray = int16ChannelData.getByteArray(0L, (int) frameLength * 2);
				System.out.println("Int16 Channel Data: " + new String(byteArray, StandardCharsets.UTF_16LE));

				audioData.add(byteArray);
			}
		};

		BlockLiteral bufferBlock = new BlockLiteral(invoke);

		AVSpeechSynthesizer.writeUtteranceToBufferCallback(synthesizer, utterance, bufferBlock);

		try {
			Thread.sleep(2500L);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testAutoReleaseSEGFAULT() {
		WeakReference<Pair> helloWeak = testAutoReleaseSUGFAULT_aux();

		//noinspection DataFlowIssue
		ID helloID = helloWeak.get().id;

		while (helloWeak.get() != null) {
			System.gc();
		}

		System.out.printf("The java object has been garbage collected, ID(%s) should be released\n", helloID);

		// This will cause a SEGFAULT
		System.out.println("ASSERTING SEGFAULT\n--------------------");
		System.out.println("YOU SHOULD NEVER SEE THIS PRINT -> " + NSObject.getRetainCount(helloID));
	}

	public static class Pair {
		public final Object obj;
		public final ID id;

		public Pair(Object obj, ID id) {
			this.obj = obj;
			this.id = id;
		}
	}

	@SuppressWarnings("StringOperationCanBeSimplified")
	public WeakReference<Pair> testAutoReleaseSUGFAULT_aux() {

		/*
		 Important for this test to succeed:

		 // Do not test with:
		 String hello = "Hello, ";

		 because literal strings are interned into a string pool,
		 they will not be garbage collected until the JVM exits

		 see String#intern() for more information
		 */

		String hello = new String("Hello, ");
		String world = new String("World!");

		ID helloID = AutoRelease.register(hello, NSString.alloc(hello)).getId();
		ID worldID = AutoRelease.register(world, NSString.alloc(world)).getId();
		ID helloworldID = NSString.allocStringByAppendingString(helloID, worldID);
		AutoRelease.register(NSString.getJavaString(helloworldID), helloworldID);

		// This should not cause a SEGFAULT
		System.out.println("CHECKING RETAIN COUNT, ASSERT 1 -> " + NSObject.getRetainCount(helloID));

		return new WeakReference<>(new Pair(hello, helloID));

	}

	@Test
	public void testAutoReleaseSEGFAULT2() {
		WeakReference<Pair> weakRefs = testAutoReleaseSEGFAULT2_AUX();

		//noinspection DataFlowIssue
		long idValue = weakRefs.get().id.getLong(0);

		while (weakRefs.get() != null) {
			System.gc();
		}

		System.out.printf("The ID object has been garbage collected, ID(%s) should be released\n", idValue);

		// This will cause a SEGFAULT
		System.out.println("ASSERTING SEGFAULT\n--------------------");
		System.out.println("YOU SHOULD NEVER SEE THIS PRINT -> " + NSObject.getRetainCount(new ID(idValue)));
	}

	@SuppressWarnings("StringOperationCanBeSimplified")
	private WeakReference<Pair> testAutoReleaseSEGFAULT2_AUX() {
		String hello = new String("Hello, ");
		String world = new String("World!");

		ID helloID = AutoRelease.register(NSString.alloc(hello));
		ID worldID = AutoRelease.register(NSString.alloc(world));
		ID helloworldID = AutoRelease.register(NSString.allocStringByAppendingString(helloID, worldID));

		String helloworld = NSString.getJavaString(helloworldID);

		// This should not cause a SEGFAULT
		System.out.println("ASSERT 1 -> " + NSObject.getRetainCount(helloworldID));

		return new WeakReference<>(new Pair(helloworld, helloworldID));
	}

}
