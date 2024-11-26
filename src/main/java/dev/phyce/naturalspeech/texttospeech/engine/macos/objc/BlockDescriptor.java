package dev.phyce.naturalspeech.texttospeech.engine.macos.objc;

import com.sun.jna.Callback;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import lombok.NonNull;

/**
 * <pre> {@code
 * struct Block_descriptor {
 *     unsigned long int reserved;
 *     unsigned long int size;
 *     // void* will point to the specific block literal struct
 *     void (*copy)(void *dst, void *src);
 *     void (*dispose)(void *);
 * };
 * }</pre>
 */
@Structure.FieldOrder({"reserved", "size", "copy", "dispose"})
public class BlockDescriptor extends Structure implements Structure.ByReference {
	public NativeLong reserved;
	public NativeLong size;
	public BlockCopyHelper copy;
	public BlockDisposeHelper dispose;

	private BlockDescriptor(@NonNull Pointer memory) {
		super(memory);
		read();
	}

	public BlockDescriptor(
		@NonNull NativeLong reserved,
		@NonNull NativeLong size,
		@NonNull BlockCopyHelper copy,
		@NonNull BlockDisposeHelper dispose
	) {
		super();
		this.reserved = reserved;
		this.size = size;
		this.copy = copy;
		this.dispose = dispose;

		write();
	}

	public static BlockDescriptor cast(@NonNull Pointer memory) {
		return new BlockDescriptor(memory);
	}

	public interface BlockCopyHelper extends Callback {
		void callback(Pointer dst, Pointer src);
	}

	public interface BlockDisposeHelper extends Callback {
		void callback(Pointer block);
	}
}
