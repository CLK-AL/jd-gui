/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.api.model;

import javax.swing.*;

public interface TreeNodeData {
	String getLabel();

	String getTip();

	Icon getIcon();

	Icon getOpenIcon();
}