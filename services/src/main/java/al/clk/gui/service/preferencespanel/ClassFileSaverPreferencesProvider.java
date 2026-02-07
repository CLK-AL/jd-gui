/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.preferencespanel;

public class ClassFileSaverPreferencesProvider
				extends GenericPreferencesPanelProvider<ClassFileSaverPreferences> {
	public ClassFileSaverPreferencesProvider() {
		super(ClassFileSaverPreferences.values());
	}

	@Override
	public String getPreferencesGroupTitle() {return "Source Saver";}

	@Override
	public String getPreferencesPanelTitle() {return "Class file";}
}

