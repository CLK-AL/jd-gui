/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.mainpanel;

import al.clk.gui.api.model.Container;
import al.clk.gui.service.extension.ExtensionService;
import al.clk.gui.spi.PanelFactory;

import java.util.Collection;
import java.util.HashMap;

public class PanelFactoryService {
	protected static final PanelFactoryService           PANEL_FACTORY_SERVICE = new PanelFactoryService();
	protected              HashMap<String, PanelFactory> mapProviders          = new HashMap<>();

	protected PanelFactoryService() {
		Collection<PanelFactory> providers = ExtensionService.getInstance()
		                                                     .load(PanelFactory.class);

		for (PanelFactory provider : providers) {
			for (String type : provider.getTypes()) {
				mapProviders.put(type,
				                 provider);
			}
		}
	}

	public static PanelFactoryService getInstance() {return PANEL_FACTORY_SERVICE;}

	public PanelFactory get(Container container) {
		PanelFactory factory = mapProviders.get(container.getType());
		return (factory != null)
		       ? factory
		       : mapProviders.get("default");
	}
}
