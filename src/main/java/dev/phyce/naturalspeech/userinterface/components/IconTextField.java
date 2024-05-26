/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package dev.phyce.naturalspeech.userinterface.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.text.Document;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.util.SwingUtil;
import org.apache.commons.lang3.StringUtils;

/**
 * This component is a FlatTextField with an icon on its left side, and a clear button (×) on its right side.
 */
public class IconTextField extends JPanel {
	// To support gifs, the icon needs to be wrapped in a JLabel
	private final JLabel iconWrapperLabel;
	private final FlatTextField textField;
	private final JButton clearButton;
	private final JButton suggestionButton;

	@Getter
	private final DefaultListModel<String> suggestionListModel;

	private final List<Runnable> clearListeners = new ArrayList<>();
	private final JLabel placeHolderTextField;

	public IconTextField() {
		setLayout(new BorderLayout());

		iconWrapperLabel = new JLabel();
		iconWrapperLabel.setPreferredSize(new Dimension(30, 0));
		iconWrapperLabel.setVerticalAlignment(JLabel.CENTER);
		iconWrapperLabel.setHorizontalAlignment(JLabel.CENTER);

		JPanel textFieldWrapper = new JPanel();
		textFieldWrapper.setLayout(new OverlayLayout(textFieldWrapper));

		textField = new FlatTextField();
		textField.setBorder(null);

		JPanel placeHolderWrapper = new JPanel();
		placeHolderWrapper.setBackground(new Color(0, 0, 0, 0));
		placeHolderWrapper.setLayout(new BorderLayout());

		placeHolderTextField = new JLabel();
		placeHolderTextField.setHorizontalAlignment(JLabel.LEADING);
		placeHolderTextField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		placeHolderWrapper.add(placeHolderTextField);

		textFieldWrapper.add(placeHolderWrapper);
		textFieldWrapper.add(textField);


		final JTextField innerTxt = textField.getTextField();
		innerTxt.removeMouseListener(innerTxt.getMouseListeners()[innerTxt.getMouseListeners().length - 1]);

		final MouseListener hoverEffect = new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent mouseEvent) {
				if (textField.isBlocked()) return;

				final Color hoverColor = textField.getHoverBackgroundColor();

				if (hoverColor != null) {
					IconTextField.super.setBackground(hoverColor);
					textField.setBackground(hoverColor, false);
				}
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent) {
				setBackground(textField.getBackgroundColor());
			}
		};

		textField.addMouseListener(hoverEffect);
		innerTxt.addMouseListener(hoverEffect);

		innerTxt.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				placeHolderWrapper.setVisible(false);
			}

			@Override
			public void focusLost(FocusEvent e) {
				placeHolderWrapper.setVisible(textField.getText().isEmpty());
			}
		});

		clearButton = createRHSButton(ColorScheme.PROGRESS_ERROR_COLOR, Color.PINK, FontManager.getRunescapeBoldFont());
		clearButton.setText("×");
		clearButton.addActionListener(evt -> {
			setText(null);

			for (Runnable listener : clearListeners) {listener.run();}
		});

		suggestionListModel = new DefaultListModel<>();
		suggestionListModel.addListDataListener(new ListDataListener() {
			@Override
			public void intervalAdded(ListDataEvent e) {
				updateContextButton();
			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				updateContextButton();
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				updateContextButton();
			}
		});

		JList<String> suggestionList = new JList<>();
		suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		suggestionList.setModel(suggestionListModel);
		suggestionList.addListSelectionListener(e ->
		{
			String val = suggestionList.getSelectedValue();
			if (val == null) return;

			textField.setText(val);
			textField.getTextField().selectAll();
			textField.getTextField().requestFocusInWindow();
		});

		JPopupMenu popup = new JPopupMenu();
		popup.setLightWeightPopupEnabled(true);
		popup.setLayout(new BorderLayout());
		popup.add(suggestionList, BorderLayout.CENTER);
		popup.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				popup.setVisible(false);
				suggestionList.clearSelection();
			}
		});

		suggestionButton = createRHSButton(ColorScheme.LIGHT_GRAY_COLOR, ColorScheme.MEDIUM_GRAY_COLOR,
			FontManager.getDefaultBoldFont());
		suggestionButton.setText("▾");
		suggestionButton.addActionListener(e -> {
			suggestionList.setPreferredSize(new Dimension(getWidth(), suggestionList.getPreferredSize().height));
			popup.show(IconTextField.this, 0, suggestionButton.getHeight());
			popup.revalidate();
			popup.requestFocusInWindow();
		});

		// Show the clear button when text is present, and hide again when empty
		textField.getTextField().getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				placeHolderWrapper.setVisible(textField.getText().isEmpty());
				updateContextButton();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				placeHolderWrapper.setVisible(textField.getText().isEmpty());
				updateContextButton();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				placeHolderWrapper.setVisible(textField.getText().isEmpty());
				updateContextButton();
			}
		});

		JPanel rhsButtons = new JPanel();
		rhsButtons.setBackground(new Color(0, 0, 0, 0));
		rhsButtons.setOpaque(false);
		rhsButtons.setLayout(new BorderLayout());
		rhsButtons.add(clearButton, BorderLayout.EAST);
		rhsButtons.add(suggestionButton, BorderLayout.WEST);
		updateContextButton();

		add(iconWrapperLabel, BorderLayout.WEST);
		add(textFieldWrapper, BorderLayout.CENTER);
		add(rhsButtons, BorderLayout.EAST);
	}

	private JButton createRHSButton(Color foregroundColor, Color rollover, Font font) {
		JButton button = new JButton();
		button.setPreferredSize(new Dimension(30, 0));
		button.setFont(font);
		button.setBorder(null);
		button.setRolloverEnabled(true);
		SwingUtil.removeButtonDecorations(button);
		button.setForeground(foregroundColor);

		button.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent mouseEvent) {
				button.setForeground(rollover);
				textField.dispatchEvent(mouseEvent);
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent) {
				button.setForeground(foregroundColor);
				textField.dispatchEvent(mouseEvent);
			}
		});

		return button;
	}

	private void updateContextButton() {
		boolean empty = StringUtils.isBlank(textField.getText());

		clearButton.setVisible(!empty);
		suggestionButton.setVisible(!suggestionListModel.isEmpty() && empty);
	}

	public void addActionListener(ActionListener actionListener) {
		textField.addActionListener(actionListener);
	}

	public void setIcon(ImageIcon imageIcon) {
		iconWrapperLabel.setIcon(imageIcon);
	}

	public String getText() {
		return textField.getText();
	}

	public void setText(String text) {
		assert SwingUtilities.isEventDispatchThread();
		textField.setText(text);
	}

	public void setPlaceholderText(String text) {
		assert SwingUtilities.isEventDispatchThread();
		placeHolderTextField.setText(text);
	}

	@Override
	public void setBackground(Color color) {
		if (color == null) return;

		super.setBackground(color);

		if (textField != null) textField.setBackground(color);
	}

	public void setHoverBackgroundColor(Color hoverBackgroundColor) {
		if (hoverBackgroundColor == null) return;

		this.textField.setHoverBackgroundColor(hoverBackgroundColor);
	}

	@Override
	public void addKeyListener(KeyListener keyListener) {
		textField.addKeyListener(keyListener);
	}

	public void addClearListener(Runnable clearListener) {
		clearListeners.add(clearListener);
	}

	@Override
	public void removeKeyListener(KeyListener keyListener) {
		textField.removeKeyListener(keyListener);
	}

	public void setEditable(boolean editable) {
		textField.setEditable(editable);
		if (!editable) super.setBackground(textField.getBackgroundColor());
	}

	@Override
	public boolean requestFocusInWindow() {
		super.requestFocusInWindow();
		return textField.requestFocusInWindow();
	}

	public Document getDocument() {
		return textField.getDocument();
	}

}
