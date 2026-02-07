/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.indexer;

import al.clk.gui.api.API;
import al.clk.gui.api.model.Container;
import al.clk.gui.api.model.Indexes;
import al.clk.gui.util.io.TextReader;

public class TextFileIndexerProvider
				extends AbstractIndexerProvider {

	@Override
	public String[] getSelectors() {
		return appendSelectors("*:file:*.txt",
		                       "*:file:*.html",
		                       "*:file:*.xhtml",
		                       "*:file:*.js",
		                       "*:file:*.jsp",
		                       "*:file:*.jspf",
		                       "*:file:*.xml",
		                       "*:file:*.xsl",
		                       "*:file:*.xslt",
		                       "*:file:*.xsd",
		                       "*:file:*.properties",
		                       "*:file:*.sql",
		                       "*:file:*.yaml",
		                       "*:file:*.yml",
		                       "*:file:*.json");
	}

	@Override
	@SuppressWarnings("unchecked")
	public void index(API api,
	                  Container.Entry entry,
	                  Indexes indexes) {
		indexes.getIndex("strings")
		       .get(TextReader.getText(entry.getInputStream()))
		       .add(entry);
	}
}
