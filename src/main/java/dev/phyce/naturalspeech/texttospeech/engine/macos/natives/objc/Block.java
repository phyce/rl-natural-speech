package dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc;

import static com.google.common.base.Preconditions.checkState;
import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import static dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.BlockFlags.BLOCK_HAS_COPY_DISPOSE;
import static dev.phyce.naturalspeech.texttospeech.engine.macos.natives.objc.BlockFlags.BLOCK_NEEDS_FREE;
import java.util.Vector;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;


/**
 * <pre>{@code
 * struct Block_layout {
 *     void *isa;
 *     int flags;
 *     int reserved;
 *     void (*invoke)(void *, ...);
 *     struct Block_descriptor *descriptor;
 *     // Imported variables below...
 * };
 * }</pre>
 * Implements Objective-C block, using the underlying application-binary-interface.<br>
 * <a href="https://clang.llvm.org/docs/Block-ABI-Apple.html">Block-ABI-Apple</a>
 * <br>
 * Note:The LLVM specification doc for blocks contains severe typos/errs in examples<br>
 * <a href="https://github.com/llvm/llvm-project/issues/90341">My GitHub Issue</a><br>
 * <br><br>
 * An Obj-C block is synonymous with closure in other languages.
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
 * <p> Why? Because Objective-C APIs use blocks extensively, similar to how Java APIs uses java-closures.
 * </p>
 *
 * @see LibObjC LibObjC explains Objective-C runtime and libobjc.dylib in detail
 */
@Structure.FieldOrder({"isa", "flags", "reserved", "invoke", "descriptor"})
@Slf4j
public class Block extends Structure {

	public Pointer isa;
	public int flags;
	public int reserved;
	public Callback invoke;
	public Pointer descriptor;

	private static final int SIZE_OF = new Block().size();

	private static final BlockDescriptor BLOCK_DESCRIPTOR;

	// hold callback objects to prevent GC
	private static final Vector<Callback> BLOCK_CALLBACK_OBJECTS = new Vector<>();

	static {
		if (LibObjC.INSTANCE == null) {
			BLOCK_DESCRIPTOR = null;
		}
		else {
			BLOCK_DESCRIPTOR = new BlockDescriptor(
				new NativeLong(0, true),
				new NativeLong(SIZE_OF, true),
				// copy is only called when _NSConcreteStackBlock blocks are copied to heap with Block_copy
				// used to copy captured variables, impossible to happen in our case.
				(dst, src) -> log.error("Copying block: @{} -> @{}", dst, src),
				block -> {
					BLOCK_CALLBACK_OBJECTS.remove(Block.cast(block).invoke);
					log.debug("Disposing heap block@{}. BLOCK_CALLBACK_OBJECTS size:{}", block,
						BLOCK_CALLBACK_OBJECTS.size());
				}
			);
		}
	}

	// used to calculate size, don't use otherwise.
	@SuppressWarnings("DeprecatedIsStillUsed")
	@Deprecated
	private Block() {}

	private Block(@NonNull Pointer memory) {
		super(memory);
		this.useMemory(memory);
		read();
	}

	/**
	 * Allocates a new heap block with the given function pointer
	 *
	 * @param invoke The function pointer to the block invoke function
	 * @memory ref-counted and Native::freed by the Objective-C Block Runtime, does not need manual free.
	 * Use {@link #retain(Block)} and {@link #release(Block)} to manage the block's lifecycle.
	 */
	private Block(@NonNull Callback invoke) {
		// Objective-C runtime ref-counts blocks, and free them when they are no longer needed.
		// monitor BLOCK_DESCRIPTOR::dispose to see when the block is disposed
		super(new Pointer(Native.malloc(SIZE_OF)));

		checkState(LibObjC.INSTANCE != null, "Objective-C runtime is not available");
		this.isa = LibObjC.INSTANCE._NSConcreteMallocBlock;
		this.flags = BLOCK_NEEDS_FREE | BLOCK_HAS_COPY_DISPOSE;
		this.reserved = 0;
		this.invoke = invoke;
		this.descriptor = BLOCK_DESCRIPTOR.getPointer();

		BLOCK_CALLBACK_OBJECTS.add(invoke);

		write();
	}

	public static Block alloc(@NonNull Callback invoke) {
		Block heapBlock = new Block(invoke);
		log.debug("Allocating new heap block@{}", heapBlock.getPointer());

		// retain the block, caller must release
		retain(heapBlock);

		return heapBlock;
	}

	public static void retain(@NonNull Block block) {
		// Block_copy for heap blocks will increment the ref-count
		// It is called Block_copy because generally it's used to copy stack blocks to heap
		LibObjC.Block_copy(block);
	}

	// utility to remind you to release the block using intellisense
	public static void release(@NonNull Block block) {
		LibObjC.Block_release(block);
	}

	public static Block cast(@NonNull Pointer pointer) {
		return new Block(pointer);
	}


}
