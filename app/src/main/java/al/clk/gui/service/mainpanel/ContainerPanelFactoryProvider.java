/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.mainpanel;

import al.clk.gui.api.API;
import al.clk.gui.api.feature.ContentIndexable;
import al.clk.gui.api.feature.SourcesSavable;
import al.clk.gui.api.feature.UriGettable;
import al.clk.gui.api.model.Container;
import al.clk.gui.api.model.Indexes;
import al.clk.gui.spi.Indexer;
import al.clk.gui.spi.PanelFactory;
import al.clk.gui.spi.SourceSaver;
import al.clk.gui.spi.TreeNodeFactory;
import al.clk.gui.util.exception.ExceptionUtil;
import al.clk.gui.view.component.panel.TreeTabbedPanel;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;

public class ContainerPanelFactoryProvider
				implements PanelFactory {
	protected static final String[] TYPES = {"default"};

	@Override
	public String[] getTypes() {return TYPES;}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends JComponent & UriGettable> T make(API api,
	                                                   Container container) {
		return (T) new ContainerPanel(api,
		                              container);
	}

	protected static class DelegatedMap<K, V>
					implements Map<K, V> {
		protected Map<K, V> map;

		public DelegatedMap(Map<K, V> map) {this.map = map;}

		@Override
		public int size() {return map.size();}

		@Override
		public boolean isEmpty() {return map.isEmpty();}

		@Override
		public boolean containsKey(Object o) {return map.containsKey(o);}

		@Override
		public boolean containsValue(Object o) {return map.containsValue(o);}

		@Override
		public V get(Object o) {return map.get(o);}

		@Override
		public V put(K k,
		             V v) {
			return map.put(k,
			               v);
		}

		@Override
		public V remove(Object o) {return map.remove(o);}

		@Override
		public void putAll(Map<? extends K, ? extends V> map) {this.map.putAll(map);}

		@Override
		public void clear() {map.clear();}

		@Override
		public Set<K> keySet() {return map.keySet();}

		@Override
		public Collection<V> values() {return map.values();}

		@Override
		public Set<Entry<K, V>> entrySet() {return map.entrySet();}

		@Override
		public boolean equals(Object o) {return map.equals(o);}

		@Override
		public int hashCode() {return map.hashCode();}
	}

	protected static class DelegatedMapWithDefault
					extends DelegatedMap<String, Collection> {
		public DelegatedMapWithDefault(Map<String, Collection> map) {super(map);}

		@Override
		public Collection get(Object o) {
			Collection value = map.get(o);
			if (value == null) {
				String key = o.toString();
				map.put(key,
				        value = new ArrayList());
			}
			return value;
		}
	}

	protected static class DelegatedMapMapWithDefault
					extends DelegatedMap<String, Map<String, Collection>> {
		protected HashMap<String, Map<String, Collection>> wrappers = new HashMap<>();

		public DelegatedMapMapWithDefault(Map<String, Map<String, Collection>> map) {super(map);}

		@Override
		public Map<String, Collection> get(Object o) {
			Map<String, Collection> value = wrappers.get(o);

			if (value == null) {
				String                      key = o.toString();
				HashMap<String, Collection> m   = new HashMap<>();
				map.put(key,
				        m);
				wrappers.put(key,
				             value = new DelegatedMapWithDefault(m));
			}

			return value;
		}
	}

	protected class ContainerPanel
					extends TreeTabbedPanel
					implements ContentIndexable,
					           SourcesSavable {
		protected Container.Entry entry;

		public ContainerPanel(API api,
		                      Container container) {
			super(api,
			      container.getRoot()
			               .getParent()
			               .getUri());

			this.entry = container.getRoot()
			                      .getParent();

			DefaultMutableTreeNode root = new DefaultMutableTreeNode();

			for (Container.Entry entry : container.getRoot()
			                                      .getChildren()) {
				TreeNodeFactory factory = api.getTreeNodeFactory(entry);
				if (factory != null) {
					root.add(factory.make(api,
					                      entry));
				}
			}

			tree.setModel(new DefaultTreeModel(root));
		}

		@Override
		public Container.Entry getEntry() {
			return entry;
		}

		// --- ContentIndexable --- //
		@Override
		public Indexes index(API api) {
			HashMap<String, Map<String, Collection>> map            = new HashMap<>();
			DelegatedMapMapWithDefault               mapWithDefault = new DelegatedMapMapWithDefault(map);

			// Index populating value automatically
			Indexes indexesWithDefault = name -> mapWithDefault.get(name);

			// Index entry
			Indexer indexer = api.getIndexer(entry);

			if (indexer != null) {
				indexer.index(api,
				              entry,
				              indexesWithDefault);
			}

			// To prevent memory leaks, return an index without the 'populate' behaviour
			return name -> map.get(name);
		}

		// --- SourcesSavable --- //
		@Override
		public String getSourceFileName() {
			SourceSaver saver = api.getSourceSaver(entry);

			if (saver != null) {
				String path  = saver.getSourcePath(entry);
				int    index = path.lastIndexOf('/');
				return path.substring(index + 1);
			} else {
				return null;
			}
		}

		@Override
		public int getFileCount() {
			SourceSaver saver = api.getSourceSaver(entry);
			return (saver != null)
			       ? saver.getFileCount(api,
			                            entry)
			       : 0;
		}

		@Override
		public void save(API api,
		                 Controller controller,
		                 Listener listener,
		                 Path path) {
			try {

				URI uri = path.toUri();
				URI archiveUri = new URI("jar:" + uri.getScheme(),
				                         uri.getHost(),
				                         uri.getPath() + "!/",
				                         null);

				try (FileSystem archiveFs = FileSystems.newFileSystem(archiveUri,
				                                                      Collections.singletonMap("create",
				                                                                               "true"))) {
					Path        archiveRootPath = archiveFs.getPath("/");
					SourceSaver saver           = api.getSourceSaver(entry);

					if (saver != null) {
						saver.saveContent(api,
						                  () -> controller.isCancelled(),
						                  (p) -> listener.pathSaved(p),
						                  archiveRootPath,
						                  archiveRootPath,
						                  entry);
					}
				}
			} catch (URISyntaxException | IOException e) {
				assert ExceptionUtil.printStackTrace(e);
			}
		}
	}
}
