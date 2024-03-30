package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.piper.ModelRepository;
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
			if (voiceMetadata.getGender() == Gender.MALE) {
				MaleList.add(voiceID);
			} else if (voiceMetadata.getGender() == Gender.FEMALE) {
				FemaleList.add(voiceID);
			} else {
				OtherList.add(voiceID);
			}
		}
	}

	public void removeModel(ModelRepository.ModelLocal modelLocal) {
		for (ModelRepository.VoiceMetadata voiceMetadata : modelLocal.getVoiceMetadata()) {
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
