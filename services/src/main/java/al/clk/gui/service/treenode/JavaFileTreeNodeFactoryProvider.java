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
import al.clk.gui.view.component.JavaFilePage;
import al.clk.gui.view.data.TreeNodeBean;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;

public class JavaFileTreeNodeFactoryProvider
				extends AbstractTypeFileTreeNodeFactoryProvider {
	protected static final ImageIcon JAVA_FILE_ICON =
					new ImageIcon(JavaFileTreeNodeFactoryProvider.class.getClassLoader()
	                                                                                                     .getResource(
					                                                                                                     "al/clk"
					                                                                                                     + "/gui"
					                                                                                                     +
					                                                                                                     "/images/jcu_obj.png"));
	protected static final Factory   FACTORY        = new Factory();

	@Override
	public String[] getSelectors() {return appendSelectors("*:file:*.java");}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> T make(API api,
	                                                                                        Container.Entry entry) {
		int lastSlashIndex = entry.getPath()
		                          .lastIndexOf('/');
		String label = entry.getPath()
		                    .substring(lastSlashIndex + 1);
		String location = new File(entry.getUri()).getPath();
		return (T) new FileTreeNode(entry,
		                            new TreeNodeBean(label,
		                                             "Location: " + location,
		                                             JAVA_FILE_ICON),
		                            FACTORY);
	}

	protected static class Factory
					implements AbstractTypeFileTreeNodeFactoryProvider.PageAndTipFactory {
		// --- PageAndTipFactory --- //
		@Override
		@SuppressWarnings("unchecked")
		public <T extends JComponent & UriGettable> T makePage(API a,
		                                                       Container.Entry e) {
			return (T) new JavaFilePage(a,
			                            e);
		}

		@Override
		public String makeTip(API api,
		                      Container.Entry entry) {
			String        location = new File(entry.getUri()).getPath();
			StringBuilder tip      = new StringBuilder("<html>Location: ");

			tip.append(location);
			tip.append("</html>");

			return tip.toString();
		}
	}
}
