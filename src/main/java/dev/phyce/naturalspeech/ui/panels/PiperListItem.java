package dev.phyce.naturalspeech.ui.panels;

import dev.phyce.naturalspeech.tts.Piper;
import dev.phyce.naturalspeech.tts.PiperProcess;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

@Slf4j
public class PiperListItem extends JPanel {

	private final Piper piper;
	private final JPanel processListPanel;


	public PiperListItem(Piper piper) {
		this.piper = piper;

		this.setLayout(new BorderLayout());

		// piper
		JLabel piperTitle = new JLabel(piper.getModelLocal().getModelName());
		piperTitle.setFont(FontManager.getRunescapeBoldFont());
		this.add(piperTitle, BorderLayout.NORTH);

		// piperprocess of the piper
		this.processListPanel = new JPanel();
		this.processListPanel.setLayout(new DynamicGridLayout(0, 1));
		this.add(processListPanel, BorderLayout.SOUTH);

		// listener should not leak, do not need to call removePiperListener
		// when piper is removed in TextToSpeech this JPanel is removed by MainSettingsPanel
		// So the both will be garbage collected.
		ItemPiperProcessLifeTimeListener listener = new ItemPiperProcessLifeTimeListener(piper);
		piper.addPiperListener(listener);

	}

	private class ItemPiperProcessLifeTimeListener implements Piper.PiperProcessLifetimeListener {

		public final Map<PiperProcess, JLabel> labelMap = new HashMap<>();

		public ItemPiperProcessLifeTimeListener(Piper piper) {
			piper.getProcessMap().forEach((pid, process) -> {
				AddProcess(process);
			});
		}

		public void AddProcess(PiperProcess process) {
			log.info("Labeling process {}", process);
			JLabel processLabel = new JLabel(process.toString());
			labelMap.put(process, processLabel);
			processListPanel.add(processLabel);
			processListPanel.revalidate();
		}

		private void RemoveProcess(PiperProcess process) {
			log.info("Removing label for process {}", process);
			JLabel label = labelMap.remove(process);
			processListPanel.remove(label);
			processListPanel.revalidate();
		}

		@Override
		public void onPiperProcessBusy(PiperProcess process) {
			JLabel label = labelMap.get(process);
			label.setForeground(Color.GREEN);
		}

		@Override
		public void onPiperProcessDone(PiperProcess process) {
			JLabel label = labelMap.get(process);
			label.setForeground(null);
		}

		@Override
		public void onPiperProcessCrash(PiperProcess process) {
		}

		@Override
		public void onPiperProcessStart(PiperProcess process) {
			AddProcess(process);
		}

		@Override
		public void onPiperProcessExit(PiperProcess process) {
			RemoveProcess(process);
		}
	}


}
