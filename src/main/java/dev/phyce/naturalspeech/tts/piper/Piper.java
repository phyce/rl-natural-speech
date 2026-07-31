package dev.phyce.naturalspeech.tts.piper;

import dev.phyce.naturalspeech.tts.AudioPlayer;
import dev.phyce.naturalspeech.tts.AudioQueue;
import dev.phyce.naturalspeech.tts.ModelRepository;
import dev.phyce.naturalspeech.tts.PlaybackGate;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.IntSupplier;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

// Renamed from TTSModel
@Slf4j
public class Piper {
	@Getter
	private final Map<Long, PiperProcess> processMap = new HashMap<>();
	@Getter
	private final ConcurrentHashMap<String, AudioQueue> namedAudioQueueMap = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<PiperTask> piperTaskQueue = new ConcurrentLinkedQueue<>();
	private final AudioPlayer audioPlayer;

	@Getter
	private final ModelRepository.ModelLocal modelLocal;
	@Getter
	private final Path piperPath;
	private final Thread processPiperTaskThread;
	private final Thread processAudioQueueThread;

	private final List<PiperProcessLifetimeListener> piperProcessLifetimeListeners = new ArrayList<>();

	/**
	 * Create a piper and immediately start
	 *
	 * @throws IOException if piper fails to start an IOException will be thrown. (because stdin cannot be opened).
	 */
	public static Piper start(ModelRepository.ModelLocal modelLocal, Path piperPath, int instanceCount)
		throws IOException {
		return new Piper(modelLocal, piperPath, instanceCount, () -> -1, PlaybackGate.disabled());
	}

	public static Piper start(ModelRepository.ModelLocal modelLocal, Path piperPath, int instanceCount,
		IntSupplier dialogGenSupplier, PlaybackGate playbackGate) throws IOException {
		return new Piper(modelLocal, piperPath, instanceCount, dialogGenSupplier, playbackGate);
	}

	private final IntSupplier dialogGenSupplier;
	private final PlaybackGate playbackGate;

	private Piper(ModelRepository.ModelLocal modelLocal, Path piperPath, int instanceCount,
		IntSupplier dialogGenSupplier, PlaybackGate playbackGate) throws IOException {
		this.modelLocal = modelLocal;
		this.piperPath = piperPath;
		this.dialogGenSupplier = dialogGenSupplier;
		this.playbackGate = playbackGate;

		audioPlayer = new AudioPlayer();

		startMore(instanceCount);

		processPiperTaskThread =
			new Thread(this::processPiperTask, String.format("[%s] Piper::processPiperTask Thread", this));
		processPiperTaskThread.start();

		processAudioQueueThread =
			new Thread(this::processAudioQueue, String.format("[%s] Piper::processAudioQueue Thread", this));
		processAudioQueueThread.start();
	}

	public void startMore(int instanceCount) throws IOException {
		//Instance count should not be more than 2
		for (int index = 0; index < instanceCount; index++) {
			PiperProcess process;
			try {
				process = PiperProcess.start(piperPath, modelLocal.getOnnx().toPath());
				triggerOnPiperProcessStart(process);
			} catch (IOException e) {
				// clean-up stray instances before throwing
				processMap.forEach((pid, piperProcess) -> piperProcess.stop());
				processMap.clear();
				throw e;
			}
			process.onExit().thenAccept(p -> {
				triggerOnPiperProcessExit(p);
			});
			processMap.put(process.getPid(), process);
		}
	}

	//Process message queue
	public void processPiperTask() {
		while (!processPiperTaskThread.isInterrupted()) {
			if (piperTaskQueue.isEmpty()) {
				synchronized (piperTaskQueue) {
					try {
						piperTaskQueue.wait();
					} catch (InterruptedException e) {
						return; // just exit on interrupt
					}
				}
				continue; // double check emptiness after notify.
			}

			PiperTask task = piperTaskQueue.poll();

			if (processMap.isEmpty()) {
			}

			// using iterator to loop, so if an invalid PiperProcess is found we can remove.
			Iterator<Long> iter = processMap.keySet().iterator();
			while (iter.hasNext()) {
				long pid = iter.next();
				PiperProcess process = processMap.get(pid);

				if (!process.isAlive()) {
					iter.remove();
					triggerOnPiperProcessCrash(process);
					continue;
				}

				if (!process.getPiperLocked().get()) {
					byte[] audioClip;
					try {
						triggerOnPiperProcessBusy(process);
						audioClip = process.generateAudio(task.getText(), task.getVoiceID().getPiperVoiceID());
						triggerOnPiperProcessDone(process);
					} catch (IOException | InterruptedException e) {
						// PiperProcess exited unexpectedly, remove the process
						log.error("{} had an unexpected exited, either crashed or terminated by user.", process);
						triggerOnPiperProcessCrash(process);

						process.stop();
						iter.remove();
						continue;
					}
					if (audioClip != null && audioClip.length > 0) {
						// Drop stale audio whose generation has been bumped (e.g. user
						// skipped past this dialog line while its synth was in flight).
						if (task.generation >= 0 && task.generation != dialogGenSupplier.getAsInt()) {
							log.trace("Dropping stale audio for {} (gen {} != current {})",
								task.audioQueueName, task.generation, dialogGenSupplier.getAsInt());
							break;
						}
						AudioQueue audioQueue =
							namedAudioQueueMap.computeIfAbsent(task.audioQueueName, audioQueueName -> new AudioQueue());
						audioQueue.queue.add(new AudioQueue.AudioTask(audioClip, task.getVolume()));

						synchronized (namedAudioQueueMap) {namedAudioQueueMap.notify();}

						break;
					}
				}
			}
		}
	}

