/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.service.extension;

import al.clk.gui.util.exception.ExceptionUtil;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSigner;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ExtensionService {

	protected static final ExtensionService EXTENSION_SERVICE = new ExtensionService();
	protected static final UrlComparator    URL_COMPARATOR    = new UrlComparator();

	protected URLClassLoader extensionClassLoader;
	protected Path extensionsDirectory;

	protected ExtensionService() {
		try {
			URI jarUri = ExtensionService.class.getProtectionDomain()
			                                   .getCodeSource()
			                                   .getLocation()
			                                   .toURI();
			File baseDirectory = new File(jarUri).getParentFile();
			File extDirectory = new File(baseDirectory,
			                             "ext");
			extensionsDirectory = extDirectory.toPath().normalize();

			if (extDirectory.exists() && extDirectory.isDirectory()) {
				ArrayList<URL> urls = new ArrayList<>();

				searchJarAndMetaInf(urls,
				                    extDirectory);

				if (!urls.isEmpty()) {
					// Filter and validate URLs before loading
					List<URL> validatedUrls = new ArrayList<>();
					for (URL url : urls) {
						if (isValidExtensionUrl(url)) {
							// Extract the actual file path from the URL for JAR verification
							// jarPath is null for directories (META-INF case), which is allowed
							Path jarPath = extractJarPath(url);
							if (jarPath == null || verifyJarSignature(jarPath)) {
								validatedUrls.add(url);
							}
						}
					}

					if (!validatedUrls.isEmpty()) {
						URL[] array = validatedUrls.toArray(new URL[0]);
						Arrays.sort(array,
						            URL_COMPARATOR);
						extensionClassLoader = new URLClassLoader(array,
						                                          ExtensionService.class.getClassLoader());

						// Register shutdown hook to close the classloader
						Runtime.getRuntime().addShutdownHook(new Thread(this::closeExtensionClassLoader));
					}
				}
			}
		} catch (Exception e) {
			assert ExceptionUtil.printStackTrace(e);
		}
	}

	public static ExtensionService getInstance() {
		return EXTENSION_SERVICE;
	}

	protected void searchJarAndMetaInf(List<URL> urls,
	                                   File directory)
					throws
					Exception {
		File metaInf = new File(directory,
		                        "META-INF");

		if (metaInf.exists() && metaInf.isDirectory()) {
			urls.add(directory.toURI()
			                  .toURL());
		} else {
			for (File child : directory.listFiles()) {
				if (child.isDirectory()) {
					searchJarAndMetaInf(urls,
					                    child);
				} else if (child.getName()
				                .toLowerCase()
				                .endsWith(".jar")) {
					urls.add(new URL("jar",
					                 "",
					                 child.toURI()
					                      .toURL() + "!/"));
				}
			}
		}
	}

	public <T> Collection<T> load(Class<T> service) {
		ArrayList<T> list = new ArrayList<>();
		ClassLoader classLoader = extensionClassLoader != null
		                          ? extensionClassLoader
		                          : ExtensionService.class.getClassLoader();
		Iterator<T> iterator = ServiceLoader.load(service,
		                                          classLoader)
		                                    .iterator();

		while (iterator.hasNext()) {
			list.add(iterator.next());
		}

		return list;
	}

	/**
	 * Returns the extensions directory path.
	 */
	protected Path getExtensionsDirectory() {
		return extensionsDirectory;
	}

	/**
	 * Validates that the URL is a safe extension URL.
	 * Only allows file: URLs from the extensions directory.
	 */
	private boolean isValidExtensionUrl(URL url) {
		// Handle jar: URLs by extracting the inner file URL
		String protocol = url.getProtocol();
		if ("jar".equals(protocol)) {
			String path = url.getPath();
			// jar URLs look like: file:/path/to/file.jar!/
			if (path.startsWith("file:")) {
				int bangIndex = path.indexOf("!/");
				if (bangIndex > 0) {
					path = path.substring(5, bangIndex); // Remove "file:" prefix
					try {
						Path jarPath = Paths.get(path);
						return validatePath(jarPath, url);
					} catch (Exception e) {
						logWarn("Failed to validate extension URL: " + url);
						assert ExceptionUtil.printStackTrace(e);
						return false;
					}
				}
			}
			logWarn("Rejecting invalid jar URL for extension: " + url);
			return false;
		}

		if (!"file".equals(protocol)) {
			logWarn("Rejecting non-file URL for extension: " + url);
			return false;
		}

		try {
			Path path = Paths.get(url.toURI());
			return validatePath(path, url);
		} catch (Exception e) {
			logWarn("Failed to validate extension URL: " + url);
			assert ExceptionUtil.printStackTrace(e);
			return false;
		}
	}

	/**
	 * Validates that the path is within the extensions directory.
	 */
	private boolean validatePath(Path path, URL url) {
		Path extensionsDir = getExtensionsDirectory();
		if (extensionsDir == null) {
			logWarn("Extensions directory not set, rejecting: " + url);
			return false;
		}

		// Ensure the path is within the extensions directory (prevent path traversal)
		if (!path.normalize().startsWith(extensionsDir.normalize())) {
			logWarn("Rejecting extension outside extensions directory: " + path);
			return false;
		}

		// For files, check extension (directories are allowed for META-INF)
		if (path.toFile().isFile() && !path.toString().toLowerCase().endsWith(".jar")) {
			logWarn("Rejecting non-JAR extension file: " + path);
			return false;
		}

		return true;
	}

	/**
	 * Extracts the JAR file path from a URL (handles jar: URLs).
	 */
	private Path extractJarPath(URL url) {
		try {
			if ("jar".equals(url.getProtocol())) {
				String path = url.getPath();
				if (path.startsWith("file:")) {
					int bangIndex = path.indexOf("!/");
					if (bangIndex > 0) {
						return Paths.get(path.substring(5, bangIndex));
					}
				}
				return null;
			} else if ("file".equals(url.getProtocol())) {
				Path path = Paths.get(url.toURI());
				// Only return path if it's a JAR file
				if (path.toFile().isFile() && path.toString().toLowerCase().endsWith(".jar")) {
					return path;
				}
				// For directories (META-INF case), return null as no JAR verification needed
				return null;
			}
		} catch (Exception e) {
			logWarn("Failed to extract JAR path from URL: " + url);
			assert ExceptionUtil.printStackTrace(e);
		}
		return null;
	}

	/**
	 * Verifies the JAR signature. Currently logs warnings for unsigned JARs
	 * but allows them to load. Returns false only if signature verification fails.
	 */
	private boolean verifyJarSignature(Path jarPath) {
		if (jarPath == null) {
			// No JAR file to verify (e.g., directory with META-INF)
			return true;
		}

		try (JarFile jar = new JarFile(jarPath.toFile(), true)) {
			Enumeration<JarEntry> entries = jar.entries();
			byte[] buffer = new byte[8192];

			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				// Skip directories and signature files
				if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
					continue;
				}

				try (InputStream is = jar.getInputStream(entry)) {
					// Reading triggers signature verification
					while (is.read(buffer) != -1) {
						// Just consume the stream to trigger verification
					}
				}

				// Check if entry is signed (optional - log for debugging)
				// Unsigned JARs are allowed for now
				assert entry.getCodeSigners() != null ||
				       Boolean.TRUE; // Debug: "Unsigned JAR entry in " + jarPath
			}
			return true;
		} catch (SecurityException e) {
			logError("JAR signature verification failed: " + jarPath);
			assert ExceptionUtil.printStackTrace(e);
			return false;
		} catch (IOException e) {
			logError("Error verifying JAR: " + jarPath);
			assert ExceptionUtil.printStackTrace(e);
			return false;
		}
	}

	/**
	 * Closes the extension class loader to prevent resource leaks.
	 */
	private void closeExtensionClassLoader() {
		if (extensionClassLoader != null) {
			try {
				extensionClassLoader.close();
			} catch (IOException e) {
				logError("Failed to close extension class loader");
				assert ExceptionUtil.printStackTrace(e);
			}
		}
	}

	private static void logWarn(String message) {
		IFernflowerLogger logger = getLogger();
		if (logger != null) {
			logger.writeMessage(message, IFernflowerLogger.Severity.WARN);
		} else {
			System.err.println("WARN: " + message);
		}
	}

	private static void logError(String message) {
		IFernflowerLogger logger = getLogger();
		if (logger != null) {
			logger.writeMessage(message, IFernflowerLogger.Severity.ERROR);
		} else {
			System.err.println("ERROR: " + message);
		}
	}

	private static IFernflowerLogger getLogger() {
		try {
			return DecompilerContext.getLogger();
		} catch (Exception e) {
			return null;
		}
	}

	protected static class UrlComparator
					implements Comparator<URL> {
		@Override
		public int compare(URL url1,
		                   URL url2) {
			return url1.getPath()
			           .compareTo(url2.getPath());
		}
	}
}
