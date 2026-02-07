/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.fileloader;

import al.clk.gui.api.API;

import java.io.File;

public class JarFileLoaderProvider
				extends ZipFileLoaderProvider {
	protected static final String[] EXTENSIONS = {"jar"};

	@Override
	public String[] getExtensions() {return EXTENSIONS;}

	@Override
	public String getDescription() {return "Java archive files (*.jar)";}

	@Override
	public boolean accept(API api,
	                      File file) {
		return file.exists() && file.isFile() && file.canRead() && file.getName()
		                                                               .toLowerCase()
		                                                               .endsWith(".jar");
	}
}
