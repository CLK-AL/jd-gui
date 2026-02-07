/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.view.component;

import al.clk.gui.api.API;
import al.clk.gui.api.feature.ContentCopyable;
import al.clk.gui.api.feature.ContentSavable;
import al.clk.gui.api.feature.ContentSelectable;
import al.clk.gui.util.exception.ExceptionUtil;
import al.clk.gui.util.io.NewlineOutputStream;

import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class TextPage
				extends AbstractTextPage
				implements ContentCopyable,
				           ContentSelectable,
				           ContentSavable {

	// --- ContentCopyable --- //
	@Override
	public void copy() {
		if (textArea.getSelectionStart() == textArea.getSelectionEnd()) {
			getToolkit().getSystemClipboard()
			            .setContents(new StringSelection(""),
			                         null);
		} else {
			textArea.copyAsStyledText();
		}
	}

	// --- ContentSelectable --- //
	@Override
	public void selectAll() {
		textArea.selectAll();
	}

	// --- ContentSavable --- //
	@Override
	public String getFileName() {return "file.txt";}

	@Override
	public void save(API api,
	                 OutputStream os) {
		try (OutputStreamWriter writer = new OutputStreamWriter(new NewlineOutputStream(os),
		                                                        StandardCharsets.UTF_8)) {
			writer.write(textArea.getText());
		} catch (IOException e) {
			assert ExceptionUtil.printStackTrace(e);
		}
	}
}
