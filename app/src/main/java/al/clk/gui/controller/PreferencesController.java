/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.controller;

import al.clk.gui.model.configuration.Configuration;
import al.clk.gui.spi.PreferencesPanel;
import al.clk.gui.view.PreferencesView;

import javax.swing.*;
import java.util.Collection;

public class PreferencesController {
	protected PreferencesView preferencesView;

	public PreferencesController(Configuration configuration,
	                             JFrame mainFrame,
	                             Collection<PreferencesPanel> panels) {
		// Create UI
		preferencesView = new PreferencesView(configuration,
		                                      mainFrame,
		                                      panels);
	}

	public void show(Runnable okCallback) {
		// Show
		preferencesView.show(okCallback);
	}
}
