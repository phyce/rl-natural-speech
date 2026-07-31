package dev.phyce.naturalspeech.utils;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TextUtilTest {

	@Test
	public void stripChatTags_removesColourTags() {
		assertEquals(
			"You no sword to hand over. Kovac give commission already. Go make me heavy spiked sword.",
			TextUtil.stripChatTags(
				"You no sword to hand over. Kovac give commission already. " +
					"Go make me <col=ef1020>heavy spiked</col> sword.")
		);
	}

	@Test
	public void stripChatTags_removesTwitchColNormalPrefix() {
		assertEquals("hello from twitch", TextUtil.stripChatTags("<colNORMAL>hello from twitch"));
	}

	@Test
	public void stripChatTags_removesImageTags() {
		assertEquals("nice drop", TextUtil.stripChatTags("<img=2>nice drop"));
	}

	@Test
	public void stripChatTags_keepsTypedAngleBrackets() {
		assertEquals("<3", TextUtil.stripChatTags("<lt>3"));
		assertEquals("<col=ff0000>", TextUtil.stripChatTags("<lt>col=ff0000<gt>"));
	}

	@Test
	public void stripChatTags_keepsTypedAngleBracketsWhileStrippingRealTags() {
		assertEquals("<3", TextUtil.stripChatTags("<col=ef1020><lt>3</col>"));
	}

	@Test
	public void stripChatTags_leavesPlainTextAlone() {
		assertEquals("just a normal message", TextUtil.stripChatTags("just a normal message"));
	}
}
