package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.piper.PiperRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GenderedVoiceMap {

	public List<VoiceID> MaleList = new ArrayList<>();
	public List<VoiceID> FemaleList = new ArrayList<>();
	public List<VoiceID> OtherList = new ArrayList<>();

	public void addModel(PiperRepository.ModelLocal modelLocal) {
		for (PiperRepository.VoiceMetadata voiceMetadata : modelLocal.getVoiceMetadata()) {
			VoiceID voiceID = voiceMetadata.toVoiceID();
			if (voiceMetadata.getGender() == Gender.MALE) {
				MaleList.add(voiceID);
			} else if (voiceMetadata.getGender() == Gender.FEMALE) {
				FemaleList.add(voiceID);
			} else {
				OtherList.add(voiceID);
			}
		}
	}

	public void removeModel(PiperRepository.ModelLocal modelLocal) {
		for (PiperRepository.VoiceMetadata voiceMetadata : modelLocal.getVoiceMetadata()) {
			VoiceID voiceID = voiceMetadata.toVoiceID();
			if (voiceMetadata.getGender() == Gender.MALE) {
				MaleList.remove(voiceID);
			} else if (voiceMetadata.getGender() == Gender.FEMALE) {
				FemaleList.remove(voiceID);
			} else {
				OtherList.remove(voiceID);
			}
		}
	}

	public List<VoiceID> find(Gender gender) {
		if (gender == Gender.MALE) {
			return Collections.unmodifiableList(MaleList);
		} else if (gender == Gender.FEMALE) {
			return Collections.unmodifiableList(FemaleList);
		} else {
			return Collections.unmodifiableList(OtherList);
		}
	}
}
