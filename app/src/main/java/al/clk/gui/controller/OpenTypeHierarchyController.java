/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.controller;

import al.clk.gui.api.API;
import al.clk.gui.api.feature.IndexesChangeListener;
import al.clk.gui.api.model.Container;
import al.clk.gui.api.model.Indexes;
import al.clk.gui.util.net.UriUtil;
import al.clk.gui.view.OpenTypeHierarchyView;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public class OpenTypeHierarchyController
				implements IndexesChangeListener {
	private final ScheduledExecutorService    executor;
	protected     API                         api;
	protected     JFrame                      mainFrame;
	protected     OpenTypeHierarchyView       openTypeHierarchyView;
	protected     SelectLocationController    selectLocationController;
	protected     Collection<Future<Indexes>> collectionOfFutureIndexes;
	protected     Consumer<URI>               openCallback;

	public OpenTypeHierarchyController(API api,
	                                   ScheduledExecutorService executor,
	                                   JFrame mainFrame) {
		this.api = api;
		this.executor = executor;
		this.mainFrame = mainFrame;
		// Create UI
		openTypeHierarchyView = new OpenTypeHierarchyView(api,
		                                                  mainFrame,
		                                                  this::onTypeSelected);
		selectLocationController = new SelectLocationController(api,
		                                                        mainFrame);
	}

	public void show(Collection<Future<Indexes>> collectionOfFutureIndexes,
	                 Container.Entry entry,
	                 String typeName,
	                 Consumer<URI> openCallback) {
		// Init attributes
		this.collectionOfFutureIndexes = collectionOfFutureIndexes;
		this.openCallback = openCallback;
		executor.execute(() -> {
			// Waiting the end of indexation...
			openTypeHierarchyView.showWaitCursor();
			SwingUtilities.invokeLater(() -> {
				openTypeHierarchyView.hideWaitCursor();
				// Show
				openTypeHierarchyView.show(collectionOfFutureIndexes,
				                           entry,
				                           typeName);
			});
		});
	}

	protected void onTypeSelected(Point leftBottom,
	                              Collection<Container.Entry> entries,
	                              String typeName) {
		if (entries.size() == 1) {
			// Open the single entry uri
			openCallback.accept(UriUtil.createURI(api,
			                                      collectionOfFutureIndexes,
			                                      entries.iterator()
			                                             .next(),
			                                      null,
			                                      typeName));
		} else {
			// Multiple entries -> Open a "Select location" popup
			selectLocationController.show(new Point(leftBottom.x + (16 + 2),
			                                        leftBottom.y + 2),
			                              entries,
			                              (entry) -> openCallback.accept(UriUtil.createURI(api,
			                                                                               collectionOfFutureIndexes,
			                                                                               entry,
			                                                                               null,
			                                                                               typeName)),
			                              // entry selected
			                              () -> openTypeHierarchyView.focus());                                                               // popup closeClosure
		}
	}

	// --- IndexesChangeListener --- //
	public void indexesChanged(Collection<Future<Indexes>> collectionOfFutureIndexes) {
		if (openTypeHierarchyView.isVisible()) {
			// Update the list of containers
			this.collectionOfFutureIndexes = collectionOfFutureIndexes;
			// And refresh
			openTypeHierarchyView.updateTree(collectionOfFutureIndexes);
		}
	}
}
