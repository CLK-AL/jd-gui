package al.clk.gui.service.preferencespanel;

import java.util.LinkedHashMap;

public enum ClassFileDecompilerPreferences
				implements Preference {
	escapeUnicodeCharacters("Escape unicode characters",
	                        Preference.FALSE,
	                        Preference.FALSE,
	                        Preference.TRUE),
	realignLineNumbers("Realign line numbers",
	                   Preference.FALSE,
	                   Preference.FALSE,
	                   Preference.TRUE),
	decompileWithVineflower("Decompile jar with Vineflower on Save All Sources (Ctrl+Alt+S).",
	                        Preference.TRUE,
	                        Preference.FALSE,
	                        Preference.TRUE);
	private static final LinkedHashMap<String, ClassFileDecompilerPreferences> namePreferencesMap
					= GenericPreferencesPanel.toPreferenceByNameMap(ClassFileDecompilerPreferences.values());

	private static final LinkedHashMap<String, ClassFileDecompilerPreferences> descriptionPreferencesMap
					= GenericPreferencesPanel.toPreferenceByDescriptionMap(ClassFileDecompilerPreferences.values());

	private final String   description;
	private final String[] possibleValues;
	private final String   deselectedValue;
	private final String   defaultValue;
	private       String   selectedValue;

	ClassFileDecompilerPreferences(String description,
	                               String defaultValue,
	                               String... possibleValues) {
		this.description = description;
		this.possibleValues = possibleValues;
		this.defaultValue = defaultValue;
		this.deselectedValue = possibleValues.length > 0
		                       ? possibleValues[0]
		                       : defaultValue;
		this.selectedValue = possibleValues.length > 1
		                     ? possibleValues[1]
		                     : defaultValue;
	}

	public static ClassFileDecompilerPreferences getByName(String name) {
		return namePreferencesMap.get(name);
	}

	public static ClassFileDecompilerPreferences getByDescription(String description) {
		return descriptionPreferencesMap.get(description);
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String[] getPossibleValues() {
		return possibleValues;
	}

	@Override
	public String getDefaultValue() {
		return defaultValue;
	}

	@Override
	public String getDeselectedValue() {
		return deselectedValue;
	}

	@Override
	public String getSelectedValue() {
		return selectedValue;
	}

	public void setSelectedValue(String selectedValue) {
		this.selectedValue = selectedValue;
	}
}
