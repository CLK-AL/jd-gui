/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.uriloader;

import al.clk.gui.api.API;
import al.clk.gui.service.extension.ExtensionService;
import al.clk.gui.spi.UriLoader;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;

public class UriLoaderService {
	protected static final UriLoaderService           URI_LOADER_SERVICE = new UriLoaderService();
	protected              HashMap<String, UriLoader> mapProviders       = new HashMap<>();

	protected UriLoaderService() {
		Collection<UriLoader> providers = ExtensionService.getInstance()
		                                                  .load(UriLoader.class);

		for (UriLoader provider : providers) {
			for (String scheme : provider.getSchemes()) {
				mapProviders.put(scheme,
				                 provider);
			}
		}
	}

	public static UriLoaderService getInstance() {return URI_LOADER_SERVICE;}

	public UriLoader get(API api,
	                     URI uri) {
		UriLoader provider = mapProviders.get(uri.getScheme());

		if (provider.accept(api,
		                    uri)) {
			return provider;
		} else {
			return null;
		}
	}
}
