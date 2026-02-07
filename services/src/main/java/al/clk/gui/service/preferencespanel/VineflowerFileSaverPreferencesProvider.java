/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.preferencespanel;

/**
 * Preferences panel provider for Vineflower decompiler settings.
 * Vineflower is the successor to Quiltflower/Fernflower with Java 21+ support.
 */
public class VineflowerFileSaverPreferencesProvider
				extends GenericPreferencesPanelProvider<VineflowerFileSaverPreferences> {
	public VineflowerFileSaverPreferencesProvider() {
		super(VineflowerFileSaverPreferences.values());
	}

	@Override
	public String getPreferencesGroupTitle() {return "Decompiler";}

	@Override
	public String getPreferencesPanelTitle() {return "Vineflower";}
}
