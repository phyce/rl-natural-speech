package dev.phyce.naturalspeech.texttospeech.engine.macos.objc;

public final class BlockFlags {
	public static final int BLOCK_REFCOUNT_MASK = (0xffff);
	public static final int BLOCK_NEEDS_FREE = (1 << 24);
	public static final int BLOCK_HAS_COPY_DISPOSE = (1 << 25);
	public static final int BLOCK_HAS_CTOR = (1 << 26);
	public static final int BLOCK_IS_GC = (1 << 27);
	public static final int BLOCK_IS_GLOBAL = (1 << 28);
	public static final int BLOCK_HAS_DESCRIPTOR = (1 << 29);
}
