/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.fileloader;

import al.clk.gui.api.API;
import al.clk.gui.service.extension.ExtensionService;
import al.clk.gui.spi.FileLoader;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;

public class FileLoaderService {
	protected static final FileLoaderService           FILE_LOADER_SERVICE = new FileLoaderService();
	protected final        Collection<FileLoader>      providers           = ExtensionService.getInstance()
	                                                                                         .load(FileLoader.class);
	protected              HashMap<String, FileLoader> mapProviders        = new HashMap<>();

	protected FileLoaderService() {
		for (FileLoader provider : providers) {
			for (String extension : provider.getExtensions()) {
				mapProviders.put(extension,
				                 provider);
			}
		}
	}

	public static FileLoaderService getInstance() {return FILE_LOADER_SERVICE;}

	public FileLoader get(API api,
	                      File file) {
		String     name      = file.getName();
		int        lastDot   = name.lastIndexOf('.');
		String     extension = name.substring(lastDot + 1);
		FileLoader provider  = mapProviders.get(extension);
		return provider;
	}

	public HashMap<String, FileLoader> getMapProviders() {
		return mapProviders;
	}
}
