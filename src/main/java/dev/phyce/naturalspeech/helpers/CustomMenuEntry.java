package dev.phyce.naturalspeech.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;

@Slf4j
public class CustomMenuEntry {
	@Getter
	private final String text;
	private final int index;
	@Getter
	private final Consumer<CustomMenuEntry> action;
	@Getter
	private final List<CustomMenuEntry> children = new ArrayList<>();

	public CustomMenuEntry(String text, int index) {
		this.text = text;
		this.action = entry -> {
			log.info("no function for custom menu entry main");
		};
		this.index = index;
	}

	public CustomMenuEntry(String text, int index, Consumer<CustomMenuEntry> action) {
		this.text = text;
		this.action = action;
		this.index = index;
	}

	public void addChild(CustomMenuEntry child) {
		children.add(child);
	}

	public void addMenuEntry(Client client, String option, Consumer<CustomMenuEntry> action,
							 CustomMenuEntry[] children) {

		MenuEntry parentEntry = client.createMenuEntry(this.index)
			.setOption(option)
			.setTarget("")
			.setType(MenuAction.RUNELITE)
			.onClick(consumer -> action.accept(this));

		for (CustomMenuEntry child : children) {
			MenuEntry childEntry = client.createMenuEntry(child.index)
				.setOption(child.text)
				.setTarget("")
				.setType(MenuAction.RUNELITE)
				.onClick(consumer -> child.action.accept(child));

//			childEntry.setParent(parentEntry);
		}
	}

	public void addTo(Client client) {
		addMenuEntry(client, this.text, this.action, this.children.toArray(new CustomMenuEntry[0]));
	}
}