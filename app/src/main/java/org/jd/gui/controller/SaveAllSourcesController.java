/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.controller;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.SourcesSavable;
import org.jd.gui.api.model.Container;
import org.jd.gui.service.preferencespanel.ClassFileDecompilerPreferences;
import org.jd.gui.service.preferencespanel.Preference;
import org.jd.gui.service.preferencespanel.VineflowerFileSaverPreferences;
import org.jd.gui.util.exception.ExceptionUtil;
import org.jd.gui.view.SaveAllSourcesView;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@SuppressWarnings("UnnecessaryLocalVariable")
public class SaveAllSourcesController
				implements SourcesSavable.Controller,
				           SourcesSavable.Listener {
	protected static final String             JD_CORE_VERSION     = "JdGuiPreferences.jdCoreVersion";
	protected static final String             VINEFLOWER_VERSION  = "JdGuiPreferences.vineflowerVersion";
	protected              API                api;
	protected              SaveAllSourcesView saveAllSourcesView;
	protected              boolean            cancel;
	protected              int                counter;
	protected              int                mask;

	public SaveAllSourcesController(API api,
	                                JFrame mainFrame) {
		this.api = api;
		// Create UI
		this.saveAllSourcesView = new SaveAllSourcesView(mainFrame,
		                                                 this::onCanceled);
	}

	public void show(ScheduledExecutorService executor,
	                 SourcesSavable savable,
	                 File toSourcesJarFile,
	                 Map<String, String> preferences) {
		// Show
		Container.Entry entry       = savable.getEntry();
		File            fromJarFile = new File(entry.getUri());
		this.saveAllSourcesView.show(fromJarFile,
		                             toSourcesJarFile);

		// Execute background task
		executor.execute(() -> {
			int fileCount = savable.getFileCount();

			saveAllSourcesView.updateProgressBar(0);
			saveAllSourcesView.setMaxValue(fileCount);

			cancel = false;
			counter = 0;
			mask = 2;

			while (fileCount > 64) {
				fileCount >>= 1;
				mask <<= 1;
			}

			mask--;

			try {
				Path path = Paths.get(toSourcesJarFile.toURI());
				Files.deleteIfExists(path);

				try {
					Path parentPath = path.getParent();

					if ((parentPath != null) && !Files.exists(parentPath)) {
						Files.createDirectories(parentPath);
					}
					boolean decompileWithVineflower = Preference.getBoolean(preferences,
					                                                        ClassFileDecompilerPreferences.decompileWithVineflower);
					if (decompileWithVineflower) {
						String[] vineflowerArgs = VineflowerFileSaverPreferences.toVineflowerJarArgs(fromJarFile,
						                                                                             toSourcesJarFile,
						                                                                             preferences);
						System.out.printf("Decompiling jar file with Vineflower %s%n"
						                  + "from %s%n"
						                  + "to %s%n"
						                  + "preferences=%s%n"
						                  + "vineflowerArgs=%s%n",
						                  preferences.get(VINEFLOWER_VERSION),
						                  fromJarFile,
						                  toSourcesJarFile,
						                  preferences,
						                  Arrays.toString(vineflowerArgs));
						ConsoleDecompiler.main(vineflowerArgs);
					} else {
						System.out.printf("Decompiling jar file with JD-Core %s%n" + "from %s%n" + "to %s%n" + "preferences=%s%n",
						                  preferences.get(JD_CORE_VERSION),
						                  fromJarFile,
						                  toSourcesJarFile,
						                  preferences);
						savable.save(api,
						             this,
						             this,
						             path);
					}
				} catch (Exception e) {
					assert ExceptionUtil.printStackTrace(e);
					saveAllSourcesView.showActionFailedDialog();
					cancel = true;
				}

				if (cancel) {
					Files.deleteIfExists(path);
				}
			} catch (Throwable t) {
				assert ExceptionUtil.printStackTrace(t);
			}
			saveAllSourcesView.hide();
		});
	}

	public boolean isActivated() {return saveAllSourcesView.isVisible();}

	protected void onCanceled()  {cancel = true;}

	// --- SourcesSavable.Controller --- //
	@Override
	public boolean isCancelled() {return cancel;}

	// --- SourcesSavable.Listener --- //
	@Override
	public void pathSaved(Path path) {
		if (((counter++) & mask) == 0) {
			saveAllSourcesView.updateProgressBar(counter);
		}
	}
}
