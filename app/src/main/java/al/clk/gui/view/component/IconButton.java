/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.view.component;

import javax.swing.*;
import java.awt.*;

public class IconButton
				extends JButton {
	protected static final Insets INSETS0 = new Insets(0,
	                                                   0,
	                                                   0,
	                                                   0);

	public IconButton(String text,
	                  Action action) {
		setFocusPainted(false);
		setBorderPainted(false);
		setMargin(INSETS0);
		setAction(action);
		setText(text);
	}

	public IconButton(Action action) {
		this(null,
		     action);
	}
}
