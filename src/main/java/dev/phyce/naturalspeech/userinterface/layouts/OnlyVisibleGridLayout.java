package dev.phyce.naturalspeech.userinterface.layouts;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
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
		for (Component component : parent.getComponents()) {
			if (component.isVisible()) {
				count++;
			}
		}
		return count;
	}

	private Component[] getVisibleComponents(Container parent) {
		Component[] components = parent.getComponents();

		int count = getVisibleComponentCount(parent);
		Component[] visibleComponents = new Component[count];
		int index = 0;
		for (Component component : components) {
			if (component.isVisible()) {
				visibleComponents[index] = component;
				index++;
			}
		}
		return visibleComponents;
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
			// default all children bounds to 0,0
			// only update the visible children
			for (Component comp : parent.getComponents()) {
				comp.setBounds(0, 0, 0, 0);
			}

			final Insets insets = parent.getInsets();
			final int ncomponents = getVisibleComponentCount(parent);
			final Component[] components = getVisibleComponents(parent);

			int nrows = getRows();
			int ncols = getColumns();

			if (ncomponents == 0) {
				return;
			}

			if (nrows > 0) {
				ncols = (ncomponents + nrows - 1) / nrows;
			}
			else {
				nrows = (ncomponents + ncols - 1) / ncols;
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
			for (int i = 0; i < ncomponents; i++) {
				final int r = i / ncols;
				final int c = i % ncols;
				final Component comp = components[i];
				final Dimension d = comp.getPreferredSize();
				d.width = (int) (sw * d.width);
				d.height = (int) (sh * d.height);

				if (w[c] < d.width) {
					w[c] = d.width;
				}

				if (h[r] < d.height) {
					h[r] = d.height;
				}
			}

			// Apply new bounds to all child components
			Component comp = null;
			for (int c = 0, x = insets.left; c < ncols; c++) {
				for (int r = 0, y = insets.top; r < nrows; r++) {
					int i = r * ncols + c;

					if (i < ncomponents) {
						comp = components[i];
						comp.setBounds(x, y, w[c], h[r]);
					}

					y += h[r] + vgap;
				}

				x += w[c] + hgap;
			}
		}
	}

	/**
	 * Calculate outer size of the layout based on it's children and sizer
	 *
	 * @param parent parent component
	 * @param sizer  functioning returning dimension of the child component
	 *
	 * @return outer size
	 */
	private Dimension calculateSize(final Container parent, final Function<Component, Dimension> sizer) {
		final int ncomponents = getVisibleComponentCount(parent);
		final Component[] components = getVisibleComponents(parent);

		int nrows = getRows();
		int ncols = getColumns();

		if (nrows > 0) {
			ncols = (ncomponents + nrows - 1) / nrows;
		}
		else {
			nrows = (ncomponents + ncols - 1) / ncols;
		}

		final int[] w = new int[ncols];
		final int[] h = new int[nrows];

		// Calculate dimensions for all components
		for (int i = 0; i < ncomponents; i++) {
			final int r = i / ncols;
			final int c = i % ncols;
			final Component comp = components[i];
			final Dimension d = sizer.apply(comp);

			if (w[c] < d.width) {
				w[c] = d.width;
			}

			if (h[r] < d.height) {
				h[r] = d.height;
			}
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
