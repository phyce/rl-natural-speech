package dev.phyce.naturalspeech.texttospeech.engine.macos.objc;

import com.sun.jna.Pointer;
import lombok.Getter;

/**
 * Represents an Objective-C `id` type.<br>
 *
 * {@code typedef struct objc_object *id;}
 *
 * This is a direct pointer to an NSObject.
 *
 * @see <a href="https://developer.apple.com/documentation/objectivec/id?language=objc">obj-c id</a>

 */
@SuppressWarnings("unused")
public class ID extends Pointer {

	/**
	 * Check NIL with {@link #equals(Object)} or {@link #isNil()}, not `==`
	 */
	@Getter
	private static final ID NIL = new ID(0L);

	public ID(long peer) {
		super(peer);
	}

	public ID(Pointer peer) {
		super(Pointer.nativeValue(peer));
	}

	public boolean isNil() {
		return equals(NIL);
	}
}