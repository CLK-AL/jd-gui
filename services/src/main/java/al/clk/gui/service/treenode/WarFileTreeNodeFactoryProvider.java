/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.treenode;

import al.clk.gui.api.API;
import al.clk.gui.api.feature.ContainerEntryGettable;
import al.clk.gui.api.feature.UriGettable;
import al.clk.gui.api.model.Container;
import al.clk.gui.view.data.TreeNodeBean;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;

public class WarFileTreeNodeFactoryProvider
				extends ZipFileTreeNodeFactoryProvider {
	protected static final ImageIcon ICON = new ImageIcon(JarFileTreeNodeFactoryProvider.class.getClassLoader()
	                                                                                          .getResource("al/clk/gui"
	                                                                                                       + "/images"
	                                                                                                       + "/war_obj"
	                                                                                                       + ".gif"));

	@Override
	public String[] getSelectors() {return appendSelectors("*:file:*.war");}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> T make(API api,
	                                                                                        Container.Entry entry) {
		int lastSlashIndex = entry.getPath()
		                          .lastIndexOf("/");
		String label = entry.getPath()
		                    .substring(lastSlashIndex + 1);
		String location = new File(entry.getUri()).getPath();
		T node = (T) new TreeNode(entry,
		                          new TreeNodeBean(label,
		                                           "Location: " + location,
		                                           ICON));
		// Add dummy node
		node.add(new DefaultMutableTreeNode());
		return node;
	}
}
