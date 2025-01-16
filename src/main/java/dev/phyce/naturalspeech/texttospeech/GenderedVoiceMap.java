package dev.phyce.naturalspeech.texttospeech;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GenderedVoiceMap {

	private final Set<VoiceID> maleList = Collections.synchronizedSet(new HashSet<>());
	private final Set<VoiceID> femaleList = Collections.synchronizedSet(new HashSet<>());
	private final Set<VoiceID> otherList = Collections.synchronizedSet(new HashSet<>());

	public void put(VoiceID voiceID, Gender gender) {
		if (gender == Gender.MALE) {
			maleList.add(voiceID);
		}
		else if (gender == Gender.FEMALE) {
			femaleList.add(voiceID);
		}
		else {
			otherList.add(voiceID);
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
