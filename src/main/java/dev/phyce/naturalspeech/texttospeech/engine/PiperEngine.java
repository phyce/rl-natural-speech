package dev.phyce.naturalspeech.texttospeech.engine;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import dev.phyce.naturalspeech.configs.PiperConfig;
import dev.phyce.naturalspeech.configs.RuntimePathConfig;
import dev.phyce.naturalspeech.eventbus.PluginEventBus;
import dev.phyce.naturalspeech.events.PiperProcessEvent;
import dev.phyce.naturalspeech.events.SpeechEngineEvent;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.texttospeech.Voice;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperProcess;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository.PiperModel;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository.PiperVoice;
import dev.phyce.naturalspeech.utils.FuncFutures;
import dev.phyce.naturalspeech.utils.Result;
import static dev.phyce.naturalspeech.utils.Result.Error;
import static dev.phyce.naturalspeech.utils.Result.Ok;
import static dev.phyce.naturalspeech.utils.Result.ResultFutures.immediateError;
import static dev.phyce.naturalspeech.utils.Result.ResultFutures.immediateOk;
import dev.phyce.naturalspeech.utils.StreamableFuture;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.CheckReturnValue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import lombok.Getter;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

// Renamed from TTSModel
@Slf4j
public class PiperEngine extends ManagedSpeechEngine {

	public interface Factory {
		@NonNull
		PiperEngine create(@NonNull PiperModel model);
	}

	private static final AudioFormat audioFormat =
		new AudioFormat(Encoding.PCM_SIGNED,
			22050.0F, // Sample Rate (per second)
			16, // Sample Size (bits)
			1, // Channels
			2, // Frame Size (bytes)
			22050.0F, // Frame Rate (same as sample rate because PCM is 1 sample per 1 frame)
			false
		); // Little Endian

	private final RuntimePathConfig runtimePathConfig;
	private final PiperConfig piperConfig;
	private final PluginExecutorService pluginExecutorService;
	private final PluginEventBus pluginEventBus;

	private final ConcurrentHashMap<Long, PiperProcess> processes = new ConcurrentHashMap<>();
	private final BlockingQueue<PiperProcess> idleProcesses = Queues.newLinkedBlockingQueue();
	private final Vector<StreamableFuture<Audio>> inflightFutures = new Vector<>();
	@Getter
	private final PiperModel model;

	@Getter // @Override
	private final ImmutableSet<Voice> voices;
	@Getter // @Override
	private final ImmutableSet<VoiceID> voiceIDs;


	@Inject
	public PiperEngine(
		RuntimePathConfig runtimePathConfig,
		PiperConfig piperConfig,
		PluginExecutorService pluginExecutorService,
		PluginEventBus pluginEventBus,
		@Assisted PiperModel model
	) {
		this.runtimePathConfig = runtimePathConfig;
		this.piperConfig = piperConfig;
		this.pluginExecutorService = pluginExecutorService;
		this.pluginEventBus = pluginEventBus;
		this.model = model;

		voices = voices(model);
		voiceIDs = voiceIDs(model);
	}

	@Override
	public @NonNull Result<StreamableFuture<Audio>, Rejection> generate(
		@NonNull VoiceID voiceID,
		@NonNull String text
	) {
		if (!isAlive()) return Error(Rejection.DEAD(this));
		if (!voiceIDs.contains(voiceID)) return Error(Rejection.REJECT(this));


		List<String> segments = text.length() > 50 ? TextUtil.splitSentence(text) : List.of(text);


		int piperId = Preconditions.checkNotNull(voiceID.getIntId());

		ImmutableList<ListenableFuture<Audio>> futures = segments.stream()
			.map(segment -> _generate(piperId, segment))
			.map(generate -> Futures.submit(generate, pluginExecutorService))
			.collect(ImmutableList.toImmutableList());

		StreamableFuture<Audio> future = new StreamableFuture<>(futures, Audio::join);
		inflightFutures.add(future);
		FuncFutures.onComplete(future, () -> inflightFutures.remove(future));
		return Ok(future);
	}

