package dev.phyce.naturalspeech.jna.macos.objc;

import com.sun.jna.Pointer;

/**
 * Represents a direct pointer to SEL object
 *
 */
public class SEL extends ID {

	public SEL() {
		super();
	}

	public SEL(long peer) {
		super(peer);
	}

	public SEL(Pointer peer) {
		super(peer);
	}
}
