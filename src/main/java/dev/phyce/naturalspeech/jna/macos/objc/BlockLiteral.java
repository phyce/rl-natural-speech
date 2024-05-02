package dev.phyce.naturalspeech.jna.macos.objc;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import lombok.AllArgsConstructor;

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
 *
 * @note Objective-C is a two-part system built-upon C. <br>
 * <ol>
 * <li><b>Clang code-generation</b>
 *   <p>
 *       Clang code-generates Objective-C code into C code, which assumes the existence of an Objective-C runtime.
 *       (metaphorically, in reality into LLVM IR and then into machine code)
 *   </p>
 * </li>
 * <li><b>Objective-C runtime</b>
 *   <p>
 *     The Objective-C runtime lives in libobjc.dylib, placed in the OS file system and dynamically links
 *     to executables. The runtime library implements features common in interpreted languages, such as
 *     dynamic method resolution, introspection, runtime class creation, closures etc.
 *   </p>
 *   <p>
 *     In a way, Objective-C and Java are quite similar. The Java runtime is the JVM, and the Objective-C runtime is libobjc.dylib.
 *   </p>
 * </li>
 * </ol>
 *
 * <p>
 *     For blocks, clang code-generates the Obj-C syntax {@code ^{ ... }} into C-style
 *     structs conforming to the block ABI. The inner code turns into a global function definition
 *     and captured variables are imported into the struct definition as const literal members.
 *     If the captured variable is attributed with {@code __block}, another "byref" struct is created for the variable, which manages the variable.
 * </p>
 * <br>
 * <p>
 *     Our JNA struct directly implements this c-style block struct.
 *     <br>
 * </p>
 *
 * <p> Why is block implementation needed? Because Objective-C APIs use blocks extensively.
 * </p>
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

	public static final int SIZE_OF = Native.getNativeSize(BlockLiteral.class);

	// Global block descriptor, never changes, never needs to be freed
	private static final BlockDescriptor GLOBAL_BLOCK_DESCRIPTOR =
		new BlockDescriptor (
			new NativeLong(0), // no reserved
			new NativeLong(BlockLiteral.SIZE_OF), // we never import any variables into the block, so the size is always the same
			null, // no copy helper
			null, // no dispose helper
			null // no type signature required
		);

	/**
	 * Allocates a new global block with the given function pointer (memory auto-managed)
	 *
	 * @param invoke The function pointer to the block invoke function
	 *
	 * @return A new global block literal
	 *
	 * @note As opposed to stack blocks, or heap blocks, global blocks are never copied or disposed
	 * This is the accurate block type for JNA callbacks because the block lifetime is managed by us.
	 * We never want the block to be copied or disposed by the runtime.
	 *
	 * @memory
	 * <ul>
	 *     <li><b>Must ensure native objects used within the callback have lifetimes >= block lifetime.</b></li>
	 * 	   <li>The native memory is managed by JNA and auto free-ed when the structure is garbage collected.</li>
	 *     <li>The caller must ensure strong reference to the BlockLiteral until the block is no longer registered.</li>
	 * </ul>
	 */
	public BlockLiteral(Callback invoke) {
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
		public static final int BLOCK_IS_NOESCAPE      =  (1 << 23);

		public static final int BLOCK_HAS_COPY_DISPOSE =  (1 << 25);
		public static final int BLOCK_HAS_CTOR =          (1 << 26); // helpers have C++ code
		public static final int BLOCK_IS_GLOBAL =         (1 << 28);
		public static final int BLOCK_HAS_STRET =         (1 << 29); // IFF BLOCK_HAS_SIGNATURE
		public static final int BLOCK_HAS_SIGNATURE =     (1 << 30);

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
