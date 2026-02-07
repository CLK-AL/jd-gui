/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.container;

import al.clk.gui.api.API;
import al.clk.gui.api.model.Container;
import al.clk.gui.model.container.EarContainer;
import al.clk.gui.spi.ContainerFactory;
import al.clk.gui.util.exception.ExceptionUtil;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class EarContainerFactoryProvider
				implements ContainerFactory {
	@Override
	public String getType() {return "ear";}

	@Override
	public boolean accept(API api,
	                      Path rootPath) {
		if (rootPath.toUri()
		            .toString()
		            .toLowerCase()
		            .endsWith(".ear!/")) {
			return true;
		} else {
			// Extension: accept uncompressed EAR file containing a folder 'META-INF/application.xml'
			try {
				return rootPath.getFileSystem()
				               .provider()
				               .getScheme()
				               .equals("file") && Files.exists(rootPath.resolve("META-INF/application.xml"));
			} catch (InvalidPathException e) {
				assert ExceptionUtil.printStackTrace(e);
				return false;
			}
		}
	}

	@Override
	public Container make(API api,
	                      Container.Entry parentEntry,
	                      Path rootPath) {
		return new EarContainer(api,
		                        parentEntry,
		                        rootPath);
	}
}
