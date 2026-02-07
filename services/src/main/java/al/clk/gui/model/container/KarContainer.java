/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.model.container;

import al.clk.gui.api.API;
import al.clk.gui.api.model.Container;

import java.nio.file.Path;

public class KarContainer
				extends GenericContainer {
	public KarContainer(API api,
	                    Container.Entry parentEntry,
	                    Path rootPath) {
		super(api,
		      parentEntry,
		      rootPath);
	}

	public String getType() {return "kar";}
}
