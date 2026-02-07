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
import al.clk.gui.util.exception.ExceptionUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

public class MetainfServiceFileIndexerProvider
				extends AbstractIndexerProvider {

	@Override
	public String[] getSelectors() {return appendSelectors("*:file:*");}

	@Override
	public Pattern getPathPattern() {
		return (externalPathPattern != null)
		       ? externalPathPattern
		       : Pattern.compile("META-INF\\/services\\/[^\\/]+");
	}

	@Override
	@SuppressWarnings("unchecked")
	public void index(API api,
	                  Container.Entry entry,
	                  Indexes indexes) {
		Map<String, Collection> index = indexes.getIndex("typeReferences");

		try (BufferedReader br = new BufferedReader(new InputStreamReader(entry.getInputStream()))) {
			String line;

			while ((line = br.readLine()) != null) {
				String trim = line.trim();

				if (!trim.isEmpty() && (trim.charAt(0) != '#')) {
					String internalTypeName = trim.replace(".",
					                                       "/");

					index.get(internalTypeName)
					     .add(entry);
				}
			}
		} catch (IOException e) {
			assert ExceptionUtil.printStackTrace(e);
		}
	}
}
