/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.spi;

import al.clk.gui.api.API;
import al.clk.gui.api.model.Container;

import javax.swing.*;
import java.util.Collection;

public interface ContextualActionsFactory {
	String GROUP_NAME = "GroupNameKey";

	/**
	 * Build a collection of actions for 'entry' and 'fragment', grouped by GROUP_NAME and sorted by NAME. Null values
	 * are added for separators.
	 *
	 * @param fragment @see jd.gui.api.feature.UriOpenable
	 * @return a collection of actions
	 */
	Collection<Action> make(API api,
	                        Container.Entry entry,
	                        String fragment);
}
