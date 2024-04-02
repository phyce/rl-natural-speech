package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.piper.PiperRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GenderedVoiceMap {

	private final List<VoiceID> maleList = new ArrayList<>();
	private final List<VoiceID> femaleList = new ArrayList<>();
	private final List<VoiceID> otherList = new ArrayList<>();

	public void addModel(PiperRepository.ModelLocal modelLocal) {
		for (PiperRepository.PiperVoiceMetadata piperVoiceMetadata : modelLocal.getPiperVoiceMetadata()) {
			VoiceID voiceID = piperVoiceMetadata.toVoiceID();
			if (piperVoiceMetadata.getGender() == Gender.MALE) {
				maleList.add(voiceID);
			} else if (piperVoiceMetadata.getGender() == Gender.FEMALE) {
				femaleList.add(voiceID);
			} else {
				otherList.add(voiceID);
			}
		}
	}

	public void removeModel(PiperRepository.ModelLocal modelLocal) {
		for (PiperRepository.PiperVoiceMetadata piperVoiceMetadata : modelLocal.getPiperVoiceMetadata()) {
			VoiceID voiceID = piperVoiceMetadata.toVoiceID();
			if (piperVoiceMetadata.getGender() == Gender.MALE) {
				maleList.remove(voiceID);
			} else if (piperVoiceMetadata.getGender() == Gender.FEMALE) {
				femaleList.remove(voiceID);
			} else {
				otherList.remove(voiceID);
			}
		}
	}

	public List<VoiceID> find(Gender gender) {
		if (gender == Gender.MALE) {
			return Collections.unmodifiableList(maleList);
		} else if (gender == Gender.FEMALE) {
			return Collections.unmodifiableList(femaleList);
		} else {
			return Collections.unmodifiableList(otherList);
		}
	}
}
