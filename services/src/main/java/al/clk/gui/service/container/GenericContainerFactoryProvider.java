/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.container;

import al.clk.gui.api.API;
import al.clk.gui.api.model.Container;
import al.clk.gui.model.container.GenericContainer;
import al.clk.gui.spi.ContainerFactory;

import java.nio.file.Path;

public class GenericContainerFactoryProvider
				implements ContainerFactory {
	@Override
	public String getType() {return "generic";}

	@Override
	public boolean accept(API api,
	                      Path rootPath) {return true;}

	@Override
	public Container make(API api,
	                      Container.Entry parentEntry,
	                      Path rootPath) {
		return new GenericContainer(api,
		                            parentEntry,
		                            rootPath);
	}
}
