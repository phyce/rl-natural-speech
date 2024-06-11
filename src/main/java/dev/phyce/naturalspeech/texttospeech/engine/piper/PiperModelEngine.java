package dev.phyce.naturalspeech.texttospeech.engine.piper;

import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.configs.PiperConfig;
import dev.phyce.naturalspeech.eventbus.PluginEventBus;
import dev.phyce.naturalspeech.events.PiperModelEngineEvent;
import dev.phyce.naturalspeech.events.PiperProcessEvent;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechEngine;
import dev.phyce.naturalspeech.utils.FuturesUtil.DirectFutures;
import dev.phyce.naturalspeech.utils.Result;
import static dev.phyce.naturalspeech.utils.Result.Results.Error;
import static dev.phyce.naturalspeech.utils.Result.Results.Ok;
import dev.phyce.naturalspeech.utils.Texts;
import dev.phyce.naturalspeech.utils.Threads;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.CheckReturnValue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

// Renamed from TTSModel
@Slf4j
public class PiperModelEngine implements SpeechEngine {

	public interface Factory {
		@NonNull
		PiperModelEngine create(@NonNull PiperRepository.PiperModel model, @NonNull Path piperPath);
	}

	private static final AudioFormat audioFormat =
		new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
			22050.0F, // Sample Rate (per second)
			16, // Sample Size (bits)
			1, // Channels
			2, // Frame Size (bytes)
			22050.0F, // Frame Rate (same as sample rate because PCM is 1 sample per 1 frame)
			false
		); // Little Endian

	private final PiperConfig piperConfig;
	private final PluginExecutorService pluginExecutorService;
	private final PluginEventBus pluginEventBus;
	private final AudioEngine audioEngine;
	private final PiperRepository piperRepository;
	private final VoiceManager voiceManager;

	private final ConcurrentHashMap<Long, PiperProcess> processes = new ConcurrentHashMap<>();
	private final BlockingQueue<PiperProcess> idleProcesses = Queues.newLinkedBlockingQueue();
	private final BlockingQueue<PiperTask> pendingTasks = Queues.newLinkedBlockingQueue();
	private final Vector<InflightTask> inflightTasks = new Vector<>();

	private TaskDispatchThread taskDispatchThread = null;
	@Getter
	private final PiperRepository.PiperModel model;
	private final Path piperPath;


	@Inject
	public PiperModelEngine(
		PiperConfig piperConfig,
		PluginExecutorService pluginExecutorService,
		PluginEventBus pluginEventBus,
		AudioEngine audioEngine,
		PiperRepository piperRepository,
		VoiceManager voiceManager,
		@Assisted PiperRepository.PiperModel model,
		@Assisted Path piperPath
	) {
		this.piperConfig = piperConfig;
		this.pluginExecutorService = pluginExecutorService;
		this.pluginEventBus = pluginEventBus;
		this.audioEngine = audioEngine;
		this.piperRepository = piperRepository;
		this.voiceManager = voiceManager;
		this.model = model;
		this.piperPath = piperPath;
	}

	@Override
	public @NonNull SpeechEngine.SpeakStatus speak(
		VoiceID voiceID,
		String text,
		Supplier<Float> gainSupplier,
		String lineName
	) {
		if (taskDispatchThread == null) {
			log.error("{} not started, cannot speak. text:{} lineName:{}", this, text, lineName);
			return SpeakStatus.REJECT;
		}

		if (processCount() == 0) {
			log.error("{}: no active processes. text:{} lineName:{}", this, text, lineName);
			return SpeakStatus.REJECT;
		}

		List<String> segments = text.length() > 50 ? Texts.splitSentence(text) : List.of(text);

		if (!contains(voiceID)) {
			return SpeakStatus.REJECT;
		}

		int id = Preconditions.checkNotNull(voiceID.getIntId());

		PiperTask task = new PiperTask(segments, id, gainSupplier, lineName);

		pendingTasks.add(task);

		return SpeakStatus.ACCEPT;
	}

	@Override
	public ListenableFuture<StartResult> start() {
		if (!piperConfig.isEnabled(model.getModelName())) {
			return Futures.immediateFuture(StartResult.DISABLED);
		}

		Result<Void, IOException> result = spawn(piperConfig.getProcessCount(model.getModelName()));
		if (result.isError()) {
			log.error("Failed to spawn piper process for {}.", this, result.unwrapError());
			return Futures.immediateFuture(StartResult.FAILED);
		}

		register();

		taskDispatchThread = new TaskDispatchThread(String.format("%s::TaskDispatchThread", model.modelName));
		taskDispatchThread.start();

		pluginEventBus.post(PiperModelEngineEvent.STARTED(model));

		return Futures.immediateFuture(StartResult.SUCCESS);
	}

	public void stop() {
		if (!isStarted()) return;

		for (PiperProcess process : processes.values()) {
			process.destroy();
		}

		silenceAll();

		processes.clear();
		idleProcesses.clear();

		taskDispatchThread.interrupt();
		taskDispatchThread = null;

		pluginEventBus.post(PiperModelEngineEvent.STOPPED(model));

		unregister();
	}

	@Override
	public boolean isStarted() {
		return taskDispatchThread != null;
	}

	@Override
	public boolean contains(VoiceID voice) {
		Integer id = voice.getIntId();
		if (id == null) {
			return false;
		}

		if (!model.modelName.equals(voice.modelName)) {
			return false;
		}

		return Arrays.stream(model.voices)
			.anyMatch(metadata -> metadata.piperVoiceID == id);
	}

	@Override
	public void silence(Predicate<String> lineCondition) {
		var taskIter = pendingTasks.iterator();
		while (taskIter.hasNext()) {
			PiperTask task = taskIter.next();
			if (lineCondition.test(task.lineName)) {
				log.debug("removing pending task:{}", task);
				taskIter.remove();
			}
		}

		inflightTasks.forEach(inflight -> {
			if (lineCondition.test(inflight.task.lineName)) {
				log.debug("interrupting inflight task:{}", inflight);
				inflight.runnerFuture.cancel(true);
			}
		});
	}

	@Override
	public void silenceAll() {
		pendingTasks.clear();
		inflightTasks.forEach(taskFuture -> taskFuture.runnerFuture.cancel(true));
	}

	@Override
	public @NonNull EngineType getEngineType() {
		return EngineType.EXTERNAL_DEPENDENCY;
	}

	@Override
	public @NonNull String getEngineName() {
		return "PiperModel";
	}

	@CheckReturnValue
	private Result<Void, IOException> spawn(int count) {
		//Instance count should not be more than 2
		for (int index = 0; index < count; index++) {
			Result<PiperProcess, IOException> result = PiperProcess.start(piperPath, model.getOnnx().toPath());
			if (result.isError()) return Error(result.unwrapError());

			result.ifOk(process -> {
				processes.put(process.getPid(), process);
				idleProcesses.add(process);

				pluginEventBus.post(PiperProcessEvent.SPAWNED(this, process, model));

				DirectFutures.addSuccess(process.onExit(), callback ->
					pluginEventBus.post(PiperProcessEvent.DIED(this, process, model)));
				DirectFutures.addSuccess(process.onCrash(), callback ->
					pluginEventBus.post(PiperProcessEvent.CRASHED(this, process, model)));

			});
		}

		return Ok();
	}

	public Map<Long, PiperProcess> getProcesses() {
		return Collections.unmodifiableMap(processes);
	}

	public int processCount() {
		return (int) processes.values().stream().filter(PiperProcess::alive).count();
	}

	private void register() {
		for (PiperRepository.PiperVoice voiceMetadata : model.getVoices()) {
			voiceManager.register(voiceMetadata.toVoiceID(), voiceMetadata.getGender());
		}
	}

	private void unregister() {
		for (PiperRepository.PiperVoice voiceMetadata : model.getVoices()) {
			voiceManager.unregister(voiceMetadata.toVoiceID());
		}
	}

	@Override
	public String toString() {
		return String.format("Piper for %s with %d active processes, task count:%d, result count:%d",
			getModel().getModelName(),
			processCount(),
			pendingTasks.size(),
			inflightTasks.size()
		);
	}

	@Value
	@AllArgsConstructor
	private static class PiperTask {
		@NonNull
		List<String> texts;
		int voice;
		@NonNull
		Supplier<Float> gainSupplier;
		@NonNull
		String lineName;

		@Override
		public String toString() {
			return String.format("(PiperTask lineName:%s id:%s text:%s)",
				lineName,
				voice,
				Texts.sentenceSegmentPrettyPrint(texts));
		}
	}

	@Value
	@AllArgsConstructor
	private static class InflightTask {
		@NonNull
		PiperTask task;
		@NonNull
		ListenableFuture<Void> runnerFuture;

		@Override
		public String toString() {
			return String.format("(InflightTask task:%s)", task);
		}
	}

	private class TaskDispatchThread extends Thread {

		public TaskDispatchThread(String name) {
			super(name);
			setUncaughtExceptionHandler(Threads.silentInterruptHandler);
		}

		@SneakyThrows(InterruptedException.class)
		@Override
		public void run() {
			while (!isInterrupted()) {
				PiperTask task = pendingTasks.take(); // blocking
				TaskRunner runner = new TaskRunner(task);
				ListenableFuture<Void> taskFuture = Futures.submit(runner, pluginExecutorService);
				InflightTask inflightTask = new InflightTask(task, taskFuture);
				inflightTasks.add(inflightTask);
				taskFuture.addListener(() -> inflightTasks.remove(inflightTask), pluginExecutorService);
			}

			log.trace("TaskDispatchThread dead.");
		}

	}

	@Value
	private class TaskRunner implements Runnable {

		@NonNull
		PiperTask task;

		@Override
		public void run() {
			_run();
		}

		private void _run() {
			List<ListenableFuture<byte[]>> sequence = new ArrayList<>();

			for (String text : task.texts) {
				ListenableFuture<byte[]> result =
					Futures.submit(() -> {
						PiperProcess process = null;
						try {
							process = idleProcesses.take();
							byte[] audio = process.generate(task.voice, text);
							log.debug("Generated audio(bytes:{})", audio.length);
							return audio;
						} finally {
							if (process != null) idleProcesses.add(process);
						}
					}, pluginExecutorService);

				DirectFutures.addFailure(result, exception -> {
					if (exception instanceof CancellationException) {log.debug("Cancelled {}", task);}
					else {log.error("Exception {}", task, exception);}
				});

				sequence.add(result);
			}

			for (ListenableFuture<byte[]> segment : sequence) {
				try {
					byte[] audio = segment.get(); // blocking

					AudioInputStream audioStream = new AudioInputStream(
						new ByteArrayInputStream(audio),
						audioFormat,
						audio.length / 2
					);
					audioEngine.play(task.lineName, audioStream, task.gainSupplier);
				} catch (InterruptedException e) {
					log.debug("Interrupted while waiting for audio generation. {}:{} ", this, task);
					sequence.forEach(future -> future.cancel(true));
					return;
				} catch (ExecutionException e) {
					log.error("Exception when generating audio. {}:{}", this, task, e);
					sequence.forEach(future -> future.cancel(true));
					return;
				}
			}
		}
	}

}


