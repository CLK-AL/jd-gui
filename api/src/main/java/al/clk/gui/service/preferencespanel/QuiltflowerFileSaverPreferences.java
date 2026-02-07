package al.clk.gui.service.preferencespanel;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UnnecessaryLocalVariable")
public enum QuiltflowerFileSaverPreferences
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
	//	mpm("Maximum allowed processing time per decompiled method, in seconds. 0 means no upper limit",
	//	    FALSE,
	//	    FALSE,
	//	    TRUE),
	ren("Rename ambiguous (resp. obfuscated) classes and class elements",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	//	urc("Full name of a user-supplied class implementing IIdentifierRenamer interface. It is used to determine which"
	//	    + " class identifiers should be renamed and provides new identifier names (see \"Renaming identifiers\")",
	//	    "user-supplied"),
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
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	//	tlf("Experimental try loop enhancements (may cause some methods to decompile wrong or not at all!)",
	//	    FALSE,
	//	    FALSE,
	//	    TRUE),
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
	//	thr("Maximum number of threads",
	//	    "number of threads available to the JVM"),
	jrt("Add the currently used Java runtime as a library",
	    Preference.FALSE,
	    Preference.FALSE,
	    Preference.TRUE),
	dbe("Dump bytecode on errors",
	    Preference.TRUE,
	    Preference.FALSE,
	    Preference.TRUE),
	//		nls("New line character to be used for output.",
	//		    "OS-dependent",
	//		    "0 - '\r\n' (Windows)",
	//		    "1 - '\n' (Unix)"),
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
	    Preference.TRUE);
	private static final LinkedHashMap<String, QuiltflowerFileSaverPreferences> namePreferencesMap = GenericPreferencesPanel.toPreferenceByNameMap(QuiltflowerFileSaverPreferences.values());

	private static final LinkedHashMap<String, QuiltflowerFileSaverPreferences> descriptionPreferencesMap = GenericPreferencesPanel.toPreferenceByDescriptionMap(QuiltflowerFileSaverPreferences.values());

	private final String   description;
	private final String[] possibleValues;
	private final String   deselectedValue;
	private final String   defaultValue;
	private       String   selectedValue;

	QuiltflowerFileSaverPreferences(String description,
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

	public static QuiltflowerFileSaverPreferences getByName(String name) {
		return namePreferencesMap.get(name);
	}

	public static QuiltflowerFileSaverPreferences getByDescription(String description) {
		return descriptionPreferencesMap.get(description);
	}

	public static String[] toQuiltflowerJarArgs(File fromJarFile,
	                                            File toSourcesJarFile,
	                                            Map<String, String> preferences) {
		List<String> quiltflowerArgsList = new ArrayList<>();
		quiltflowerArgsList.add("--file");
		quiltflowerArgsList.add(fromJarFile.getAbsolutePath());
		quiltflowerArgsList.add(toSourcesJarFile.getAbsolutePath());
		String[] quiltflowerArgs = quiltflowerArgsList.toArray(new String[0]);
		return quiltflowerArgs;
	}

	public static String[] toQuiltflowerClassArgs(File fromJarFile,
	                                              File fromClassFile,
	                                              File toJavaFile,
	                                              Map<String, String> preferences) {
		List<String> quiltflowerArgsList = new ArrayList<>();
		quiltflowerArgsList.add("--file");
		quiltflowerArgsList.add(fromClassFile.getAbsolutePath());
		quiltflowerArgsList.add(String.format("-e=%s",
		                                      fromJarFile.getAbsolutePath()));
		quiltflowerArgsList.add(toJavaFile.getAbsolutePath());
		String[] quiltflowerArgs = quiltflowerArgsList.toArray(new String[0]);
		return quiltflowerArgs;
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
