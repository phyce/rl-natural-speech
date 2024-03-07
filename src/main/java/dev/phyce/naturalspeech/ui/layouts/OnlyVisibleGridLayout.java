package dev.phyce.naturalspeech.ui.layouts;

import java.awt.*;
import java.util.function.Function;

public class OnlyVisibleGridLayout extends GridLayout {

	public OnlyVisibleGridLayout() {
		this(1, 0, 0, 0);
	}

	public OnlyVisibleGridLayout(int rows, int columns) {
		this(rows, columns, 0, 0);
	}

	public OnlyVisibleGridLayout(int rows, int columns, int horizontalGap, int verticalGap) {
		super(rows, columns, horizontalGap, verticalGap);
	}

	// Pretends invisible components don't exist during layout
	private int getVisibleComponentCount(Container parent) {
		int count = 0;
		for (Component component : parent.getComponents()) if (component.isVisible()) count++;
		return count;
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		synchronized (parent.getTreeLock()) {
			return calculateSize(parent, Component::getPreferredSize);
		}
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		synchronized (parent.getTreeLock()) {
			return calculateSize(parent, Component::getMinimumSize);
		}
	}

	@Override
	public void layoutContainer(Container parent) {
		synchronized (parent.getTreeLock()) {
			final Insets insets = parent.getInsets();
			final int componentCount = parent.getComponentCount();
			final int visibleComponentCount = getVisibleComponentCount(parent);
			int rowCount = getRows();
			int columnCount = getColumns();

			if (visibleComponentCount == 0) return;

			if (rowCount > 0) columnCount = (visibleComponentCount + rowCount - 1) / rowCount;
			else rowCount = (visibleComponentCount + columnCount - 1) / columnCount;

			final int horizontalGap = getHgap();
			final int verticalGap = getVgap();

			// scaling factors
			final Dimension preferredDimension = preferredLayoutSize(parent);
			final Insets parentInsets = parent.getInsets();
			int widthBorder = parentInsets.left + parentInsets.right;
			int heightBorder = parentInsets.top + parentInsets.bottom;
			final double widthScale = (1.0 * parent.getWidth() - widthBorder) / (preferredDimension.width - widthBorder);
			final double heightScale = (1.0 * parent.getHeight() - heightBorder) / (preferredDimension.height - heightBorder);

			final int[] widths = new int[columnCount];
			final int[] heights = new int[rowCount];

			// calculate dimensions for all components + apply scaling
			{
				int visibleIndex = 0;
				int trueIndex = 0;
				while (trueIndex < componentCount) {
					final Component comp = parent.getComponent(trueIndex);

					if (!comp.isVisible()) {
						trueIndex++;
						continue;
					}

					final int row = visibleIndex / columnCount;
					final int column = visibleIndex % columnCount;
					final Dimension dimension = comp.getPreferredSize();

					dimension.width = (int) (widthScale * dimension.width);
					dimension.height = (int) (heightScale * dimension.height);

					if (widths[column] < dimension.width) widths[column] = dimension.width;
					if (heights[row] < dimension.height) heights[row] = dimension.height;

					visibleIndex++;
					trueIndex++;
				}
			}

			// Apply new bounds to all child components
			int visibleIndex = 0;
			int trueIndex = 0;
			while (trueIndex < componentCount) {
				final Component comp = parent.getComponent(trueIndex);

				if (!comp.isVisible()) {
					trueIndex++;
					continue;
				}

				final int row = visibleIndex / columnCount;
				final int column = visibleIndex % columnCount;
				final int x = insets.left + column * (widths[column] + horizontalGap);
				final int y = insets.top + row * (heights[row] + verticalGap);
				comp.setBounds(x, y, widths[column], heights[row]);
				visibleIndex++;
				trueIndex++;
			}
		}
	}

	/**
	 * Calculate outer size of the layout based on it's children and sizer
	 *
	 * @param parent parent component
	 * @param sizer  functioning returning dimension of the child component
	 * @return outer size
	 */
	private Dimension calculateSize(final Container parent, final Function<Component, Dimension> sizer) {
		final int visibleComponentCount = getVisibleComponentCount(parent);
		final int componentCount = parent.getComponentCount();
		int rowCount = getRows();
		int columnCount = getColumns();

		if (rowCount > 0) columnCount = (visibleComponentCount + rowCount - 1) / rowCount;
		else rowCount = (visibleComponentCount + columnCount - 1) / columnCount;

		final int[] width = new int[columnCount];
		final int[] height = new int[rowCount];

		// Calculate dimensions for all components
		int visibleIndex = 0;
		int trueIndex = 0;
		while (trueIndex < componentCount) {
			final Component comp = parent.getComponent(trueIndex);

			if (!comp.isVisible()) {
				trueIndex++;
				continue;
			}

			final int row = visibleIndex / columnCount;
			final int column = visibleIndex % columnCount;
			final Dimension dimension = sizer.apply(comp);

			if (width[column] < dimension.width) width[column] = dimension.width;
			if (height[row] < dimension.height) height[row] = dimension.height;

			visibleIndex++;
			trueIndex++;
		}

		// Calculate total width and height of the layout
		int netWidth = 0;

		for (int j = 0; j < columnCount; j++) netWidth += width[j];

		int netHeight = 0;

		for (int i = 0; i < rowCount; i++) netHeight += height[i];

		final Insets insets = parent.getInsets();

		// Apply insets and horizontal and vertical gap to layout
		return new Dimension(
			insets.left + insets.right + netWidth + (columnCount - 1) * getHgap(),
			insets.top + insets.bottom + netHeight + (rowCount - 1) * getVgap()
		);
	}
}
