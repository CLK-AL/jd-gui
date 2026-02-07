package al.clk.gui.service.preferencespanel;

import java.util.Map;

@SuppressWarnings("UnnecessaryLocalVariable")
public interface Preference {
	String FALSE = "0";
	String TRUE  = "1";
	String TRACE = "TRACE";
	String INFO  = "INFO";
	String WARN  = "WARN";
	String ERROR = "ERROR";

	static boolean getBoolean(Map<String, String> preferences,
	                          String key,
	                          boolean defaultValue) {
		String preferenceString = preferences.get(key);
		boolean preferenceBoolean = (preferenceString == null)
		                            ? defaultValue
		                            : Boolean.parseBoolean(preferenceString);
		return preferenceBoolean;
	}

	static boolean getBoolean(Map<String, String> preferences,
	                          Preference preference) {
		String preferenceString = Preference.get(preferences,
		                                         preference);
		boolean preferenceValue = preferenceString.equals(preference.getDefaultValue());
		return preferenceValue;
	}

	static String get(Map<String, String> preferences,
	                  Preference preference) {
		String key   = preference.getKey();
		String value = preferences.get(key);
		String preferenceValue = value == null
		                         ? preference.getDefaultValue()
		                         : value;
		return preferenceValue;
	}

	default String getKey() {
		String key = getClass().getSimpleName() + "." + name();
		return key;
	}

	String name();

	String getDescription();

	String getDefaultValue();

	String getDeselectedValue();

	String getSelectedValue();

	void setSelectedValue(String selectedValue);

	String[] getPossibleValues();
}