	public void processAudioQueue() {
		while (!processAudioQueueThread.isInterrupted()) {

			synchronized (namedAudioQueueMap) {
				try {
					namedAudioQueueMap.wait();
				} catch (InterruptedException e) {
					return;
				}
			}

			namedAudioQueueMap.forEach((queueName, audioQueue) -> {

				if (!audioQueue.isPlaying() && !audioQueue.queue.isEmpty()) {
					audioQueue.setPlaying(true);

					// start a thread for each named audio queue
					new Thread(() -> {
						try {
							// held for the whole drain, so the fragments a single message was split
							// into are not interleaved with another speaker's when playing in turn
							try (PlaybackGate.Hold ignored = playbackGate.acquire()) {
								AudioQueue.AudioTask task;
								while ((task = audioQueue.queue.poll()) != null) {
									audioPlayer.playClip(task.getAudioClip(), task.getVolume(), queueName);
								}
							}
						} finally {
							audioQueue.setPlaying(false);
						}
					}, String.format("[%s] AudioPlayer Thread for %s", this, queueName)).start();
				}

			});
		}
	}

	public void speak(String text, VoiceID voiceID, float volume, String audioQueueName) throws IOException {
		speak(text, voiceID, volume, audioQueueName, -1);
	}

	public void speak(String text, VoiceID voiceID, float volume, String audioQueueName, int generation) throws IOException {
		if (countAlive() == 0) {
			throw new IOException("No active PiperProcess instances running for " + voiceID.getModelName());
		}

		if (piperTaskQueue.size() > 10) {
			log.info("Cleared queue because queue size is too large. (more then 10)");
			clearQueue();
		}

		piperTaskQueue.add(new PiperTask(text, voiceID, volume, audioQueueName, generation));
		synchronized (piperTaskQueue) {piperTaskQueue.notify();}
	}

	public void clearQueue() {
		piperTaskQueue.clear();
		namedAudioQueueMap.values().forEach(audioQueue -> {
			audioQueue.queue.clear();
		});
	}

	public void silenceQueue(String queueName) {
		piperTaskQueue.removeIf(task -> queueName.equals(task.getAudioQueueName()));
		AudioQueue audioQueue = namedAudioQueueMap.get(queueName);
		if (audioQueue != null) {
			audioQueue.queue.clear();
		}
		audioPlayer.stopQueue(queueName);
	}

	public int pendingAudioCount() {
		int total = piperTaskQueue.size();
		for (AudioQueue audioQueue : namedAudioQueueMap.values()) {
			total += audioQueue.queue.size();
		}
		return total;
	}

	public int countAlive() {
		int result = 0;
		for (PiperProcess process : processMap.values()) {
			if (process.isAlive()) result++;
		}
		return result;
	}

	public void stop() {
		audioPlayer.stop();

		for (PiperProcess instance : processMap.values()) {
			instance.stop();
		}
		processMap.clear();

		// clear task and audio queue on stop
		clearQueue();

		processAudioQueueThread.interrupt();
		processPiperTaskThread.interrupt();
	}

	/**
	 * @param listener This is not called on the client thread, please be careful.
	 */
	public void addPiperListener(PiperProcessLifetimeListener listener) {
		piperProcessLifetimeListeners.add(listener);
	}

	public void removePiperListener(PiperProcessLifetimeListener listener) {
		piperProcessLifetimeListeners.remove(listener);
	}

	private void triggerOnPiperProcessBusy(PiperProcess process) {
		for (PiperProcessLifetimeListener listener : piperProcessLifetimeListeners) {
			listener.onPiperProcessBusy(process);
		}
	}

	private void triggerOnPiperProcessCrash(PiperProcess process) {
		for (PiperProcessLifetimeListener listener : piperProcessLifetimeListeners) {
			listener.onPiperProcessCrash(process);
		}
	}

	private void triggerOnPiperProcessDone(PiperProcess process) {
		for (PiperProcessLifetimeListener listener : piperProcessLifetimeListeners) {
			listener.onPiperProcessDone(process);
		}
	}

	private void triggerOnPiperProcessStart(PiperProcess process) {
		for (PiperProcessLifetimeListener listener : piperProcessLifetimeListeners) {
			listener.onPiperProcessStart(process);
		}
	}

	private void triggerOnPiperProcessExit(PiperProcess process) {
		for (PiperProcessLifetimeListener listener : piperProcessLifetimeListeners) {
			listener.onPiperProcessExit(process);
		}
	}

	@Override
	public String toString() {
		return String.format("Piper for %s with %d active processes", getModelLocal().getModelName(), countAlive());
	}

	// Renamed from TTSItem, decoupled from dependencies
	@Value
	@AllArgsConstructor
	private static class PiperTask {
		String text;
		VoiceID voiceID;
		float volume;
		String audioQueueName;
		int generation;
	}

	public interface PiperProcessLifetimeListener {
		default void onPiperProcessStart(PiperProcess process) {}

		default void onPiperProcessExit(PiperProcess process) {}

		// Busy generating voices
		default void onPiperProcessBusy(PiperProcess process) {}

		// Done generating voices
		default void onPiperProcessDone(PiperProcess process) {}

		default void onPiperProcessCrash(PiperProcess process) {}
	}
}
