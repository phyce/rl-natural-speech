package dev.phyce.naturalspeech;

import dev.phyce.naturalspeech.utils.TextUtil;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

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

		// Lumbridge Guide
		"Greetings, adventurer. I am Phileas, the Lumbridge Guide. I am here to give information and directions to new players. Is there anything I can help you with?",
		"You're strong enough to work out what monsters to fight for yourself now, but the combat tutors might help you with any questions you have about the skills;they're just over there to the south of the general store.",

		// Woodcutting Guide
		"This is a Skillcape of Woodcutting, wearing one increases your chance of finding bird's nests. Only a person who has achieved the highest possible level in a skill can wear one.",
	};

	@Test
	public void testSplitSentence() {
		for (String exampleSentence : exampleSentences) {
			List<String> segments = TextUtil.splitSentence(exampleSentence);
			String str = segments.stream().map(s -> "[" + s + "]").reduce("", (a, b) ->  a + b);
			System.out.print(segments.size());
			System.out.println(str);
		}
	}
}
