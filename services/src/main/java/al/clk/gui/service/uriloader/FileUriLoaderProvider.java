/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.uriloader;

import al.clk.gui.api.API;
import al.clk.gui.spi.FileLoader;
import al.clk.gui.spi.UriLoader;

import java.io.File;
import java.net.URI;

public class FileUriLoaderProvider
				implements UriLoader {
	protected static final String[] SCHEMES = {"file"};

	public String[] getSchemes() {return SCHEMES;}

	public boolean accept(API api,
	                      URI uri) {return "file".equals(uri.getScheme());}

	public boolean load(API api,
	                    URI uri) {
		File       file       = new File(uri.getPath());
		FileLoader fileLoader = api.getFileLoader(file);

		return (fileLoader != null) && fileLoader.load(api,
		                                               file);
	}
}
