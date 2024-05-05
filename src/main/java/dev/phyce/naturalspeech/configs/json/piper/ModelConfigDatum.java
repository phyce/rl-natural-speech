package dev.phyce.naturalspeech.configs.json.piper;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

@Value
public class ModelConfigDatum {
	List<PiperConfigDatum> piperConfigData = new ArrayList<>();
}
