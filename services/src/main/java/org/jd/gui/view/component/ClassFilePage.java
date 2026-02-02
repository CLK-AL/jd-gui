/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.jd.core.v1.ClassFileToJavaSourceDecompiler;
import org.jd.core.v1.api.loader.LoaderException;
import org.jd.core.v1.api.printer.Printer;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.service.preferencespanel.ClassFileDecompilerPreferences;
import org.jd.gui.service.preferencespanel.Preference;
import org.jd.gui.service.preferencespanel.QuiltflowerFileSaverPreferences;
import org.jd.gui.util.decompiler.*;
import org.jd.gui.util.exception.ExceptionUtil;
import org.jd.gui.util.io.NewlineOutputStream;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("UnnecessaryLocalVariable")
public class ClassFilePage
				extends TypePage {
	protected static final String ESCAPE_UNICODE_CHARACTERS = "ClassFileDecompilerPreferences.escapeUnicodeCharacters";
	protected static final String REALIGN_LINE_NUMBERS      = "ClassFileDecompilerPreferences.realignLineNumbers";
	protected static final String WRITE_LINE_NUMBERS        = "ClassFileSaverPreferences.writeLineNumbers";
	protected static final String WRITE_METADATA            = "ClassFileSaverPreferences.writeMetadata";
	protected static final String JD_CORE_VERSION           = "JdGuiPreferences.jdCoreVersion";
	protected static final String QUILTFLOWER_VERSION       = "JdGuiPreferences.quiltflowerVersion";

	protected static final ClassFileToJavaSourceDecompiler JD_CORE_CLASS_FILE_TO_JAVA_SOURCE_DECOMPILER
					= new ClassFileToJavaSourceDecompiler();

	/** Custom syntax style for Java 21 with extended keywords */
	public static final String SYNTAX_STYLE_JAVA21 = "text/java21";

	static {
		// Register Java 21 token maker for enhanced keyword support
		AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
		atmf.putMapping(SYNTAX_STYLE_JAVA21, Java21TokenMaker.class.getName());

		// Early class loading
		try {
			String internalTypeName = ClassFilePage.class.getName()
			                                             .replace('.',
			                                                      '/');
			JD_CORE_CLASS_FILE_TO_JAVA_SOURCE_DECOMPILER.decompile(new ClassPathLoader(),
			                                                       new NopPrinter(),
			                                                       internalTypeName);
		} catch (Throwable t) {
			assert ExceptionUtil.printStackTrace(t);
		}
	}

	protected int maximumLineNumber = -1;

	public ClassFilePage(API api,
	                     Container.Entry entry) {
		super(api,
		      entry);
		Map<String, String> preferences = api.getPreferences();
		// Init view
		setErrorForeground(Color.decode(preferences.get("JdGuiPreferences.errorBackgroundColor")));
		// Display source
		decompile(preferences);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static String[] toQuiltflowerClassArgs(Container.Entry entry,
	                                              ContainerLoader containerLoader,
	                                              String entryInternalName,
	                                              Map<String, String> preferences)
					throws
					IOException,
					LoaderException {
		URI  entryUri      = entry.getUri();
		File fromEntryFile = new File(entryUri);

		File fromJarFile = getFromJarFile(fromEntryFile);

		File fromTempClassFile = getFromTempClassFile(fromEntryFile);
		saveClassFile(containerLoader,
		              entryInternalName,
		              fromTempClassFile);
		File fromClassFile = getFromClassFile(fromEntryFile);
		fromTempClassFile.renameTo(fromClassFile);

		File toJavaFile = getToJavaFile(fromEntryFile);
		String[] quiltflowerArgs = QuiltflowerFileSaverPreferences.toQuiltflowerClassArgs(fromJarFile,
		                                                                                  fromClassFile,
		                                                                                  toJavaFile,
		                                                                                  preferences);
		System.out.printf("Decompiling class file with Quiltflower %s%n"
		                  + "from %s%n"
		                  + "to %s%n"
		                  + "preferences=%s%n"
		                  + "quiltflowerArgs=%s%n",
		                  preferences.get(QUILTFLOWER_VERSION),
		                  fromClassFile,
		                  toJavaFile,
		                  preferences,
		                  Arrays.toString(quiltflowerArgs));
		return quiltflowerArgs;
	}

	@Override
	public String getSyntaxStyle() {return SYNTAX_STYLE_JAVA21;}

	// --- ContentSavable --- //
	@Override
	public String getFileName() {
		String path  = entry.getPath();
		int    index = path.lastIndexOf('.');
		return path.substring(0,
		                      index) + ".java";
	}

	public static File getFromTempClassFile(File fromEntryFile)
					throws
					IOException {
		File fromFile = getFromClassFile(fromEntryFile);
		File fromTempFile = File.createTempFile(fromFile.getName(),
		                                        ".class");
		fromTempFile.deleteOnExit();
		return fromTempFile;
	}

	private static File getFromJarFile(File fromEntryFile) {
		String fromJarAbsolutePath = fromEntryFile.getAbsolutePath()
		                                          .split("!")[0];
		File fromJarFile = new File(fromJarAbsolutePath);
		return fromJarFile;
	}

	private static File getFromClassFile(File fromEntryFile) {
		String fromClassAbsolutePath = fromEntryFile.getAbsolutePath()
		                                            .replace(".jar!",
		                                                     "/classes");
		File fromFile = new File(fromClassAbsolutePath);
		return fromFile;
	}

	private static void saveClassFile(ContainerLoader containerLoader,
	                                  String entryInternalName,
	                                  File fromFile)
					throws
					LoaderException,
					IOException {
		byte[] entryBytes = containerLoader.load(entryInternalName);
		try (FileOutputStream fileOutputStream = new FileOutputStream(fromFile)) {
			fileOutputStream.write(entryBytes);
		}
	}

	public static File getToJavaFile(File fromEntryFile)
					throws
					IOException {
		String toJavaAbsolutePath = fromEntryFile.getAbsolutePath()
		                                         .replace(".jar!",
		                                                  "/src/main/java")
		                                         .replace(".class",
		                                                  ".java");
		File toFile = new File(toJavaAbsolutePath);
		//		File toTempFile = File.createTempFile(toFile.getName(),
		//		                                      ".tmp");
		//		toTempFile.deleteOnExit();
		return toFile;
	}

	public void decompile(Map<String, String> preferences) {
		try {
			// Clear ...
			clearHyperlinks();
			clearLineNumbers();
			declarations.clear();
			typeDeclarations.clear();
			strings.clear();

			// Init preferences
			boolean realignmentLineNumbers = Preference.getBoolean(preferences,
			                                                       REALIGN_LINE_NUMBERS,
			                                                       false);
			boolean unicodeEscape = Preference.getBoolean(preferences,
			                                              ESCAPE_UNICODE_CHARACTERS,
			                                              false);

			Map<String, Object> configuration = new HashMap<>();
			configuration.put("realignLineNumbers",
			                  realignmentLineNumbers);

			setShowMisalignment(realignmentLineNumbers);

			// Init classFilePrinter
			ClassFilePrinter classFilePrinter = new ClassFilePrinter();
			classFilePrinter.setRealignmentLineNumber(realignmentLineNumbers);
			classFilePrinter.setUnicodeEscape(unicodeEscape);

			decompileClassFile(preferences,
			                   configuration,
			                   classFilePrinter);
		} catch (Throwable t) {
			assert ExceptionUtil.printStackTrace(t);
			String text = String.format("// INTERNAL ERROR //%n%s",
			                            t);
			setText(text);
		}

		maximumLineNumber = getMaximumSourceLineNumber();
	}

	@Override
	public void save(API api,
	                 OutputStream os) {
		try {
			// Init preferences
			Map<String, String> preferences = api.getPreferences();
			boolean realignmentLineNumbers = Preference.getBoolean(preferences,
			                                                       REALIGN_LINE_NUMBERS,
			                                                       false);

			boolean unicodeEscape = Preference.getBoolean(preferences,
			                                              ESCAPE_UNICODE_CHARACTERS,
			                                              false);

			boolean showLineNumbers = Preference.getBoolean(preferences,
			                                                WRITE_LINE_NUMBERS,
			                                                true);

			boolean writeMetadata = Preference.getBoolean(preferences,
			                                              ClassFilePage.WRITE_METADATA,
			                                              true);
			Map<String, Object> configuration = new HashMap<>();
			configuration.put("realignLineNumbers",
			                  realignmentLineNumbers);

			// Init lineNumberStringBuilderPrinter
			LineNumberStringBuilderPrinter lineNumberStringBuilderPrinter = new LineNumberStringBuilderPrinter();
			lineNumberStringBuilderPrinter.setRealignmentLineNumber(realignmentLineNumbers);
			lineNumberStringBuilderPrinter.setUnicodeEscape(unicodeEscape);
			lineNumberStringBuilderPrinter.setShowLineNumbers(showLineNumbers);
			StringBuilder stringBuffer = lineNumberStringBuilderPrinter.getStringBuffer();

			decompileClassFile(preferences,
			                   configuration,
			                   lineNumberStringBuilderPrinter);

			// Metadata
			if (writeMetadata) {
				// Add location
				String location = new File(entry.getUri()).getPath()
				                                          // Escape "\ u" sequence to prevent "Invalid unicode" errors
				                                          .replaceAll("(^|[^\\\\])\\\\u",
				                                                      "\\\\\\\\u");
				stringBuffer.append("\n\n/* Location:              ");
				stringBuffer.append(location);
				// Add Java compiler version
				int majorVersion = lineNumberStringBuilderPrinter.getMajorVersion();

				if (majorVersion >= 45) {
					stringBuffer.append("\n * Java compiler version: ");

					if (majorVersion >= 49) {
						stringBuffer.append(majorVersion - (49 - 5));
					} else {
						stringBuffer.append(majorVersion - (45 - 1));
					}

					stringBuffer.append(" (");
					stringBuffer.append(majorVersion);
					stringBuffer.append('.');
					stringBuffer.append(lineNumberStringBuilderPrinter.getMinorVersion());
					stringBuffer.append(')');
				}
				boolean decompileWithQuiltflower = Preference.getBoolean(preferences,
				                                                         ClassFileDecompilerPreferences.decompileWithQuiltflower);
				if (decompileWithQuiltflower) {
					// Add JD-Core version
					stringBuffer.append("\n * Quiltflower Version:       ");
					stringBuffer.append(preferences.get(QUILTFLOWER_VERSION));
					stringBuffer.append("\n */");
				} else {
					// Add JD-Core version
					stringBuffer.append("\n * JD-Core Version:       ");
					stringBuffer.append(preferences.get(JD_CORE_VERSION));
					stringBuffer.append("\n */");
				}
			}

			try (PrintStream ps = new PrintStream(new NewlineOutputStream(os),
			                                      true,
			                                      "UTF-8")) {
				ps.print(stringBuffer.toString());
			} catch (IOException e) {
				assert ExceptionUtil.printStackTrace(e);
			}
		} catch (Throwable t) {
			assert ExceptionUtil.printStackTrace(t);

			try (OutputStreamWriter writer = new OutputStreamWriter(os,
			                                                        Charset.defaultCharset())) {
				writer.write("// INTERNAL ERROR //");
			} catch (IOException ee) {
				assert ExceptionUtil.printStackTrace(ee);
			}
		}
	}

	private void decompileClassFile(Map<String, String> preferences,
	                                Map<String, Object> configuration,
	                                Printer printer)
					throws
					Exception {
		// Init containerLoader
		ContainerLoader containerLoader = new ContainerLoader(entry);

		// Format internal name
		String entryPath = entry.getPath();
		assert entryPath.endsWith(".class");
		String entryInternalName = entryPath.substring(0,
		                                               entryPath.length() - 6); // 6 = ".class".length()

		//TODO
		//		boolean decompileWithQuiltflower = Preference.getBoolean(preferences,
		//		                                                         ClassFileDecompilerPreferences
		//		                                                         .decompileWithQuiltflower);
		//		if (decompileWithQuiltflower) {
		//			String[] quiltflowerArgs = toQuiltflowerClassArgs(entry,
		//			                                                  containerLoader,
		//			                                                  entryInternalName,
		//			                                                  preferences);
		//			ConsoleDecompiler.main(quiltflowerArgs);
		//		} else {
		URI  entryUri      = entry.getUri();
		File fromEntryFile = new File(entryUri);
		File fromFile      = getFromClassFile(fromEntryFile);
		File toFile        = getToJavaFile(fromEntryFile);
		System.out.printf("Decompiling class file with JD-Core %s%n"
		                  + "from %s%n"
		                  + "to %s%n"
		                  + "preferences=%s%n"
		                  + "configuration=%s%n",
		                  preferences.get(JD_CORE_VERSION),
		                  fromFile,
		                  toFile,
		                  preferences,
		                  configuration);
		JD_CORE_CLASS_FILE_TO_JAVA_SOURCE_DECOMPILER.decompile(containerLoader,
		                                                       printer,
		                                                       entryInternalName,
		                                                       configuration);
		//		}
	}

	// --- LineNumberNavigable --- //
	@Override
	public int getMaximumLineNumber() {return maximumLineNumber;}

	@Override
	public void goToLineNumber(int lineNumber) {
		int textAreaLineNumber = getTextAreaLineNumber(lineNumber);
		if (textAreaLineNumber > 0) {
			try {
				int start = textArea.getLineStartOffset(textAreaLineNumber - 1);
				int end   = textArea.getLineEndOffset(textAreaLineNumber - 1);
				setCaretPositionAndCenter(new DocumentRange(start,
				                                            end));
			} catch (BadLocationException e) {
				assert ExceptionUtil.printStackTrace(e);
			}
		}
	}

	@Override
	public boolean checkLineNumber(int lineNumber) {return lineNumber <= maximumLineNumber;}

	// --- PreferencesChangeListener --- //
	@Override
	public void preferencesChanged(Map<String, String> preferences) {
		DefaultCaret caret        = (DefaultCaret) textArea.getCaret();
		int          updatePolicy = caret.getUpdatePolicy();

		caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		decompile(preferences);
		caret.setUpdatePolicy(updatePolicy);

		super.preferencesChanged(preferences);
	}

	public class ClassFilePrinter
					extends StringBuilderPrinter {
		protected HashMap<String, ReferenceData> referencesCache = new HashMap<>();

		// Manage line number and misalignment
		int textAreaLineNumber = 1;

		@Override
		public void start(int maxLineNumber,
		                  int majorVersion,
		                  int minorVersion) {
			super.start(maxLineNumber,
			            majorVersion,
			            minorVersion);

			if (maxLineNumber == 0) {
				scrollPane.setLineNumbersEnabled(false);
			} else {
				setMaxLineNumber(maxLineNumber);
			}
		}

		@Override
		public void end() {
			setText(stringBuffer.toString());
		}

		// --- Add strings --- //
		@Override
		public void printStringConstant(String constant,
		                                String ownerInternalName) {
			if (constant == null) {
				constant = "null";
			}
			if (ownerInternalName == null) {
				ownerInternalName = "null";
			}

			strings.add(new TypePage.StringData(stringBuffer.length(),
			                                    constant.length(),
			                                    constant,
			                                    ownerInternalName));
			super.printStringConstant(constant,
			                          ownerInternalName);
		}

		@Override
		public void printDeclaration(int type,
		                             String internalTypeName,
		                             String name,
		                             String descriptor) {
			if (internalTypeName == null) {
				internalTypeName = "null";
			}
			if (name == null) {
				name = "null";
			}
			if (descriptor == null) {
				descriptor = "null";
			}

			switch (type) {
				case TYPE:
					TypePage.DeclarationData data = new TypePage.DeclarationData(stringBuffer.length(),
					                                                             name.length(),
					                                                             internalTypeName,
					                                                             null,
					                                                             null);
					declarations.put(internalTypeName,
					                 data);
					typeDeclarations.put(stringBuffer.length(),
					                     data);
					break;
				case CONSTRUCTOR:
					declarations.put(internalTypeName + "-<init>-" + descriptor,
					                 new TypePage.DeclarationData(stringBuffer.length(),
					                                              name.length(),
					                                              internalTypeName,
					                                              "<init>",
					                                              descriptor));
					break;
				default:
					declarations.put(internalTypeName + '-' + name + '-' + descriptor,
					                 new TypePage.DeclarationData(stringBuffer.length(),
					                                              name.length(),
					                                              internalTypeName,
					                                              name,
					                                              descriptor));
					break;
			}
			super.printDeclaration(type,
			                       internalTypeName,
			                       name,
			                       descriptor);
		}

		@Override
		public void printReference(int type,
		                           String internalTypeName,
		                           String name,
		                           String descriptor,
		                           String ownerInternalName) {
			if (internalTypeName == null) {
				internalTypeName = "null";
			}
			if (name == null) {
				name = "null";
			}
			if (descriptor == null) {
				descriptor = "null";
			}

			switch (type) {
				case TYPE:
					addHyperlink(new TypePage.HyperlinkReferenceData(stringBuffer.length(),
					                                                 name.length(),
					                                                 newReferenceData(internalTypeName,
					                                                                  null,
					                                                                  null,
					                                                                  ownerInternalName)));
					break;
				case CONSTRUCTOR:
					addHyperlink(new TypePage.HyperlinkReferenceData(stringBuffer.length(),
					                                                 name.length(),
					                                                 newReferenceData(internalTypeName,
					                                                                  "<init>",
					                                                                  descriptor,
					                                                                  ownerInternalName)));
					break;
				default:
					addHyperlink(new TypePage.HyperlinkReferenceData(stringBuffer.length(),
					                                                 name.length(),
					                                                 newReferenceData(internalTypeName,
					                                                                  name,
					                                                                  descriptor,
					                                                                  ownerInternalName)));
					break;
			}
			super.printReference(type,
			                     internalTypeName,
			                     name,
			                     descriptor,
			                     ownerInternalName);
		}

		@Override
		public void startLine(int lineNumber) {
			super.startLine(lineNumber);
			setLineNumber(textAreaLineNumber,
			              lineNumber);
		}

		@Override
		public void endLine() {
			super.endLine();
			textAreaLineNumber++;
		}

		@Override
		public void extraLine(int count) {
			super.extraLine(count);
			if (realignmentLineNumber) {
				textAreaLineNumber += count;
			}
		}

		// --- Add references --- //
		public TypePage.ReferenceData newReferenceData(String internalName,
		                                               String name,
		                                               String descriptor,
		                                               String scopeInternalName) {
			String        key       = internalName + '-' + name + '-' + descriptor + '-' + scopeInternalName;
			ReferenceData reference = referencesCache.get(key);

			if (reference == null) {
				reference = new TypePage.ReferenceData(internalName,
				                                       name,
				                                       descriptor,
				                                       scopeInternalName);
				referencesCache.put(key,
				                    reference);
				references.add(reference);
			}

			return reference;
		}
	}
}
