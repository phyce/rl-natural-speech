package dev.phyce.naturalspeech.tts.elevenlabs;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.tts.AudioPlayer;
import dev.phyce.naturalspeech.tts.AudioQueue;
import dev.phyce.naturalspeech.tts.PlaybackGate;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import javax.annotation.CheckForNull;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
public class ElevenLabsEngine {
	private static final HttpUrl SPEECH_URL = HttpUrl.get("https://api.elevenlabs.io/v1/text-to-speech");
	private static final String OUTPUT_FORMAT = "pcm_22050";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final int MAX_PENDING_TASKS = 10;

	@Getter
	private final ConcurrentHashMap<String, AudioQueue> namedAudioQueueMap = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<SpeechTask> taskQueue = new ConcurrentLinkedQueue<>();
	private final AtomicReference<Call> activeCall = new AtomicReference<>();
	private final AudioPlayer audioPlayer = new AudioPlayer();
	private final ExecutorService synthesisExecutor;
	private final ExecutorService playbackExecutor;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final NaturalSpeechConfig config;
	private final ElevenLabsCache cache;
	private final PlaybackGate playbackGate;
	private final IntSupplier dialogGenSupplier;

	public ElevenLabsEngine(OkHttpClient httpClient, Gson gson, NaturalSpeechConfig config, ElevenLabsCache cache,
							PlaybackGate playbackGate, IntSupplier dialogGenSupplier) {
		this.httpClient = httpClient.newBuilder()
			.readTimeout(30, TimeUnit.SECONDS)
			.build();
		this.gson = gson;
		this.config = config;
		this.cache = cache;
		this.playbackGate = playbackGate;
		this.dialogGenSupplier = dialogGenSupplier;

		synthesisExecutor = Executors.newSingleThreadExecutor(
			new ThreadFactoryBuilder().setNameFormat("ElevenLabs synthesis").setDaemon(true).build());
		playbackExecutor = Executors.newCachedThreadPool(
			new ThreadFactoryBuilder().setNameFormat("ElevenLabs AudioPlayer %d").setDaemon(true).build());
	}

	public void speak(String text, VoiceID voiceID, float volume, String audioQueueName, int generation) {
		if (taskQueue.size() > MAX_PENDING_TASKS) {
			log.info("Cleared ElevenLabs queue because queue size is too large. (more than {})", MAX_PENDING_TASKS);
			taskQueue.clear();
		}

		taskQueue.add(new SpeechTask(text, voiceID, volume, audioQueueName, generation));
		synthesisExecutor.execute(this::processSpeechTask);
	}

	private void processSpeechTask() {
		SpeechTask task = taskQueue.poll();
		if (task == null || isStale(task)) return;

		byte[] audioClip = synthesize(task);
		if (audioClip == null || audioClip.length == 0) return;

		if (isStale(task)) {
			log.trace("Dropping stale audio for {} (gen {} != current {})",
				task.getAudioQueueName(), task.getGeneration(), dialogGenSupplier.getAsInt());
			return;
		}

		AudioQueue audioQueue =
			namedAudioQueueMap.computeIfAbsent(task.getAudioQueueName(), audioQueueName -> new AudioQueue());
		audioQueue.queue.add(new AudioQueue.AudioTask(audioClip, task.getVolume()));
		startPlayback(task.getAudioQueueName(), audioQueue);
	}

	private boolean isStale(SpeechTask task) {
		return task.getGeneration() >= 0 && task.getGeneration() != dialogGenSupplier.getAsInt();
	}

	@CheckForNull
	private byte[] synthesize(SpeechTask task) {
		String model = task.getVoiceID().getModelName();
		String cacheKey = String.join("|", model, OUTPUT_FORMAT, task.getVoiceID().getId(), task.getText());

		if (config.elevenLabsCacheEnabled()) {
			byte[] cached = cache.get(cacheKey);
			if (cached != null) return cached;
		}

		byte[] audioClip = request(task, model);
		if (audioClip != null && config.elevenLabsCacheEnabled()) {
			cache.put(cacheKey, audioClip);
		}
		return audioClip;
	}

	@CheckForNull
	private byte[] request(SpeechTask task, String model) {
		String apiKey = config.elevenLabsApiKey().trim();
		if (apiKey.isEmpty()) return null;

		JsonObject body = new JsonObject();
		body.addProperty("text", task.getText());
		body.addProperty("model_id", model);

		HttpUrl url = SPEECH_URL.newBuilder()
			.addPathSegment(task.getVoiceID().getId())
			.addQueryParameter("output_format", OUTPUT_FORMAT)
			.build();

		Request request = new Request.Builder()
			.url(url)
			.header("xi-api-key", apiKey)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		Call call = httpClient.newCall(request);
		activeCall.set(call);
		try (Response response = call.execute()) {
			ResponseBody responseBody = response.body();
			if (!response.isSuccessful() || responseBody == null) {
				log.warn("ElevenLabs failed to speak with {}, HTTP {}: {}", task.getVoiceID(), response.code(),
					responseBody == null ? "" : responseBody.string());
				return null;
			}
			return responseBody.bytes();
		} catch (IOException e) {
			if (!call.isCanceled()) log.warn("ElevenLabs request failed", e);
			return null;
		} finally {
			activeCall.compareAndSet(call, null);
		}
	}

	private void startPlayback(String queueName, AudioQueue audioQueue) {
		synchronized (audioQueue) {
			if (audioQueue.isPlaying()) return;
			audioQueue.setPlaying(true);
		}

		playbackExecutor.execute(() -> {
			while (true) {
				try (PlaybackGate.Hold ignored = playbackGate.acquire(queueName)) {
					AudioQueue.AudioTask task;
					synchronized (audioQueue) {
						task = audioQueue.queue.poll();
						if (task == null) {
							audioQueue.setPlaying(false);
							return;
						}
					}
					audioPlayer.playClip(task.getAudioClip(), task.getVolume(), queueName);
				}
			}
		});
	}

	public void clearCache() {
		synthesisExecutor.execute(cache::clear);
	}

	public int pendingAudioCount() {
		int total = taskQueue.size();
		for (AudioQueue audioQueue : namedAudioQueueMap.values()) {
			total += audioQueue.queue.size();
		}
		return total;
	}

	public void silenceQueue(String queueName) {
		taskQueue.removeIf(task -> queueName.equals(task.getAudioQueueName()));
		AudioQueue audioQueue = namedAudioQueueMap.get(queueName);
		if (audioQueue != null) {
			audioQueue.queue.clear();
		}
		audioPlayer.stopQueue(queueName);
	}

	public void clearQueue() {
		taskQueue.clear();
		namedAudioQueueMap.values().forEach(audioQueue -> audioQueue.queue.clear());
	}

	public void stop() {
		clearQueue();

		Call call = activeCall.getAndSet(null);
		if (call != null) call.cancel();

		synthesisExecutor.shutdownNow();
		playbackExecutor.shutdownNow();
		audioPlayer.stop();
	}

	@Value
	private static class SpeechTask {
		String text;
		VoiceID voiceID;
		float volume;
		String audioQueueName;
		int generation;
	}
}
