/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.spi;

import al.clk.gui.api.API;

public interface PasteHandler {
	boolean accept(Object obj);

	void paste(API api,
	           Object obj);
}
