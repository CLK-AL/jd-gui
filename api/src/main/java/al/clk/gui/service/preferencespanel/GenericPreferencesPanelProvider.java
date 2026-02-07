/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.preferencespanel;

import al.clk.gui.spi.PreferencesPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class GenericPreferencesPanelProvider<Source extends Preference>
				extends JPanel
				implements PreferencesPanel,
				           GenericPreferencesPanel {

	private static LinkedHashMap<Preference, List<AbstractButton>> abstractButtonsByPreferenceMap = null;
	protected      PreferencesPanel.PreferencesPanelChangeListener listener                       = null;

	public GenericPreferencesPanelProvider(Source[] sources) {
		super(new GridLayout(0,
		                     1));
		abstractButtonsByPreferenceMap = GenericPreferencesPanel.toAbstractButtonsByPreferenceMap(sources,
		                                                                                          this);
	}

	@Override
	public abstract String getPreferencesGroupTitle();

	@Override
	public abstract String getPreferencesPanelTitle();

	@Override
	public JComponent getPanel() {return this;}

	@Override
	public void init(Color errorBackgroundColor) {}

	@Override
	public boolean isActivated() {return true;}

	@Override
	public void loadPreferences(Map<String, String> preferences) {
		GenericPreferencesPanel.load(abstractButtonsByPreferenceMap,
		                             preferences);
	}

	@Override
	public void savePreferences(Map<String, String> preferences) {
		GenericPreferencesPanel.save(preferences,
		                             abstractButtonsByPreferenceMap);
	}

	@Override
	public boolean arePreferencesValid() {return true;}

	@Override
	public void addPreferencesChangeListener(PreferencesPanelChangeListener listener) {}

	@Override
	public Component add(Component component) {
		return super.add(component);
	}

	@Override
	public void actionPerformed(ActionEvent actionEvent) {
		GenericPreferencesPanel.super.actionPerformed(actionEvent);
	}
}
