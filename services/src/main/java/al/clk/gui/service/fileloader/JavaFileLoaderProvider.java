/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.fileloader;

import al.clk.gui.api.API;
import al.clk.gui.util.io.TextReader;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaFileLoaderProvider
				extends AbstractTypeFileLoaderProvider {
	protected static final String[] EXTENSIONS = {"java"};

	@Override
	public String[] getExtensions() {return EXTENSIONS;}

	@Override
	public String getDescription() {return "Java files (*.java)";}

	@Override
	public boolean accept(API api,
	                      File file) {
		return file.exists() && file.isFile() && file.canRead() && file.getName()
		                                                               .toLowerCase()
		                                                               .endsWith(".java");
	}

	@Override
	public boolean load(API api,
	                    File file) {
		String  text    = TextReader.getText(file);
		Pattern pattern = Pattern.compile("(?s)(.*\\s)?package\\s+(\\S+)\\s*;.*");
		Matcher matcher = pattern.matcher(text);

		if (matcher.matches()) {
			// Package name found
			String pathInFile = matcher.group(2)
			                           .replace(".",
			                                    File.separator) + File.separator + file.getName();

			return load(api,
			            file,
			            pathInFile);
		} else {
			// Package name not found
			return load(api,
			            file,
			            file.getName());
		}
	}
}
