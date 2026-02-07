/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.preferencespanel;

import al.clk.gui.service.extension.ExtensionService;
import al.clk.gui.spi.PreferencesPanel;

import java.util.Collection;
import java.util.HashMap;

public class PreferencesPanelService {
	protected static final PreferencesPanelService      PREFERENCES_PANEL_SERVICE = new PreferencesPanelService();
	protected final        Collection<PreferencesPanel> providers;

	protected PreferencesPanelService() {
		Collection<PreferencesPanel> list = ExtensionService.getInstance()
		                                                    .load(PreferencesPanel.class);

		list.removeIf(preferencesPanel -> !preferencesPanel.isActivated());

		HashMap<String, PreferencesPanel> map = new HashMap<>();

		for (PreferencesPanel panel : list) {
			map.put(panel.getPreferencesGroupTitle() + '$' + panel.getPreferencesPanelTitle(),
			        panel);
		}

		providers = map.values();
	}

	public static PreferencesPanelService getInstance() {return PREFERENCES_PANEL_SERVICE;}

	public Collection<PreferencesPanel> getProviders() {
		return providers;
	}
}