	private Callable<Audio> _generate(int piperId, String text) {
		return () -> {
			PiperProcess process = null;
			try {
				do {
					var candidate = idleProcesses.take();
					if (candidate.alive()) {
						process = candidate;
					}
					else {
						log.error("Found dead Piper process in idle queue. text:{}", candidate.getPid());
					}
				} while (process == null);

				byte[] bytes = process.generate(piperId, text);
				Audio audio = Audio.of(bytes, audioFormat);
				log.debug("Generated audio (byte size:{})", bytes.length);
				return audio;
			} finally {
				log.debug("Adding back to idle {}", process);
				if (process != null) idleProcesses.add(process);
			}
		};
	}

	@Override
	@Synchronized
	@NonNull
	ListenableFuture<Result<Void, EngineError>> startup() {
		// if process count has changed, shutdown first then start.
		if (isAlive()) {
			return immediateOk();
		}

		if (!piperConfig.isEnabled(model.getModelName())) {
			return immediateError(EngineError.DISABLED(this));
		}

		if (!runtimePathConfig.isPiperPathValid()) {
			return immediateError(EngineError.NO_RUNTIME(this));
		}

		Result<Void, IOException> result = spawn(piperConfig.getProcessCount(model.getModelName()));
		if (result.isError()) {
			log.error("Failed to spawn piper process for {}.", this, result.unwrapError());
			return immediateError(EngineError.UNEXPECTED_FAIL(this));
		}

		return immediateOk();
	}


	@Override
	@Synchronized
	void shutdown() {
		try {
			if (!isAlive()) {
				return;
			}

			for (PiperProcess process : processes.values()) {
				process.destroy();
			}

			Iterator<StreamableFuture<Audio>> iter = inflightFutures.iterator();
			while (iter.hasNext()) {
				iter.next().cancel(true);
				iter.remove();
			}

		} finally {
			cleanup();
		}
	}

	@CheckReturnValue
	private Result<Void, IOException> spawn(int count) {
		//Instance count should not be more than 2
		for (int index = 0; index < count; index++) {
			Result<PiperProcess, IOException> result =
				PiperProcess.start(runtimePathConfig.getPiperPath(), model.getOnnx().toPath());
			if (result.isError()) return Error(result.unwrapError());
			result.ifOk(process -> {
				processes.put(process.getPid(), process);
				idleProcesses.add(process);

				pluginEventBus.post(PiperProcessEvent.SPAWNED(this, process, model));

				FuncFutures.onSuccess(process.onExit(),
					callback -> pluginEventBus.post(PiperProcessEvent.DIED(this, process, model)));

				FuncFutures.onSuccess(process.onCrash(), callback -> {
					pluginEventBus.post(PiperProcessEvent.CRASHED(this, process, model));
					if (!isAlive()) {
						EngineError error = EngineError.UNEXPECTED_FAIL(this);
						pluginEventBus.post(SpeechEngineEvent.CRASHED(error));
						cleanup();
					}
				});
			});
		}

		return Ok();
	}

	private void cleanup() {
		processes.clear();
		idleProcesses.clear();
	}

	@Override
	public boolean isAlive() {
		return processCount() != 0;
	}

	public int processCount() {
		return (int) processes.values().stream().filter(PiperProcess::alive).count();
	}

	@Override
	public @NonNull EngineType getEngineType() {
		return EngineType.EXTERNAL_DEPENDENCY;
	}

	@Override
	public @NonNull String getEngineName() {
		return model.getModelName();
	}

	public Map<Long, PiperProcess> getProcesses() {
		return Collections.unmodifiableMap(processes);
	}

	@NonNull
	private static ImmutableSet<Voice> voices(PiperModel model) {
		return Arrays.stream(model.getVoices())
			.map(metadata -> Voice.of(metadata.toVoiceID(), metadata.getGender()))
			.collect(ImmutableSet.toImmutableSet());
	}

	private static ImmutableSet<VoiceID> voiceIDs(PiperModel model) {
		return Arrays.stream(model.getVoices())
			.map(PiperVoice::toVoiceID)
			.collect(ImmutableSet.toImmutableSet());

	}

	@Override
	public String toString() {
		return String.format("PiperEngine(model:%s active-processes:%d)",
			getModel().getModelName(),
			processCount()
		);
	}

}


