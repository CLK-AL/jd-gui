/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.spi;

import al.clk.gui.api.API;
import al.clk.gui.api.model.Container;

import java.nio.file.Path;

public interface ContainerFactory {
	String getType();

	boolean accept(API api,
	               Path rootPath);

	Container make(API api,
	               Container.Entry parentEntry,
	               Path rootPath);
}
