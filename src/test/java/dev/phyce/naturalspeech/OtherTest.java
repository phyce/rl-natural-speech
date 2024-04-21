package dev.phyce.naturalspeech;

public class OtherTest {

	private static String[] exampleSentences = new String[] {
		"Hello",
		"Hello, World.",
		"Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello, world.",
		"Hello, world world world world world world world world world world.",
		"Hello Hello Hello Hello Hello Hello, world world world world world.",

		"Hello Hello Hello Hello Hello Hello Hello Hello, world world world world world world.",
		"Hello, world world world world world world world world world world world world world.",
		"Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello, world.",

		"Hello,NaturalSpeech NaturalSpeech NaturalSpeech NaturalSpeech, world.",
		"Hello,NaturalSpeech NaturalSpeech NaturalSpeech NaturalSpeech NaturalSpeech, world.",

		// Smithing Guide in Varrock
		"As you get better you'll find you will be able to smith Mithril and eventually Adamantite and even Runite.This can be very lucrative but very expensive on the coal front.",
		"It may be worth you stockpiling coal for a while before attempting these difficult metals or even sticking to good old reliable iron by the bucket load.",
		"If you want to stop yourself from failing to smith iron, I suggest purchasing some rings of forging. Be aware that they will break after a certain number of bars are smelted.",

		// Lumbridge Guide
		"Greetings, adventurer. I am Phileas, the Lumbridge Guide. I am here to give information and directions to new players. Is there anything I can help you with?",
		"You're strong enough to work out what monsters to fight for yourself now, but the combat tutors might help you with any questions you have about the skills;they're just over there to the south of the general store.",

		// Woodcutting Guide
		"This is a Skillcape of Woodcutting, wearing one increases your chance of finding bird's nests. Only a person who has achieved the highest possible level in a skill can wear one.",
	};






	//	@Test
	//	public void testHashDistribution() {
	//		Random random = new Random();
	//		// get 10 length long byte array
	//		byte[] bytes = new byte[20];
	//
	//		final int iterations = 50_000;
	//
	//		int[] buckets = new int[iterations];
	//
	//		for (int i = 0; i < iterations; ++i) {
	//			random.nextBytes(bytes);
	//			String username = new String(bytes, StandardCharsets.US_ASCII);
	//			++buckets[Math.abs(username.hashCode()) % iterations];
	//		}
	//
	//
	//		// remap into buckets of 1000
	//		int[] buckets_1000 = new int[iterations / 1000];
	//		for (int i = 0; i < buckets.length; i++) {
	//			buckets_1000[i / 1000] += buckets[i];
	//		}
	//
	//		StringBuilder builder = new StringBuilder();
	//		for (int i = 0; i < buckets_1000.length; i++) {
	//			int bucket = buckets_1000[i];
	//			if (bucket == 0) continue;
	//			builder.append(i);
	//			builder.append(',');
	//			builder.append(bucket);
	//			builder.append(',');
	//			builder.append('\n');
	//		}
	//
	//		System.out.println(builder);
	//	}
	//
	//	@Test
	//	public void testHashOddEvenDistribution() {
	//		Random random = new Random();
	//		// get 10 length long byte array
	//		byte[] bytes = new byte[20];
	//
	//		final int iterations = 50_000;
	//
	//		int[] buckets = new int[iterations];
	//
	//		for (int i = 0; i < iterations; ++i) {
	//			random.nextBytes(bytes);
	//			String username = new String(bytes, StandardCharsets.US_ASCII);
	//			++buckets[Math.abs(username.hashCode()) % iterations];
	//		}
	//
	//		int odd = 0;
	//		int even = 0;
	//		for (int i = 0; i < buckets.length; i++) {
	//			if (i % 2 == 0) {
	//				even += buckets[i];
	//			} else {
	//				odd += buckets[i];
	//			}
	//		}
	//
	//		System.out.println("Even: " + even);
	//		System.out.println("Odd: " + odd);
	//	}

	//	@Test
	//	public void testSplitSentenceNew() {
	//		for (String exampleSentence : exampleSentences) {
	//			List<String> segments = TextUtil.splitSentenceV2(exampleSentence);
	//			String str = segments.stream().map(s -> "[" + s + "]").reduce("", (a, b) ->  a + b);
	//			System.out.print(segments.size());
	//			System.out.println(str);
	//		}
	//	}
	//
	//	@Test
	//	public void testAudioEngine() throws InterruptedException {
	//		AudioEngine engine = new AudioEngine();
	//		File file = Path.of(System.getProperty("user.home"), "test.wav").toFile();
	//		if (!file.exists()) {
	//			System.err.println("No test wav file found");
	//			return;
	//		}
	//		long startTime = System.currentTimeMillis();
	//
	//		var updater = new Thread(() -> {
	//			while ((System.currentTimeMillis() - startTime) <= 6000) {
	//				engine.update();
	//			}
	//			System.out.println("Stopped updating");
	//		});
	//		updater.start();
	//
	//		for (int i = 0; i < 2; i++) {
	//			try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
	//				System.out.println("Playing");
	//				engine.play("testing", stream, () -> {
	//					float t = (System.currentTimeMillis() - startTime);
	//					float v = (float) (1 - (t / 6000)) * -20;
	//					if (v > 6) v = 6;
	//					return v;
	//				});
	//			} catch (UnsupportedAudioFileException | IOException e) {
	//				throw new RuntimeException(e);
	//			}
	//		}
	//
	//		System.out.println("Joining");
	//		updater.join();
	//
	//	}
	//
	//	@Test
	//	public void testAudio() throws LineUnavailableException {
	//		AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
	//			22050.0F, // Sample Rate (per second)
	//			16, // Sample Size (bits)
	//			1, // Channels
	//			2, // Frame Size (bytes)
	//			22050.0F, // Frame Rate (same as sample rate because PCM is 1 sample per 1 frame)
	//			false); // Little Endian
	//		var mixer = AudioSystem.getMixer(null);
	//		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
	//		int maxLine = mixer.getMaxLines(info);
	//		int testWidth = 40;
	//		long startTime = System.currentTimeMillis();
	//		int testCount = 1;
	//		for (int i = 0; i < testCount; i++) {
	//			DynamicLine line = new DynamicLine((SourceDataLine) mixer.getLine(info));
	//			System.out.println(Arrays.toString(line.getControls()));
	//			System.out.println(line.isControlSupported(FloatControl.Type.MASTER_GAIN));
	//			line.open();
	//			System.out.println(Arrays.toString(line.getControls()));
	//			System.out.println(line.isControlSupported(FloatControl.Type.MASTER_GAIN));
	////			System.out.print("0");
	////			if (i % testWidth == 0) {
	////				System.out.println();
	////			}
	////			System.out.flush();
	//		}
	//		long endTime = System.currentTimeMillis();
	//
	//
	//		System.out.println("DONE - open costs: " + ((endTime - startTime)) / testCount  + "ms");
	//	}
}
