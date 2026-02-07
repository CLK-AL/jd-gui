/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.view.data;

import al.clk.gui.api.model.TreeNodeData;

import javax.swing.*;

public class TreeNodeBean
				implements TreeNodeData {
	protected String label;
	protected String tip;
	protected Icon   icon;
	protected Icon   openIcon;

	public TreeNodeBean(String label,
	                    Icon icon) {
		this.label = label;
		this.icon = icon;
	}

	public TreeNodeBean(String label,
	                    String tip,
	                    Icon icon) {
		this.label = label;
		this.tip = tip;
		this.icon = icon;
	}

	public TreeNodeBean(String label,
	                    Icon icon,
	                    Icon openIcon) {
		this.label = label;
		this.icon = icon;
		this.openIcon = openIcon;
	}

	public TreeNodeBean(String label,
	                    String tip,
	                    Icon icon,
	                    Icon openIcon) {
		this.label = label;
		this.tip = tip;
		this.icon = icon;
		this.openIcon = openIcon;
	}

	@Override
	public String getLabel() {

		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	@Override
	public String getTip() {
		return tip;
	}

	public void setTip(String tip) {
		this.tip = tip;
	}

	@Override
	public Icon getIcon() {
		return icon;
	}

	public void setIcon(Icon icon) {
		this.icon = icon;
	}

	@Override
	public Icon getOpenIcon() {
		return openIcon;
	}

	public void setOpenIcon(Icon openIcon) {
		this.openIcon = openIcon;
	}
}
