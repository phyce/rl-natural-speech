package dev.phyce.naturalspeech.tts.elevenlabs;

import com.google.gson.Gson;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.tts.AudioPlayer;
import dev.phyce.naturalspeech.tts.AudioQueue;
import dev.phyce.naturalspeech.tts.PlaybackGate;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Speaks via the ElevenLabs cloud API ({@code POST /v1/text-to-speech/{voice_id}}).
 * <p>
 * Deliberately shaped like {@link dev.phyce.naturalspeech.tts.nativespeech.NativeSpeechEngine}: one
 * worker thread turns queued text into audio in order, the bytes land in the shared
 * {@link AudioQueue} keyed by speaker, and playback goes through the same {@link AudioPlayer} and
 * {@link PlaybackGate} as piper. That means distance fade, master volume, the friends boost, the
 * single-message queue and dialog interruption all work here without special-casing.
 * <p>
 * Audio is requested as headerless 22.05 kHz PCM so it matches {@link AudioPlayer}'s fixed format,
 * and identical lines are served from {@link ElevenLabsCache} rather than re-billed.
 */
@Slf4j
public class ElevenLabsEngine {

	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final ConcurrentHashMap<String, AudioQueue> namedAudioQueueMap = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<SpeechTask> taskQueue = new ConcurrentLinkedQueue<>();

	private final AudioPlayer audioPlayer = new AudioPlayer();
	private final PlaybackGate playbackGate;
	private final IntSupplier dialogGenSupplier;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final NaturalSpeechConfig config;
	private final ElevenLabsCache cache;

	/** The request currently in flight, so it can be cancelled on stop. */
	private final AtomicReference<Call> inFlight = new AtomicReference<>();

	private final Thread processTaskThread;
	private final Thread processAudioQueueThread;

	private volatile boolean running = true;

	public ElevenLabsEngine(
		OkHttpClient httpClient,
		Gson gson,
		NaturalSpeechConfig config,
		PlaybackGate playbackGate,
		IntSupplier dialogGenSupplier) {
		this.httpClient = httpClient.newBuilder()
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.callTimeout(0, TimeUnit.MILLISECONDS)
			.build();
		this.gson = gson;
		this.config = config;
		this.playbackGate = playbackGate;
		this.dialogGenSupplier = dialogGenSupplier;
		this.cache = new ElevenLabsCache(
			() -> Math.max(1, config.elevenLabsCacheSizeMb()) * 1024L * 1024L);

		processTaskThread = new Thread(this::processTask, "ElevenLabsEngine::processTask Thread");
		processTaskThread.setDaemon(true);
		processTaskThread.start();

		processAudioQueueThread =
			new Thread(this::processAudioQueue, "ElevenLabsEngine::processAudioQueue Thread");
		processAudioQueueThread.setDaemon(true);
		processAudioQueueThread.start();
	}

	/** ElevenLabs is usable as soon as an API key is configured; there is no process to spawn. */
	public boolean isAvailable() {
		return running && !apiKey().isEmpty();
	}

	private String apiKey() {
		String key = config.elevenLabsApiKey();
		return key == null ? "" : key.trim();
	}

	public void speak(String text, VoiceID voiceID, float volume, String audioQueueName, int generation) {
		if (!isAvailable()) {
			log.debug("No ElevenLabs API key configured, dropping: {}", text);
			return;
		}
		if (text == null || text.trim().isEmpty()) return;

		taskQueue.add(new SpeechTask(text, voiceID, volume, audioQueueName, generation));
		synchronized (taskQueue) {taskQueue.notify();}
	}

	private void processTask() {
		while (!processTaskThread.isInterrupted()) {
			if (taskQueue.isEmpty()) {
				synchronized (taskQueue) {
					try {
						taskQueue.wait();
					}
					catch (InterruptedException e) {
						return;
					}
				}
				continue; // double check emptiness after notify
			}

			SpeechTask task = taskQueue.poll();
			if (task == null) continue;

			// Dialogue that has already been skipped is not worth paying to synthesize
			if (isStale(task)) {
				log.trace("Dropping stale ElevenLabs task for {}", task.audioQueueName);
				continue;
			}

			byte[] audioClip = synthesize(task);
			if (audioClip == null || audioClip.length == 0) continue;

			if (isStale(task)) {
				log.trace("Dropping stale ElevenLabs audio for {} (gen {} != current {})",
					task.audioQueueName, task.generation, dialogGenSupplier.getAsInt());
				continue;
			}

			AudioQueue audioQueue =
				namedAudioQueueMap.computeIfAbsent(task.audioQueueName, name -> new AudioQueue());
			audioQueue.queue.add(new AudioQueue.AudioTask(audioClip, task.getVolume()));

			synchronized (namedAudioQueueMap) {namedAudioQueueMap.notify();}
		}
	}

