package dev.phyce.naturalspeech.userinterface.mainsettings;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import dev.phyce.naturalspeech.eventbus.PluginEventBus;
import dev.phyce.naturalspeech.eventbus.SubscribeWeak;
import dev.phyce.naturalspeech.events.PiperModelEngineEvent;
import dev.phyce.naturalspeech.events.PiperProcessEvent;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperProcess;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

@Slf4j
public class PiperModelMonitorItem extends JPanel {

	public interface Factory {
		PiperModelMonitorItem create(@NonNull PiperRepository.PiperModel modelEngine);
	}

	private PiperRepository.PiperModel piperModel;
	private final PluginEventBus pluginEventBus;
	private final JPanel processListPanel;
	public final Map<PiperProcess, JLabel> labelMap = new HashMap<>();

	@Inject
	public PiperModelMonitorItem(
		PluginEventBus pluginEventBus,
		@Assisted PiperRepository.PiperModel piperModel
	) {
		this.pluginEventBus = pluginEventBus;

		this.setLayout(new BorderLayout());

		// piper
		JLabel piperTitle = new JLabel(piperModel.getModelName());
		piperTitle.setFont(FontManager.getRunescapeBoldFont());
		this.add(piperTitle, BorderLayout.NORTH);

		// piperprocess of the piper
		this.processListPanel = new JPanel();
		this.processListPanel.setLayout(new DynamicGridLayout(0, 1));
		this.add(processListPanel, BorderLayout.SOUTH);

	}

	@SubscribeWeak
	public void on(PiperModelEngineEvent event) {
		if (!Objects.equals(event.getModel(), piperModel)) return;

		switch (event.getEvent()) {
			case STARTED:
				this.setVisible(true);
				break;
			case STOPPED:
				this.setVisible(false);
				break;
		}
	}

	@SubscribeWeak
	public void on(PiperProcessEvent event) {
		if (!Objects.equals(event.getModelEngine().getModel(), piperModel)) {return;}

		switch (event.getEvent()) {
			case SPAWNED:
				AddProcess(event.getProcess());
				break;
			case DIED:
				RemoveProcess(event.getProcess());
				break;
			case CRASHED: {
				JLabel label = labelMap.get(event.getProcess());
				label.setForeground(Color.RED);
				break;
			}
			case BUSY: {
				JLabel label = labelMap.get(event.getProcess());
				label.setForeground(Color.GREEN);
				break;
			}
			case DONE: {
				JLabel label = labelMap.get(event.getProcess());
				label.setForeground(null);
				break;
			}
		}
	}

	public void AddProcess(PiperProcess process) {
		log.debug("Labeling process {}", process);
		JLabel processLabel = new JLabel(process.toString());
		labelMap.put(process, processLabel);
		processListPanel.add(processLabel);
		processListPanel.revalidate();
	}

	private void RemoveProcess(PiperProcess process) {
		log.debug("Removing label for process {}", process);
		JLabel label = labelMap.remove(process);
		processListPanel.remove(label);
		processListPanel.revalidate();
	}

	//	private class ItemPiperProcessLifeTimeListener implements PiperModelEngine.PiperProcessLifetimeListener {
	//
	//
	//		public ItemPiperProcessLifeTimeListener(PiperModelEngine piper) {
	//			piper.getProcesses().forEach((pid, process) -> {
	//				AddProcess(process);
	//			});
	//		}
	//
	//		@Override
	//		public void onPiperProcessBusy(PiperProcess process) {
	//			JLabel label = labelMap.get(process);
	//			label.setForeground(Color.GREEN);
	//		}
	//
	//		@Override
	//		public void onPiperProcessDone(PiperProcess process) {
	//			JLabel label = labelMap.get(process);
	//			label.setForeground(null);
	//		}
	//
	//		@Override
	//		public void onPiperProcessCrash(PiperProcess process) {
	//			log.error("PiperListItem was alerted that {} crashed", process);
	//		}
	//
	//		@Override
	//		public void onPiperProcessStart(PiperProcess process) {
	//			AddProcess(process);
	//		}
	//
	//		@Override
	//		public void onPiperProcessExit(PiperProcess process) {
	//			RemoveProcess(process);
	//		}
	//	}


}
