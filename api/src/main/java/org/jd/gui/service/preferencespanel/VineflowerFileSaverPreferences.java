package org.jd.gui.service.preferencespanel;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vineflower decompiler preferences (successor to Quiltflower/Fernflower).
 * Vineflower is a modern Java decompiler with support for Java 21+ features.
 */
@SuppressWarnings("UnnecessaryLocalVariable")
public enum VineflowerFileSaverPreferences
				implements Preference {
	rbr("Hide bridge methods",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	rsy("Hide synthetic class members",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	din("Decompile inner classes",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	dc4("Collapse 1.4 class references",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	das("Decompile assertions",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	hes("Hide empty super invocation",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	hdc("Hide empty default constructor",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	dgs("Decompile generic signatures",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	ner("Assume return not throwing exceptions",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	den("Decompile enumerations",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	rgn("Remove getClass() invocation, when it is part of a qualified new statement",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	lit("Output numeric literals \"as-is\"",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	asc("Encode non-ASCII characters in string and character literals as Unicode escapes",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	bto("Interpret int 1 as boolean true (workaround to a compiler bug)",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	nns("Allow for not set synthetic attribute (workaround to a compiler bug)",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	uto("Consider nameless types as java.lang.Object (workaround to a compiler architecture flaw)",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	udv("Reconstruct variable names from debug information, if present",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	rer("Remove empty exception ranges",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	fdi("De-inline finally structures",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	ren("Rename ambiguous (resp. obfuscated) classes and class elements",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	inn("Check for IntelliJ IDEA-specific @NotNull annotation and remove inserted code if found",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	lac("Decompile lambda expressions to anonymous classes",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	bsm("Add mappings for source bytecode instructions to decompiled code lines",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	iib("Ignore invalid bytecode",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	vac("Verify that anonymous classes can be anonymous",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	tcs("Simplify boolean constants in ternary operations",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	pam("Decompile pattern matching",
	    Preference.TRUE, // Enabled by default for Java 21
	    Preference.FALSE,
	    Preference.TRUE),
	tco("Allow ternaries to be generated in if and loop conditions",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	isl("Inline simple lambdas",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	jvn("Use jad variable naming",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	sef("Skip copying non-class files from the input folder or file to the output",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	win("Warn about inconsistent inner class attributes",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	jrt("Add the currently used Java runtime as a library",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	dbe("Dump bytecode on errors",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	ind("Indentation string",
	    "3 spaces"),
	log("Logging level",
	    Preference.INFO,
	    Preference.TRACE,
	    Preference.INFO,
	    Preference.WARN,
	    Preference.ERROR),
	dee("Dump exceptions on errors",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	// Vineflower-specific options (Java 21+)
	dcl("Decompile sealed classes and interfaces",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	drp("Decompile record patterns",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE);

	private static final LinkedHashMap<String, VineflowerFileSaverPreferences> namePreferencesMap = GenericPreferencesPanel.toPreferenceByNameMap(VineflowerFileSaverPreferences.values());

	private static final LinkedHashMap<String, VineflowerFileSaverPreferences> descriptionPreferencesMap = GenericPreferencesPanel.toPreferenceByDescriptionMap(VineflowerFileSaverPreferences.values());

	private final String   description;
	private final String[] possibleValues;
	private final String   deselectedValue;
	private final String   defaultValue;
	private       String   selectedValue;

	VineflowerFileSaverPreferences(String description,
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

	public static VineflowerFileSaverPreferences getByName(String name) {
		return namePreferencesMap.get(name);
	}

	public static VineflowerFileSaverPreferences getByDescription(String description) {
		return descriptionPreferencesMap.get(description);
	}

	public static String[] toVineflowerJarArgs(File fromJarFile,
	                                           File toSourcesJarFile,
	                                           Map<String, String> preferences) {
		List<String> vineflowerArgsList = new ArrayList<>();
		vineflowerArgsList.add("--file");
		vineflowerArgsList.add(fromJarFile.getAbsolutePath());
		vineflowerArgsList.add(toSourcesJarFile.getAbsolutePath());
		String[] vineflowerArgs = vineflowerArgsList.toArray(new String[0]);
		return vineflowerArgs;
	}

	public static String[] toVineflowerClassArgs(File fromJarFile,
	                                             File fromClassFile,
	                                             File toJavaFile,
	                                             Map<String, String> preferences) {
		List<String> vineflowerArgsList = new ArrayList<>();
		vineflowerArgsList.add("--file");
		vineflowerArgsList.add(fromClassFile.getAbsolutePath());
		vineflowerArgsList.add(String.format("-e=%s",
		                                     fromJarFile.getAbsolutePath()));
		vineflowerArgsList.add(toJavaFile.getAbsolutePath());
		String[] vineflowerArgs = vineflowerArgsList.toArray(new String[0]);
		return vineflowerArgs;
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
