/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.api;

import al.clk.gui.api.feature.UriGettable;
import al.clk.gui.api.model.Container;
import al.clk.gui.api.model.Indexes;
import al.clk.gui.spi.*;

import javax.swing.*;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Future;

public interface API {
	boolean openURI(URI uri);

	boolean openURI(int x,
	                int y,
	                Collection<Container.Entry> entries,
	                String query,
	                String fragment);

	void addURI(URI uri);

	<T extends JComponent & UriGettable> void addPanel(String title,
	                                                   Icon icon,
	                                                   String tip,
	                                                   T component);

	Collection<Action> getContextualActions(Container.Entry entry,
	                                        String fragment);

	UriLoader getUriLoader(URI uri);

	FileLoader getFileLoader(File file);

	ContainerFactory getContainerFactory(Path rootPath);

	PanelFactory getMainPanelFactory(Container container);

	TreeNodeFactory getTreeNodeFactory(Container.Entry entry);

	TypeFactory getTypeFactory(Container.Entry entry);

	Indexer getIndexer(Container.Entry entry);

	SourceSaver getSourceSaver(Container.Entry entry);

	Map<String, String> getPreferences();

	Collection<Future<Indexes>> getCollectionOfFutureIndexes();

	String getSource(Container.Entry entry);

	void loadSource(Container.Entry entry,
	                LoadSourceListener listener);

	File loadSourceFile(Container.Entry entry);

	interface LoadSourceListener {
		void sourceLoaded(String source);
	}
}
