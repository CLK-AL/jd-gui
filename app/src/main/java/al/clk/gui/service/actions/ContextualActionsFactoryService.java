/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.actions;

import al.clk.gui.api.API;
import al.clk.gui.api.model.Container;
import al.clk.gui.service.extension.ExtensionService;
import al.clk.gui.spi.ContextualActionsFactory;

import javax.swing.*;
import java.util.*;

public class ContextualActionsFactoryService {
	protected static final ContextualActionsFactoryService      CONTEXTUAL_ACTIONS_FACTORY_SERVICE
					                                                               = new ContextualActionsFactoryService();
	protected static final ActionNameComparator                 COMPARATOR = new ActionNameComparator();
	protected final        Collection<ContextualActionsFactory> providers  = ExtensionService.getInstance()
	                                                                                         .load(ContextualActionsFactory.class);

	public static ContextualActionsFactoryService getInstance() {return CONTEXTUAL_ACTIONS_FACTORY_SERVICE;}

	public Collection<Action> get(API api,
	                              Container.Entry entry,
	                              String fragment) {
		HashMap<String, ArrayList<Action>> mapActions = new HashMap<>();

		for (ContextualActionsFactory provider : providers) {
			Collection<Action> actions = provider.make(api,
			                                           entry,
			                                           fragment);

			for (Action action : actions) {
				String            groupName = (String) action.getValue(ContextualActionsFactory.GROUP_NAME);
				ArrayList<Action> list      = mapActions.get(groupName);

				if (list == null) {
					mapActions.put(groupName,
					               list = new ArrayList<>());
				}

				list.add(action);
			}
		}

		if (!mapActions.isEmpty()) {
			ArrayList<Action> result = new ArrayList<>();

			// Sort by group names
			ArrayList<String> groupNames = new ArrayList<>(mapActions.keySet());
			Collections.sort(groupNames);

			for (String groupName : groupNames) {
				if (!result.isEmpty()) {
					// Add 'null' to mark a separator
					result.add(null);
				}
				// Sort by names
				ArrayList<Action> actions = mapActions.get(groupName);
				Collections.sort(actions,
				                 COMPARATOR);
				result.addAll(actions);
			}

			return result;
		} else {
			return Collections.emptyList();
		}
	}

	protected static class ActionNameComparator
					implements Comparator<Action> {
		@Override
		public int compare(Action a1,
		                   Action a2) {
			String n1 = (String) a1.getValue(Action.NAME);
			if (n1 == null) {
				n1 = "";
			}

			String n2 = (String) a2.getValue(Action.NAME);
			if (n2 == null) {
				n2 = "";
			}

			return n1.compareTo(n2);
		}
	}
}
