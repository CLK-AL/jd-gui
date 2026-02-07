/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.view;

import al.clk.gui.util.swing.SwingUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

public class SaveAllSourcesView {
	public static final String       SAVE_ALL_SOURCES             = "Save All Sources";
	public static final String       SAVE_ALL_SOURCES_VIEW_CANCEL = "SaveAllSourcesView.cancel";
	public static final String       CANCEL                       = "Cancel";
	protected           JDialog      saveAllSourcesDialog;
	protected           JLabel       saveAllSourcesFromLabel;
	protected           JLabel       saveAllSourcesToLabel;
	protected           JProgressBar saveAllSourcesProgressBar;

	public SaveAllSourcesView(JFrame mainFrame,
	                          Runnable cancelCallback) {
		// Build GUI
		SwingUtil.invokeLater(() -> {
			saveAllSourcesDialog = new JDialog(mainFrame,
			                                   SAVE_ALL_SOURCES,
			                                   false);
			saveAllSourcesDialog.setResizable(false);
			saveAllSourcesDialog.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					cancelCallback.run();
				}
			});

			Box vbox = Box.createVerticalBox();
			vbox.setBorder(BorderFactory.createEmptyBorder(15,
			                                               15,
			                                               15,
			                                               15));
			saveAllSourcesDialog.add(vbox);

			Box hbox = Box.createHorizontalBox();
			hbox.add(saveAllSourcesFromLabel = new JLabel());
			hbox.add(Box.createHorizontalGlue());
			vbox.add(hbox);

			hbox = Box.createHorizontalBox();
			hbox.add(saveAllSourcesToLabel = new JLabel());
			hbox.add(Box.createHorizontalGlue());
			vbox.add(hbox);

			vbox.add(Box.createVerticalStrut(10));

			vbox.add(saveAllSourcesProgressBar = new JProgressBar());

			vbox.add(Box.createVerticalStrut(15));

			// Button "Cancel"
			hbox = Box.createHorizontalBox();
			hbox.add(Box.createHorizontalGlue());
			JButton saveAllSourcesCancelButton = new JButton(CANCEL);
			Action saveAllSourcesCancelActionListener = new AbstractAction() {
				public void actionPerformed(ActionEvent actionEvent) {
					cancelCallback.run();
					saveAllSourcesDialog.setVisible(false);
				}
			};
			saveAllSourcesCancelButton.addActionListener(saveAllSourcesCancelActionListener);
			hbox.add(saveAllSourcesCancelButton);
			vbox.add(hbox);

			// Last setup
			JRootPane rootPane = saveAllSourcesDialog.getRootPane();
			rootPane.setDefaultButton(saveAllSourcesCancelButton);
			rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
			        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,
			                                    0),
			             SAVE_ALL_SOURCES_VIEW_CANCEL);
			rootPane.getActionMap()
			        .put(SAVE_ALL_SOURCES_VIEW_CANCEL,
			             saveAllSourcesCancelActionListener);

			// Prepare to display
			saveAllSourcesDialog.pack();
		});
	}

	public void show(File fromFile,
	                 File toFile) {
		SwingUtil.invokeLater(() -> {
			// Init
			String fromFilePath = fromFile.getAbsolutePath();
			saveAllSourcesFromLabel.setText(String.format("Saving '%s'",
			                                              fromFilePath));

			String toFilePath = toFile.getAbsolutePath();
			saveAllSourcesToLabel.setText(String.format("to '%s'...",
			                                            toFilePath));

			saveAllSourcesProgressBar.setValue(0);
			saveAllSourcesProgressBar.setMaximum(10);
			saveAllSourcesProgressBar.setIndeterminate(true);
			saveAllSourcesDialog.pack();
			// Show
			saveAllSourcesDialog.setLocationRelativeTo(saveAllSourcesDialog.getParent());
			saveAllSourcesDialog.setVisible(true);
		});
	}

	public boolean isVisible() {return saveAllSourcesDialog.isVisible();}

	public void setMaxValue(int maxValue) {
		SwingUtil.invokeLater(() -> {
			if (maxValue > 0) {
				saveAllSourcesProgressBar.setMaximum(maxValue);
				saveAllSourcesProgressBar.setIndeterminate(false);
			} else {
				saveAllSourcesProgressBar.setIndeterminate(true);
			}
		});
	}

	public void updateProgressBar(int value) {
		SwingUtil.invokeLater(() -> {
			saveAllSourcesProgressBar.setValue(value);
		});
	}

	public void hide() {
		SwingUtil.invokeLater(() -> {
			saveAllSourcesDialog.setVisible(false);
		});
	}

	public void showActionFailedDialog() {
		SwingUtil.invokeLater(() -> {
			JOptionPane.showMessageDialog(saveAllSourcesDialog,
			                              "'Save All Sources' action failed.",
			                              "Error",
			                              JOptionPane.ERROR_MESSAGE);
		});
	}
}
