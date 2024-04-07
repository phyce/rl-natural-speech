package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.piper.PiperRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GenderedVoiceMap {

	private final Set<VoiceID> maleList = new HashSet<>();
	private final Set<VoiceID> femaleList = new HashSet<>();
	private final Set<VoiceID> otherList = new HashSet<>();

	public void addModelLocal(PiperRepository.ModelLocal modelLocal) {
		for (PiperRepository.PiperVoiceMetadata piperVoiceMetadata : modelLocal.getPiperVoiceMetadata()) {
			VoiceID voiceID = piperVoiceMetadata.toVoiceID();
			Gender gender = piperVoiceMetadata.getGender();
			addVoiceID(gender, voiceID);
		}
	}

	public void addVoiceID(Gender gender, VoiceID voiceID) {
		if (gender == Gender.MALE) {
			if (maleList.contains(voiceID)) {
				log.warn("Attempting to add duplicate {} to male voice list", voiceID);
			} else {
				maleList.add(voiceID);
			}
		} else if (gender == Gender.FEMALE) {
			if (femaleList.contains(voiceID)) {
				log.warn("Attempting to add duplicate {} to female voice list", voiceID);
			} else {
				femaleList.add(voiceID);
			}
		} else {
			if (otherList.contains(voiceID)) {
				log.warn("Attempting to add duplicate other {} to other voice list", voiceID);
			} else {
				otherList.add(voiceID);
			}
		}
	}

	public void removeVoiceID(VoiceID voiceID) {
		maleList.remove(voiceID);
		femaleList.remove(voiceID);
		otherList.remove(voiceID);
	}

	public void removeModelLocal(PiperRepository.ModelLocal modelLocal) {
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

	public Set<VoiceID> find(Gender gender) {
		if (gender == Gender.MALE) {
			return Collections.unmodifiableSet(maleList);
		} else if (gender == Gender.FEMALE) {
			return Collections.unmodifiableSet(femaleList);
		} else {
			return Collections.unmodifiableSet(otherList);
		}
	}
}
