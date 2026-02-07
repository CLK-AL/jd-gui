/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.controller;

import al.clk.gui.api.feature.LineNumberNavigable;
import al.clk.gui.model.configuration.Configuration;
import al.clk.gui.view.GoToView;

import javax.swing.*;
import java.util.function.IntConsumer;

public class GoToController {
	protected GoToView goToView;

	public GoToController(Configuration configuration,
	                      JFrame mainFrame) {
		// Create UI
		goToView = new GoToView(configuration,
		                        mainFrame);
	}

	public void show(LineNumberNavigable navigator,
	                 IntConsumer okCallback) {
		// Show
		goToView.show(navigator,
		              okCallback);
	}
}
