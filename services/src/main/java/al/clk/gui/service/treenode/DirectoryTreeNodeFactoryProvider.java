/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.treenode;

import al.clk.gui.api.API;
import al.clk.gui.api.feature.ContainerEntryGettable;
import al.clk.gui.api.feature.TreeNodeExpandable;
import al.clk.gui.api.feature.UriGettable;
import al.clk.gui.api.model.Container;
import al.clk.gui.api.model.Container.Entry;
import al.clk.gui.spi.TreeNodeFactory;
import al.clk.gui.view.data.TreeNodeBean;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.net.URI;
import java.util.Collection;

public class DirectoryTreeNodeFactoryProvider
				extends AbstractTreeNodeFactoryProvider {
	protected static final ImageIcon ICON      = new ImageIcon(DirectoryTreeNodeFactoryProvider.class.getClassLoader()
	                                                                                                 .getResource("al/clk/gui/images/folder.gif"));
	protected static final ImageIcon OPEN_ICON = new ImageIcon(DirectoryTreeNodeFactoryProvider.class.getClassLoader()
	                                                                                                 .getResource("al/clk/gui/images/folder_open.png"));

	@Override
	public String[] getSelectors() {return appendSelectors("*:dir:*");}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> T make(API api,
	                                                                                        Container.Entry entry) {
		int lastSlashIndex = entry.getPath()
		                          .lastIndexOf('/');
		Collection<Entry> entries = entry.getChildren();

		// Aggregate directory names
		while (entries.size() == 1) {
			Entry child = entries.iterator()
			                     .next();
			if (!child.isDirectory()
			    || api.getTreeNodeFactory(child) != this
			    || entry.getContainer() != child.getContainer()) {
				break;
			}
			entry = child;
			entries = entry.getChildren();
		}

		String label = entry.getPath()
		                    .substring(lastSlashIndex + 1);
		String location = new File(entry.getUri()).getPath();
		TreeNode node = new TreeNode(entry,
		                             new TreeNodeBean(label,
		                                              "Location: " + location,
		                                              getIcon(),
		                                              getOpenIcon()));

		if (entries.size() > 0) {
			// Add dummy node
			node.add(new DefaultMutableTreeNode());
		}

		return (T) node;
	}

	public ImageIcon getIcon()     {return ICON;}

	public ImageIcon getOpenIcon() {return OPEN_ICON;}

	protected static class TreeNode
					extends DefaultMutableTreeNode
					implements ContainerEntryGettable,
					           UriGettable,
					           TreeNodeExpandable {
		Container.Entry entry;
		boolean         initialized;

		public TreeNode(Container.Entry entry,
		                Object userObject) {
			super(userObject);
			this.entry = entry;
			this.initialized = false;
		}

		// --- ContainerEntryGettable --- //
		@Override
		public Container.Entry getEntry() {return entry;}

		// --- UriGettable --- //
		@Override
		public URI getUri() {return entry.getUri();}

		// --- TreeNodeExpandable --- //
		@Override
		public void populateTreeNode(API api) {
			if (!initialized) {
				removeAllChildren();

				Collection<Container.Entry> entries = getChildren();

				while (entries.size() == 1) {
					Entry child = entries.iterator()
					                     .next();
					if (!child.isDirectory() || api.getTreeNodeFactory(child) != this) {
						break;
					}
					entries = child.getChildren();
				}

				for (Entry entry : entries) {
					TreeNodeFactory factory = api.getTreeNodeFactory(entry);
					if (factory != null) {
						add(factory.make(api,
						                 entry));
					}
				}

				initialized = true;
			}
		}

		public Collection<Container.Entry> getChildren() {return entry.getChildren();}
	}
}
