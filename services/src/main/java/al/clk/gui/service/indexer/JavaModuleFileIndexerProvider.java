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

import java.util.Collection;
import java.util.Map;

public class JavaModuleFileIndexerProvider
				extends AbstractIndexerProvider {

	@SuppressWarnings("unchecked")
	protected static void index(API api,
	                            Container.Entry entry,
	                            Indexes indexes,
	                            Map<String, Collection> packageDeclarationIndex) {
		for (Container.Entry e : entry.getChildren()) {
			if (e.isDirectory()) {
				String path = e.getPath();

				if (!path.startsWith("classes/META-INF")) {
					packageDeclarationIndex.get(path.substring(8))
					                       .add(e); // 8 = "classes/".length()
				}

				index(api,
				      e,
				      indexes,
				      packageDeclarationIndex);
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

	@Override
	public String[] getSelectors() {return appendSelectors("*:file:*.jmod");}

	@Override
	public void index(API api,
	                  Container.Entry entry,
	                  Indexes indexes) {
		for (Container.Entry e : entry.getChildren()) {
			if (e.isDirectory() && e.getPath()
			                        .equals("classes")) {
				Map<String, Collection> packageDeclarationIndex = indexes.getIndex("packageDeclarations");

				// Index module-info, packages and CLASS files
				index(api,
				      e,
				      indexes,
				      packageDeclarationIndex);
				break;
			}
		}
	}
}
