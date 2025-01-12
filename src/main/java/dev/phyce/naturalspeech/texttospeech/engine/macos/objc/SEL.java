package dev.phyce.naturalspeech.texttospeech.engine.macos.objc;

import com.sun.jna.Pointer;

/**
 * Represents a direct pointer to SEL object
 * {@code typedef struct objc_selector *SEL;}
 */
public class SEL extends ID {

	public SEL(long peer) {
		super(peer);
	}

	public SEL(Pointer peer) {
		super(peer);
	}
}
