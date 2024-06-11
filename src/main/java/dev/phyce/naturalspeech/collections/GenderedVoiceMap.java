package dev.phyce.naturalspeech.collections;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GenderedVoiceMap {

	private final Set<VoiceID> maleList = Collections.synchronizedSet(new HashSet<>());
	private final Set<VoiceID> femaleList = Collections.synchronizedSet(new HashSet<>());
	private final Set<VoiceID> otherList = Collections.synchronizedSet(new HashSet<>());

	public void add(VoiceID voiceID, Gender gender) {
		if (gender == Gender.MALE) {
			if (maleList.contains(voiceID)) {
				log.warn("Attempting to add duplicate {} to male voice list", voiceID);
			}
			else {
				maleList.add(voiceID);
			}
		}
		else if (gender == Gender.FEMALE) {
			if (femaleList.contains(voiceID)) {
				log.warn("Attempting to add duplicate {} to female voice list", voiceID);
			}
			else {
				femaleList.add(voiceID);
			}
		}
		else {
			if (otherList.contains(voiceID)) {
				log.warn("Attempting to add duplicate other {} to other voice list", voiceID);
			}
			else {
				otherList.add(voiceID);
			}
		}
	}

	public void remove(VoiceID voiceID) {
		maleList.remove(voiceID);
		femaleList.remove(voiceID);
		otherList.remove(voiceID);
	}

	public Set<VoiceID> find(Gender gender) {
		switch (gender) {
			case MALE:
				return Collections.unmodifiableSet(maleList);
			case FEMALE:
				return Collections.unmodifiableSet(femaleList);
			default:
				return Collections.unmodifiableSet(otherList);
		}
	}
}
