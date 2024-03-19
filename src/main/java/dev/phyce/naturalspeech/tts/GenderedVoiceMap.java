package dev.phyce.naturalspeech.tts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GenderedVoiceMap {

	public List<VoiceID> MaleList = new ArrayList<>();
	public List<VoiceID> FemaleList = new ArrayList<>();
	public List<VoiceID> OtherList = new ArrayList<>();

	public void addModel(ModelRepository.ModelLocal modelLocal) {
		for (ModelRepository.VoiceMetadata voiceMetadata : modelLocal.getVoiceMetadata()) {
			VoiceID voiceID = voiceMetadata.toVoiceID();
			if (voiceMetadata.getGender() == ModelRepository.Gender.MALE) {
				MaleList.add(voiceID);
			} else if (voiceMetadata.getGender() == ModelRepository.Gender.FEMALE) {
				FemaleList.add(voiceID);
			} else {
				OtherList.add(voiceID);
			}
		}
	}

	public void removeModel(ModelRepository.ModelLocal modelLocal) {
		for (ModelRepository.VoiceMetadata voiceMetadata : modelLocal.getVoiceMetadata()) {
			VoiceID voiceID = voiceMetadata.toVoiceID();
			if (voiceMetadata.getGender() == ModelRepository.Gender.MALE) {
				MaleList.remove(voiceID);
			} else if (voiceMetadata.getGender() == ModelRepository.Gender.FEMALE) {
				FemaleList.remove(voiceID);
			} else {
				OtherList.remove(voiceID);
			}
		}
	}

	public List<VoiceID> find(ModelRepository.Gender gender) {
		if (gender == ModelRepository.Gender.MALE) {
			return Collections.unmodifiableList(MaleList);
		} else if (gender == ModelRepository.Gender.FEMALE) {
			return Collections.unmodifiableList(FemaleList);
		} else {
			return Collections.unmodifiableList(OtherList);
		}
	}
}
