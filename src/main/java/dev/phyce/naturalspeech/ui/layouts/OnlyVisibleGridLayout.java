package dev.phyce.naturalspeech.ui.layouts;

import java.awt.*;
import java.util.function.Function;

public class OnlyVisibleGridLayout extends GridLayout {

	public OnlyVisibleGridLayout() {
		this(1, 0, 0, 0);
	}

	public OnlyVisibleGridLayout(int rows, int cols) {
		this(rows, cols, 0, 0);
	}

	public OnlyVisibleGridLayout(int rows, int cols, int hgap, int vgap) {
		super(rows, cols, hgap, vgap);
	}

	// Pretends invisible components don't exist during layout
	private int getVisibleComponentCount(Container parent) {
		int count = 0;
		for (Component c : parent.getComponents()) {
			if (c.isVisible()) {
				count++;
			}
		}
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
			final int ncomponents = parent.getComponentCount();
			final int ncomponents_visible = getVisibleComponentCount(parent);
			int nrows = getRows();
			int ncols = getColumns();

			if (ncomponents_visible == 0) {
				return;
			}

			if (nrows > 0) {
				ncols = (ncomponents_visible + nrows - 1) / nrows;
			} else {
				nrows = (ncomponents_visible + ncols - 1) / ncols;
			}

			final int hgap = getHgap();
			final int vgap = getVgap();

			// scaling factors
			final Dimension pd = preferredLayoutSize(parent);
			final Insets parentInsets = parent.getInsets();
			int wborder = parentInsets.left + parentInsets.right;
			int hborder = parentInsets.top + parentInsets.bottom;
			final double sw = (1.0 * parent.getWidth() - wborder) / (pd.width - wborder);
			final double sh = (1.0 * parent.getHeight() - hborder) / (pd.height - hborder);

			final int[] w = new int[ncols];
			final int[] h = new int[nrows];

			// calculate dimensions for all components + apply scaling
			{
				int visible_index = 0;
				int true_index = 0;
				while (true_index < ncomponents) {
					final Component comp = parent.getComponent(true_index);

					if (!comp.isVisible()) {
						true_index++;
						continue;
					}

					final int r = visible_index / ncols;
					final int c = visible_index % ncols;
					final Dimension d = comp.getPreferredSize();
					d.width = (int) (sw * d.width);
					d.height = (int) (sh * d.height);

					if (w[c] < d.width) {
						w[c] = d.width;
					}

					if (h[r] < d.height) {
						h[r] = d.height;
					}
					visible_index++;
					true_index++;
				}
			}

			// Apply new bounds to all child components
			int visible_index = 0;
			int true_index = 0;
			while (true_index < ncomponents) {
				final Component comp = parent.getComponent(true_index);

				if (!comp.isVisible()) {
					true_index++;
					continue;
				}

				final int r = visible_index / ncols;
				final int c = visible_index % ncols;
				final int x = insets.left + c * (w[c] + hgap);
				final int y = insets.top + r * (h[r] + vgap);
				comp.setBounds(x, y, w[c], h[r]);
				visible_index++;
				true_index++;
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
		final int ncomponents_visible = getVisibleComponentCount(parent);
		final int ncomponents = parent.getComponentCount();
		int nrows = getRows();
		int ncols = getColumns();

		if (nrows > 0) {
			ncols = (ncomponents_visible + nrows - 1) / nrows;
		} else {
			nrows = (ncomponents_visible + ncols - 1) / ncols;
		}

		final int[] w = new int[ncols];
		final int[] h = new int[nrows];

		// Calculate dimensions for all components
		int visible_index = 0;
		int true_index = 0;
		while (true_index < ncomponents) {
			final Component comp = parent.getComponent(true_index);

			if (!comp.isVisible()) {
				true_index++;
				continue;
			}

			final int r = visible_index / ncols;
			final int c = visible_index % ncols;
			final Dimension d = sizer.apply(comp);

			if (w[c] < d.width) {
				w[c] = d.width;
			}

			if (h[r] < d.height) {
				h[r] = d.height;
			}
			visible_index++;
			true_index++;
		}

		// Calculate total width and height of the layout
		int nw = 0;

		for (int j = 0; j < ncols; j++) {
			nw += w[j];
		}

		int nh = 0;

		for (int i = 0; i < nrows; i++) {
			nh += h[i];
		}

		final Insets insets = parent.getInsets();

		// Apply insets and horizontal and vertical gap to layout
		return new Dimension(
				insets.left + insets.right + nw + (ncols - 1) * getHgap(),
				insets.top + insets.bottom + nh + (nrows - 1) * getVgap());
	}
}