	private boolean isStale(SpeechTask task) {
		return task.generation >= 0 && task.generation != dialogGenSupplier.getAsInt();
	}

	private byte[] synthesize(SpeechTask task) {
		String key = apiKey();
		if (key.isEmpty()) return null;

		String voiceId = task.getVoiceID().getId();
		if (voiceId == null || voiceId.trim().isEmpty()) return null;

		String model = config.elevenLabsModel().getId();
		boolean useCache = config.elevenLabsCacheAudio();
		String cacheKey = model + "|" + ElevenLabs.OUTPUT_FORMAT + "|" + voiceId + "|" + task.getText();

		if (useCache) {
			byte[] cached = cache.get(cacheKey);
			if (cached != null) {
				log.trace("ElevenLabs cache hit for {}", task.audioQueueName);
				return cached;
			}
		}

		byte[] audio = fetch(task.getText(), voiceId, key, model);
		if (audio != null && audio.length > 0 && useCache) {
			cache.put(cacheKey, audio);
		}
		return audio;
	}

	private byte[] fetch(String text, String voiceId, String apiKey, String model) {
		Request request;
		try {
			request = new Request.Builder()
				.url(ElevenLabs.API_BASE + "/v1/text-to-speech/" + voiceId
					+ "?output_format=" + ElevenLabs.OUTPUT_FORMAT)
				.addHeader("xi-api-key", apiKey)
				.post(RequestBody.create(JSON, gson.toJson(new SpeechRequest(text, model))))
				.build();
		}
		catch (IllegalArgumentException e) {
			log.debug("Invalid ElevenLabs voice id: {}", voiceId, e);
			return null;
		}

		Call call = httpClient.newCall(request);
		inFlight.set(call);

		try (Response response = call.execute()) {
			ResponseBody body = response.body();

			if (!response.isSuccessful() || body == null) {
				log.warn("ElevenLabs returned HTTP {} (check the API key, voice id and your quota)",
					response.code());
				return null;
			}

			return body.bytes();
		}
		catch (IOException e) {
			if (!call.isCanceled()) {
				log.debug("ElevenLabs request failed", e);
			}
			return null;
		}
		finally {
			inFlight.compareAndSet(call, null);
		}
	}

	private void processAudioQueue() {
		while (!processAudioQueueThread.isInterrupted()) {
			synchronized (namedAudioQueueMap) {
				try {
					namedAudioQueueMap.wait();
				}
				catch (InterruptedException e) {
					return;
				}
			}

			namedAudioQueueMap.forEach((queueName, audioQueue) -> {
				if (!audioQueue.isPlaying() && !audioQueue.queue.isEmpty()) {
					audioQueue.setPlaying(true);

					new Thread(() -> {
						try {
							try (PlaybackGate.Hold ignored = playbackGate.acquire()) {
								AudioQueue.AudioTask task;
								while ((task = audioQueue.queue.poll()) != null) {
									audioPlayer.playClip(task.getAudioClip(), task.getVolume(), queueName);
								}
							}
						}
						finally {
							audioQueue.setPlaying(false);
						}
					}, String.format("ElevenLabsEngine AudioPlayer Thread for %s", queueName)).start();
				}
			});
		}
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

	/**
	 * Drops everything queued or in flight and cuts off playback, but leaves the engine usable —
	 * this is what the user-facing stop button means for a cloud backend with no process to kill.
	 */
	public void silenceAll() {
		Call call = inFlight.getAndSet(null);
		if (call != null) call.cancel();

		audioPlayer.stop();
		clearQueue();
	}

	/** Full teardown for plugin shutdown. The engine cannot be used again afterwards. */
	public void stop() {
		running = false;

		silenceAll();

		processAudioQueueThread.interrupt();
		processTaskThread.interrupt();
		synchronized (taskQueue) {taskQueue.notifyAll();}
		synchronized (namedAudioQueueMap) {namedAudioQueueMap.notifyAll();}
	}

	@Override
	public String toString() {
		return "ElevenLabs";
	}

	@Value
	@AllArgsConstructor
	private static class SpeechTask {
		String text;
		VoiceID voiceID;
		float volume;
		String audioQueueName;
		int generation;
	}

	/** JSON body for POST /v1/text-to-speech/{voice_id}. */
	@SuppressWarnings("unused")
	private static final class SpeechRequest {
		final String text;
		final String model_id;

		SpeechRequest(String text, String modelId) {
			this.text = text;
			this.model_id = modelId;
		}
	}
}
