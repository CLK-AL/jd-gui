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
import al.clk.gui.spi.Indexer;

public class DirectoryIndexerProvider
				extends AbstractIndexerProvider {

	@Override
	public String[] getSelectors() {return appendSelectors("*:dir:*");}

	@Override
	public void index(API api,
	                  Container.Entry entry,
	                  Indexes indexes) {
		int depth = 15;

		try {
			depth = Integer.valueOf(api.getPreferences()
			                           .get("DirectoryIndexerPreferences.maximumDepth"));
		} catch (NumberFormatException ignore) {
		}

		index(api,
		      entry,
		      indexes,
		      depth);
	}

	public void index(API api,
	                  Container.Entry entry,
	                  Indexes indexes,
	                  int depth) {
		if (depth-- > 0) {
			for (Container.Entry e : entry.getChildren()) {
				if (e.isDirectory()) {
					index(api,
					      e,
					      indexes,
					      depth);
				} else {
					Indexer indexer = api.getIndexer(e);

					if (indexer != null) {
						indexer.index(api,
						              e,
						              indexes);
					}
				}
			}
		}
	}
}
