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
import al.clk.gui.api.model.Type;
import al.clk.gui.spi.TypeFactory;
import al.clk.gui.util.exception.ExceptionUtil;
import al.clk.gui.view.component.ModuleInfoFilePage;
import al.clk.gui.view.data.TreeNodeBean;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.util.Collection;
import java.util.regex.Pattern;

public class ModuleInfoFileTreeNodeFactoryProvider
				extends ClassFileTreeNodeFactoryProvider {
	protected static final ImageIcon MODULE_FILE_ICON
					                                 = new ImageIcon(ClassFileTreeNodeFactoryProvider.class.getClassLoader()
					                                                                                       .getResource("al/clk"
					                                                                                                    + "/gui"
					                                                                                                    +
					                                                                                                    "/images"
					                                                                                                    +
					                                                                                                    "/module_obj.png"));
	protected static final Factory   FACTORY = new Factory();

	static {
		// Early class loading
		try {
			Class.forName(ModuleInfoFilePage.class.getName());
		} catch (Exception e) {
			assert ExceptionUtil.printStackTrace(e);
		}
	}

	@Override
	public String[] getSelectors() {return appendSelectors("*:file:*/module-info.class");}

	@Override
	public Pattern getPathPattern() {return externalPathPattern;}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> T make(API api,
	                                                                                        Container.Entry entry) {
		int lastSlashIndex = entry.getPath()
		                          .lastIndexOf('/');
		String label = entry.getPath()
		                    .substring(lastSlashIndex + 1);
		return (T) new ModuleInfoFileTreeNode(entry,
		                                      new TreeNodeBean(label,
		                                                       CLASS_FILE_ICON),
		                                      FACTORY);
	}

	protected static class ModuleInfoFileTreeNode
					extends FileTreeNode {
		public ModuleInfoFileTreeNode(Container.Entry entry,
		                              Object userObject,
		                              PageAndTipFactory pageAndTipFactory) {
			super(entry,
			      null,
			      userObject,
			      pageAndTipFactory);
		}

		// --- TreeNodeExpandable --- //
		@Override
		public void populateTreeNode(API api) {
			if (!initialized) {
				removeAllChildren();
				// Create type node
				TypeFactory typeFactory = api.getTypeFactory(entry);

				if (typeFactory != null) {
					Collection<Type> types = typeFactory.make(api,
					                                          entry);

					for (Type type : types) {
						add(new BaseTreeNode(entry,
						                     type.getName(),
						                     new TreeNodeBean(type.getDisplayTypeName(),
						                                      MODULE_FILE_ICON),
						                     factory));
					}
				}

				initialized = true;
			}
		}
	}

	protected static class Factory
					implements AbstractTypeFileTreeNodeFactoryProvider.PageAndTipFactory {
		// --- PageAndTipFactory --- //
		@Override
		@SuppressWarnings("unchecked")
		public <T extends JComponent & UriGettable> T makePage(API a,
		                                                       Container.Entry e) {
			return (T) new ModuleInfoFilePage(a,
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
