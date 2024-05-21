package dev.phyce.naturalspeech.configs;

import com.google.gson.JsonSyntaxException;
import java.util.Iterator;
import java.util.Vector;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.RuneLiteAPI;

@Slf4j
@Data
public class PiperConfig {

	private final Vector<ModelConfig> modelConfigs = new Vector<>();

	public static PiperConfig fromJSON(String json) throws JsonSyntaxException {
		return RuneLiteAPI.GSON.fromJson(json, PiperConfig.class);
	}

	public String toJSON() {
		return RuneLiteAPI.GSON.toJson(this);
	}

	public int getModelProcessCount(String modelName) {
		return modelConfigs.stream()
			.filter(p -> p.getModelName().equals(modelName))
			.findFirst()
			.map(ModelConfig::getProcessCount)
			.orElse(1);
	}

	public void setModelProcessCount(String modelName, int processCount) {
		// look for existing config
		for (ModelConfig p : modelConfigs) {
			if (p.getModelName().equals(modelName)) {
				p.setProcessCount(processCount);
				return;
			}
		}

		initializePiperConfig(modelName);
		setModelProcessCount(modelName, processCount);
	}

	public boolean isModelEnabled(String modelName) {
		return modelConfigs.stream().anyMatch(p -> p.getModelName().equals(modelName) && p.isEnabled());
	}

	public void setModelEnabled(String modelName, boolean enabled) {
		// look for existing config
		for (ModelConfig p : modelConfigs) {
			if (p.getModelName().equals(modelName)) {
				p.setEnabled(enabled);
				return;
			}
		}

		initializePiperConfig(modelName);
		setModelEnabled(modelName, enabled);
	}

	private void initializePiperConfig(String modelName) {
		// existing config wasn't found, make new
		ModelConfig modelConfig = new ModelConfig(modelName, false, 1);
		modelConfigs.add(modelConfig);
	}

	public void resetPiperConfig(String modelName) {
		// look for existing config
		Iterator<ModelConfig> iter = modelConfigs.iterator();
		while (iter.hasNext()) {
			ModelConfig modelConfig = iter.next();
			if (modelConfig.getModelName().equals(modelName)) {
				iter.remove();
				break;
			}
		}

		// existing config wasn't found, make new
		ModelConfig modelConfig = new ModelConfig(modelName, false, 1);
		modelConfigs.add(modelConfig);
	}

	@AllArgsConstructor
	@Data
	public static class ModelConfig {
		private String modelName;
		private boolean enabled;
		private int processCount;
	}
}
