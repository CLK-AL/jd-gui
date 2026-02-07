/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.view.component;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import al.clk.gui.api.API;
import al.clk.gui.api.feature.IndexesChangeListener;
import al.clk.gui.api.feature.UriGettable;
import al.clk.gui.api.model.Container;
import al.clk.gui.api.model.Indexes;
import al.clk.gui.util.exception.ExceptionUtil;
import al.clk.gui.util.index.IndexesUtil;
import al.clk.gui.util.io.TextReader;

import java.awt.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlFilePage
				extends TypeReferencePage
				implements UriGettable,
				           IndexesChangeListener {
	protected API                         api;
	protected Container.Entry             entry;
	protected Collection<Future<Indexes>> collectionOfFutureIndexes;

	public XmlFilePage(API api,
	                   Container.Entry entry) {
		this.api = api;
		this.entry = entry;
		// Load content file
		String text = TextReader.getText(entry.getInputStream());
		// Create hyperlinks
		Pattern pattern = Pattern.compile("(?s)<\\s*bean[^<]+class\\s*=\\s*\"([^\"]*)\"");
		Matcher matcher = pattern.matcher(textArea.getText());

		while (matcher.find()) {
			// Spring type reference found
			String value = matcher.group(1);
			String trim  = value.trim();

			if (trim != null) {
				int startIndex = matcher.start(1) - 1;
				int endIndex   = startIndex + value.length() + 2;
				String internalTypeName = trim.replace('.',
				                                       '/');
				addHyperlink(new TypeHyperlinkData(startIndex,
				                                   endIndex,
				                                   internalTypeName));
			}
		}
		// Display
		setText(text);
	}

	public String getSyntaxStyle()                                    {return SyntaxConstants.SYNTAX_STYLE_XML;}

	protected boolean isHyperlinkEnabled(HyperlinkData hyperlinkData) {return ((TypeHyperlinkData) hyperlinkData).enabled;}

	protected void openHyperlink(int x,
	                             int y,
	                             HyperlinkData hyperlinkData) {
		TypeHyperlinkData data = (TypeHyperlinkData) hyperlinkData;

		if (data.enabled) {
			try {
				// Save current position in history
				Point location = textArea.getLocationOnScreen();
				int offset = textArea.viewToModel(new Point(x - location.x,
				                                            y - location.y));
				URI uri = entry.getUri();
				api.addURI(new URI(uri.getScheme(),
				                   uri.getAuthority(),
				                   uri.getPath(),
				                   "position=" + offset,
				                   null));

				// Open link
				String internalTypeName = data.internalTypeName;
				List<Container.Entry> entries = IndexesUtil.findInternalTypeName(collectionOfFutureIndexes,
				                                                                 internalTypeName);
				String rootUri = entry.getContainer()
				                      .getRoot()
				                      .getUri()
				                      .toString();
				ArrayList<Container.Entry> sameContainerEntries = new ArrayList<>();

				for (Container.Entry entry : entries) {
					if (entry.getUri()
					         .toString()
					         .startsWith(rootUri)) {
						sameContainerEntries.add(entry);
					}
				}

				if (sameContainerEntries.size() > 0) {
					api.openURI(x,
					            y,
					            sameContainerEntries,
					            null,
					            data.internalTypeName);
				} else if (entries.size() > 0) {
					api.openURI(x,
					            y,
					            entries,
					            null,
					            data.internalTypeName);
				}
			} catch (URISyntaxException e) {
				assert ExceptionUtil.printStackTrace(e);
			}
		}
	}

	// --- UriGettable --- //
	public URI getUri() {return entry.getUri();}

	// --- ContentSavable --- //
	public String getFileName() {
		String path  = entry.getPath();
		int    index = path.lastIndexOf('/');
		return path.substring(index + 1);
	}

	// --- IndexesChangeListener --- //
	public void indexesChanged(Collection<Future<Indexes>> collectionOfFutureIndexes) {
		// Update the list of containers
		this.collectionOfFutureIndexes = collectionOfFutureIndexes;
		// Refresh links
		boolean refresh = false;

		for (Map.Entry<Integer, HyperlinkData> entry : hyperlinks.entrySet()) {
			TypeHyperlinkData data             = (TypeHyperlinkData) entry.getValue();
			String            internalTypeName = data.internalTypeName;
			boolean enabled = IndexesUtil.containsInternalTypeName(collectionOfFutureIndexes,
			                                                       internalTypeName);

			if (data.enabled != enabled) {
				data.enabled = enabled;
				refresh = true;
			}
		}

		if (refresh) {
			textArea.repaint();
		}
	}
}
