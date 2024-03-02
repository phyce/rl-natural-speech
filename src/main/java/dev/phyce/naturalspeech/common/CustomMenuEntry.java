package dev.phyce.naturalspeech.common;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CustomMenuEntry {
	@Getter
	private String text;
	private int index;
	@Getter
	private Consumer<CustomMenuEntry> action;
	@Getter
	private List<CustomMenuEntry> children = new ArrayList<>();

	public CustomMenuEntry(String text, int index) {
		this.text = text;
		this.action = entry -> {System.out.println("no function for custom menu entry main");};
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

	public void addMenuEntry(Client client, String option, Consumer<CustomMenuEntry> action, CustomMenuEntry[] children) {

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

			childEntry.setParent(parentEntry);
		}
	}

	public void addTo(Client client) {
		addMenuEntry(client, this.text, this.action, this.children.toArray(new CustomMenuEntry[0]));
	}
}