/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.container;

import al.clk.gui.api.API;
import al.clk.gui.service.extension.ExtensionService;
import al.clk.gui.spi.ContainerFactory;

import java.nio.file.Path;
import java.util.Collection;

public class ContainerFactoryService {
	protected static final ContainerFactoryService      CONTAINER_FACTORY_SERVICE = new ContainerFactoryService();
	protected final        Collection<ContainerFactory> providers                 = ExtensionService.getInstance()
	                                                                                                .load(ContainerFactory.class);

	public static ContainerFactoryService getInstance() {return CONTAINER_FACTORY_SERVICE;}

	public ContainerFactory get(API api,
	                            Path rootPath) {
		for (ContainerFactory containerFactory : providers) {
			if (containerFactory.accept(api,
			                            rootPath)) {
				return containerFactory;
			}
		}

		return null;
	}
}
