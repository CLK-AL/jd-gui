/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.pastehandler;

import al.clk.gui.api.API;
import al.clk.gui.spi.PasteHandler;
import al.clk.gui.view.component.LogPage;

import java.net.URI;

public class LogPasteHandler
				implements PasteHandler {
	protected static int counter = 0;

	public boolean accept(Object obj) {return obj instanceof String;}

	public void paste(API api,
	                  Object obj) {
		String title = "clipboard-" + (++counter) + ".log";
		URI    uri   = URI.create("memory://" + title);
		String content = (obj == null)
		                 ? null
		                 : obj.toString();
		api.addPanel(title,
		             null,
		             null,
		             new LogPage(api,
		                         uri,
		                         content));
	}
}
