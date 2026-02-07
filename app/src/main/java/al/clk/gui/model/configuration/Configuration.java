/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.model.configuration;

import al.clk.gui.Constants;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Configuration {
	protected Point     mainWindowLocation;
	protected Dimension mainWindowSize;
	protected boolean   mainWindowMaximize;
	protected String    lookAndFeel;

	protected List<File> recentFiles = new ArrayList<>();

	protected File recentLoadDirectory;
	protected File recentSaveDirectory;

	protected Map<String, String> preferences = new HashMap<>();

	public Point getMainWindowLocation() {
		return mainWindowLocation;
	}

	public void setMainWindowLocation(Point mainWindowLocation) {
		this.mainWindowLocation = mainWindowLocation;
	}

	public Dimension getMainWindowSize() {
		return mainWindowSize;
	}

	public void setMainWindowSize(Dimension mainWindowSize) {
		this.mainWindowSize = mainWindowSize;
	}

	public boolean isMainWindowMaximize() {
		return mainWindowMaximize;
	}

	public void setMainWindowMaximize(boolean mainWindowMaximize) {
		this.mainWindowMaximize = mainWindowMaximize;
	}

	public String getLookAndFeel() {
		return lookAndFeel;
	}

	public void setLookAndFeel(String lookAndFeel) {
		this.lookAndFeel = lookAndFeel;
	}

	public List<File> getRecentFiles() {
		return recentFiles;
	}

	public void setRecentFiles(List<File> recentFiles) {
		this.recentFiles = recentFiles;
	}

	public File getRecentLoadDirectory() {
		return recentLoadDirectory;
	}

	public void setRecentLoadDirectory(File recentLoadDirectory) {
		this.recentLoadDirectory = recentLoadDirectory;
	}

	public File getRecentSaveDirectory() {
		return recentSaveDirectory;
	}

	public void setRecentSaveDirectory(File recentSaveDirectory) {
		this.recentSaveDirectory = recentSaveDirectory;
	}

	public Map<String, String> getPreferences() {
		return preferences;
	}

	public void setPreferences(Map<String, String> preferences) {
		this.preferences = preferences;
	}

	public void addRecentFile(File file) {
		recentFiles.remove(file);
		recentFiles.add(0,
		                file);
		if (recentFiles.size() > Constants.MAX_RECENT_FILES) {
			recentFiles.remove(Constants.MAX_RECENT_FILES);
		}
	}
}
