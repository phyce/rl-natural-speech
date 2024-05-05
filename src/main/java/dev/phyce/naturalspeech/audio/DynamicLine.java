package dev.phyce.naturalspeech.audio;

import static com.google.common.base.Preconditions.checkState;
import dev.phyce.naturalspeech.tts.VolumeManager;
import static dev.phyce.naturalspeech.utils.CommonUtil.silentInterruptHandler;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Control;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamicLine implements SourceDataLine {
	private final SourceDataLine sourceLine;
	private final ConcurrentLinkedQueue<byte[]> byteBuffer;

	// Vector is synchronized
	private final Vector<DynamicLineListener> dynamicLineListeners;
	private final Thread bufferFlusherThread;

	@Getter
	private Supplier<Float> gainSupplier = null;
	private FloatControl gainControl = null;

	/**
	 * Note, this is the user controlled master gain.
	 * Mixed with gainSupplier then provided to line master gain.
	 */
	@Setter
	private float masterGain = 0;

	public void setGainSupplier(Supplier<Float> gainSupplier) {
		this.gainSupplier = gainSupplier;
		update();
	}

	public enum DynamicLineEvent {
		BEGUN_BUFFERING,
		DONE_BUFFERING
	}

	public interface DynamicLineListener {
		void onEvent(DynamicLineEvent event);
	}

	public void addDynamicLineListener(DynamicLineListener listener) {
		dynamicLineListeners.add(listener);
	}

	public void removeDynamicLineListener(DynamicLineListener listener) {
		dynamicLineListeners.remove(listener);
	}

	private void triggerEvent(DynamicLineEvent event) {
		dynamicLineListeners.forEach(listener -> listener.onEvent(event));
	}

	public DynamicLine(SourceDataLine sourceLine) {
		this.sourceLine = sourceLine;

		byteBuffer = new ConcurrentLinkedQueue<>();
		dynamicLineListeners = new Vector<>();

		this.bufferFlusherThread = new Thread(this::bufferFlusher);
		this.bufferFlusherThread.setUncaughtExceptionHandler(silentInterruptHandler);
	}

	@SneakyThrows(InterruptedException.class)
	private void bufferFlusher() {
		boolean buffering = false;
		while (!bufferFlusherThread.isInterrupted()) {

			// until byteBuffer contains bytes
			while (byteBuffer.isEmpty()) {
				if (buffering) {
					log.trace("{} DONE BUFFERING EVENT", this);
					triggerEvent(DynamicLineEvent.DONE_BUFFERING);
					buffering = false;
				}
				synchronized (byteBuffer) { byteBuffer.wait(); }
			}

			if (!buffering) {
				log.trace("{} BEGUN BUFFERING EVENT", this);
				triggerEvent(DynamicLineEvent.BEGUN_BUFFERING);
				buffering = true;
			}

			byte[] b = byteBuffer.poll();
			if (b != null) {
				write(b, 0, b.length);
				drain();
			} else {
				log.warn("Found null byte array in buffer, somewhere buffer(null) was called.");
			}
		}
	}

	public void update() {
		if (gainSupplier == null) {
			return;
		}
		else if (!isOpen()) {
			return;
		}
		else if (!isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			return;
		}

		if (gainControl == null) {
			gainControl = (FloatControl) getControl(FloatControl.Type.MASTER_GAIN);
		}

		float suppliedGain = gainSupplier.get() ;
		float mixedGain = suppliedGain + masterGain;
		mixedGain = Math.min(6, Math.max(VolumeManager.NOISE_FLOOR, mixedGain));
		gainControl.setValue(mixedGain);
	}

	/**
	 * a non-blocking {@link #write}, behaves exactly the same. buffers append sequentially.<br>
	 * <br>
	 * Internally DynamicLine starts a thread on open and begins to write buffered bytes.
	 */
	public void buffer(byte[] audioBytes) {
		checkState(audioBytes.length % getFormat().getFrameSize() == 0,
			"Illegal write length. Must be a multiple of frame size.");
		byteBuffer.add(audioBytes);
		synchronized (byteBuffer) {byteBuffer.notify();}
	}

	@Override
	public void open(AudioFormat format, int bufferSize) throws LineUnavailableException {
		sourceLine.open(format, bufferSize);
		bufferFlusherThread.start();
	}

	@Override
	public void open(AudioFormat format) throws LineUnavailableException {
		sourceLine.open(format);
		bufferFlusherThread.start();
	}

	@Override
	public void open() throws LineUnavailableException {
		sourceLine.open();
		bufferFlusherThread.start();
	}

	@Override
	public void close() {
		sourceLine.close();
		bufferFlusherThread.interrupt();
		byteBuffer.clear();
	}

	// region: useless function wrappers

	/**
	 * This write will conflict and write over data from {@link #buffer(byte[])}.
	 * Part of Java API.
	 * @param b a byte array containing data to be written to the data line
	 * @param off the offset from the beginning of the array, in bytes
	 * @param len the length, in bytes, of the valid data in the array (in
	 *         other words, the requested amount of data to write, in bytes)
	 * @return
	 */
	@Override @Deprecated
	public int write(byte[] b, int off, int len) {
		return sourceLine.write(b, off, len);
	}

	@Override
	public void drain() {
		sourceLine.drain();
	}

	@Override
	public void flush() {
		sourceLine.flush();
	}

	/**
	 * Starts' behaviour is better described as play. Java API
	 */
	@Override
	public void start() {
		sourceLine.start();
	}

	/**
	 * Stops' behaviour is better described as pause. Java API
	 */
	@Override
	public void stop() {
		sourceLine.stop();
	}

	/**
	 * isRunning is better described as isPlaying.
	 * @return
	 */
	@Override
	public boolean isRunning() {
		return sourceLine.isRunning();
	}

	@Override
	public boolean isActive() {
		return sourceLine.isActive();
	}

	@Override
	public AudioFormat getFormat() {
		return sourceLine.getFormat();
	}

	/**
	 * This BufferSize has nothing to do with {@link #buffer(byte[])}.
	 * Just part of Java API {@link SourceDataLine#getBufferSize()}
	 * @return
	 */
	@Override @Deprecated
	public int getBufferSize() {
		return sourceLine.getBufferSize();
	}

	@Override
	public int available() {
		return sourceLine.available();
	}

	@Override
	public int getFramePosition() {
		return sourceLine.getFramePosition();
	}

	@Override
	public long getLongFramePosition() {
		return sourceLine.getLongFramePosition();
	}

	@Override
	public long getMicrosecondPosition() {
		return sourceLine.getMicrosecondPosition();
	}

	@Override
	public float getLevel() {
		return sourceLine.getLevel();
	}

	@Override
	public Line.Info getLineInfo() {
		return sourceLine.getLineInfo();
	}



	@Override
	public boolean isOpen() {
		return sourceLine.isOpen();
	}

	@Override
	public Control[] getControls() {
		return sourceLine.getControls();
	}

	@Override
	public boolean isControlSupported(Control.Type control) {
		return sourceLine.isControlSupported(control);
	}

	@Override
	public Control getControl(Control.Type control) {
		return sourceLine.getControl(control);
	}

	@Override
	public void addLineListener(LineListener listener) {
		sourceLine.addLineListener(listener);
	}

	@Override
	public void removeLineListener(LineListener listener) {
		sourceLine.removeLineListener(listener);
	}


	//endregion
}
