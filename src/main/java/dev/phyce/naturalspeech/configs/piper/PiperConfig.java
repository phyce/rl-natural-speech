package dev.phyce.naturalspeech.configs.piper;

import dev.phyce.naturalspeech.configs.json.piper.PiperConfigDatum;
import lombok.Getter;
import lombok.Setter;

public class PiperConfig {

	@Getter
	@Setter
	private boolean enabled;
	@Getter
	private String modelName;
	@Getter
	@Setter
	private int processCount;

	public static PiperConfig fromDatum(PiperConfigDatum datum) {
		return new PiperConfig(datum);
	}

	public PiperConfigDatum toDatum() {
		return new PiperConfigDatum(this.modelName, this.enabled, this.processCount);
	}

	private PiperConfig(PiperConfigDatum datum) {
		this.enabled = datum.isEnabled();
		this.modelName = datum.getModelName();
		this.processCount = datum.getProcessCount();
	}
}
