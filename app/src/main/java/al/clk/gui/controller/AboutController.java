/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.controller;

import al.clk.gui.view.AboutView;

import javax.swing.*;

public class AboutController {
	protected AboutView aboutView;

	public AboutController(JFrame mainFrame) {
		// Create UI
		aboutView = new AboutView(mainFrame);
	}

	public void show() {
		// Show
		aboutView.show();
	}
}
