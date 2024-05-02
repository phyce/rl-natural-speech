package dev.phyce.naturalspeech.jna.macos.objc;

import static com.google.common.base.Preconditions.checkState;
import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import lombok.AllArgsConstructor;
import lombok.NonNull;

/*
struct Block_literal_1 {
    void *isa; // initialized to &_NSConcreteStackBlock or &_NSConcreteGlobalBlock
    int flags;
    int reserved;
    R (*invoke)(struct Block_literal_1 *, P...);
    struct Block_descriptor_1 {
        unsigned long int reserved;     // NULL
        unsigned long int size;         // sizeof(struct Block_literal_1)
        // optional helper functions
        void (*copy_helper)(void *dst, void *src);     // IFF (1<<25)
        void (*dispose_helper)(void *src);             // IFF (1<<25)
        // required ABI.2010.3.16
        const char *signature;                         // IFF (1<<30)
    } *descriptor;
    // imported variables
};
*/

/**
 * Implements Objective-C block, using the underlying application-binary-interface.<br>
 * <a href="https://clang.llvm.org/docs/Block-ABI-Apple.html">Block-ABI-Apple</a>
 * <br>
 * Note:The LLVM specification doc for blocks contains severe typos/errs in examples<br>
 * <a href="https://github.com/llvm/llvm-project/issues/90341">My GitHub Issue</a><br>
 * <br><br>
 * An Obj-C block is synonymous with closure/lambda in other languages.
 * It is a self-contained unit of code that can be passed around and executed.
 * <br><br>
 * <p>
 * For blocks, clang code-generates the Obj-C syntax {@code ^{ ... }} into C-style
 * structs conforming to the block ABI. The inner code turns into a global function definition
 * and captured variables are imported into the struct definition as const literal members.
 * If the captured variable is attributed with {@code __block}, another "byref" struct is created for the variable, which manages the variable.
 * </p>
 * <p>
 * Our JNA struct manually implements this c-style block struct,
 * since we don't have the benefit of clang code-generation.
 * </p>
 * <p> Why? Because Objective-C APIs use blocks extensively.
 * </p>
 *
 * @see LibObjC LibObjC explains Objective-C runtime and libobjc.dylib in detail
 */
@Structure.FieldOrder({"isa", "flags", "reserved", "invoke", "descriptor"})
public class BlockLiteral extends Structure {

	public Pointer isa;
	public int flags;
	public int reserved;
	public Callback invoke;
	public Pointer descriptor;
	// imported variables ... we never import any variables into the block we don't need to define them

	/* btw, JNA Structures ignore static and final fields */

	public static final int SIZE_OF;

	// Global block descriptor, never changes, never needs to be freed
	private static final BlockDescriptor GLOBAL_BLOCK_DESCRIPTOR;

	static {
		if (LibObjC.INSTANCE == null) {
			SIZE_OF = 0;
			GLOBAL_BLOCK_DESCRIPTOR = null;
		}
		else {
			SIZE_OF = Native.getNativeSize(BlockLiteral.class);
			GLOBAL_BLOCK_DESCRIPTOR = new BlockDescriptor(
				new NativeLong(0), // no reserved
				new NativeLong(BlockLiteral.SIZE_OF),
				// we never import any variables into the block, so the size is always the same
				null, // no copy helper
				null, // no dispose helper
				null // no type signature required
			);
		}
	}

	/**
	 * Allocates a new global block with the given function pointer (memory auto-managed)
	 *
	 * @param invoke The function pointer to the block invoke function
	 */
	public BlockLiteral(@NonNull Callback invoke) {
		checkState(LibObjC.INSTANCE != null, "Objective-C runtime is not available");

		this.isa = LibObjC.INSTANCE._NSConcreteGlobalBlock; // global block
		this.flags = BlockFlags.BLOCK_IS_GLOBAL; // global block
		this.reserved = 0; // no reserved
		this.invoke = invoke; // the function pointer
		this.descriptor = GLOBAL_BLOCK_DESCRIPTOR.getPointer();// global block descriptor

		this.ensureAllocated();
	}


	public static final class BlockFlags {
		// Set to true on blocks that have captures (and thus are not true
		// global blocks) but are known not to escape for various other
		// reasons. For backward compatibility with old runtimes, whenever
		// BLOCK_IS_NOESCAPE is set, BLOCK_IS_GLOBAL is set too. Copying a
		// non-escaping block returns the original block and releasing such a
		// block is a no-op, which is exactly how global blocks are handled.
		public static final int BLOCK_IS_NOESCAPE = (1 << 23);

		public static final int BLOCK_HAS_COPY_DISPOSE = (1 << 25);
		public static final int BLOCK_HAS_CTOR = (1 << 26); // helpers have C++ code
		public static final int BLOCK_IS_GLOBAL = (1 << 28);
		public static final int BLOCK_HAS_STRET = (1 << 29); // IFF BLOCK_HAS_SIGNATURE
		public static final int BLOCK_HAS_SIGNATURE = (1 << 30);

	}

	@AllArgsConstructor
	@FieldOrder({"reserved", "size", "copy_helper", "dispose_helper", "signature"})
	public static class BlockDescriptor extends Structure {
		public NativeLong reserved;
		public NativeLong size;
		public Callback copy_helper;
		public Callback dispose_helper;
		public String signature;

		public static final int SIZE_OF = Native.getNativeSize(BlockDescriptor.class);
	}
}
