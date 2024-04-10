package dev.phyce.naturalspeech.tts.wsapi4;

//public class SAPI4Engine implements TextToSpeechEngine {
//
//	private final SAPI4Repository sapi4Repository;
//	private final NaturalSpeechRuntimeConfig runtimeConfig;
//	private final AudioEngine audioEngine;
//	private final VoiceManager voiceManager;
//
//	private final Map<String, SpeechAPI4> sapi4s = new HashMap<>();
//
//	// "microsoft" does not denote any specific models and has no lifetime
//	// The VoiceID::ids are the actual models and can be available or not.
//	// We want "microsoft:sam", not "sam:0"
//	// A more generalized approach can be done at a later time.
//	public static final String SAPI4_MODEL_NAME = "microsoft";
//
//	@Inject
//	public SAPI4Engine(
//		SAPI4Repository sapi4Repository,
//		NaturalSpeechRuntimeConfig runtimeConfig,
//		AudioEngine audioEngine, VoiceManager voiceManager
//	) {
//		this.sapi4Repository = sapi4Repository;
//		this.runtimeConfig = runtimeConfig;
//		this.audioEngine = audioEngine;
//		this.voiceManager = voiceManager;
//	}
//
//	@Override
//	public void startup() {
//		// SAPI4 models don't have lifecycles and does not need to be cleared on stop
//		{
//			List<String> voiceNames = sapi4Repository.getVoices();
//			if (voiceNames != null) {
//				for (String voiceName : voiceNames) {
//					SpeechAPI4 sapi = SpeechAPI4.start(audioEngine, voiceName, runtimeConfig.getSAPI4Path());
//					sapi4s.put(voiceName, sapi);
//					voiceManager.registerVoiceID(new VoiceID(SAPI4_MODEL_NAME, voiceName), sapi.getGender());
//				}
//			}
//		}
//	}
//
//	@Override
//	public void shutdown() {
//
//	}
//
//	@Override
//	public boolean canSpeak() {
//		return false;
//	}
//
//	@Override
//	public boolean speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName) {
//		return false;
//	}
//
//	@Override
//	public void cancelLine(String lineName) {
//
//	}
//
//	@Override
//	public void cancelAll() {
//
//	}
//
//	@Override
//	public ListenableFuture<Void> onStartup() {
//		return null;
//	}
//
//	@Override
//	public ListenableFuture<Void> onShutdown() {
//		return null;
//	}
//
//	@Override
//	public ListenableFuture<Void> onCrash() {
//		return null;
//	}
//}
