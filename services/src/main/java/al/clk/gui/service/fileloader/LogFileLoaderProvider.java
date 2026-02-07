/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.fileloader;

import al.clk.gui.api.API;
import al.clk.gui.util.io.TextReader;
import al.clk.gui.view.component.LogPage;

import java.io.File;

public class LogFileLoaderProvider
				extends ZipFileLoaderProvider {
	protected static final String[] EXTENSIONS = {"log"};

	@Override
	public String[] getExtensions() {return EXTENSIONS;}

	@Override
	public String getDescription() {return "Log files (*.log)";}

	@Override
	public boolean accept(API api,
	                      File file) {
		return file.exists() && file.isFile() && file.canRead() && file.getName()
		                                                               .toLowerCase()
		                                                               .endsWith(".log");
	}

	@Override
	public boolean load(API api,
	                    File file) {
		api.addPanel(file.getName(),
		             null,
		             "Location: " + file.getAbsolutePath(),
		             new LogPage(api,
		                         file.toURI(),
		                         TextReader.getText(file)));
		return true;
	}
}
