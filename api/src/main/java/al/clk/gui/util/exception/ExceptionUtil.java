/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.util.exception;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

public class ExceptionUtil {
	/**
	 * Logs the exception using the Fernflower logger if available,
	 * otherwise falls back to standard error output.
	 * Always returns true for use with assert statements.
	 */
	public static boolean printStackTrace(Throwable throwable) {
		IFernflowerLogger logger = getLogger();
		if (logger != null) {
			logger.writeMessage("Exception occurred", IFernflowerLogger.Severity.ERROR, throwable);
		} else {
			// Fallback when no logger is available (e.g., during early initialization)
			System.err.println("ERROR: Exception occurred");
			throwable.printStackTrace(System.err);
		}
		return true;
	}

	/**
	 * Logs the exception with a custom message using the Fernflower logger.
	 */
	public static boolean printStackTrace(String message, Throwable throwable) {
		IFernflowerLogger logger = getLogger();
		if (logger != null) {
			logger.writeMessage(message, IFernflowerLogger.Severity.ERROR, throwable);
		} else {
			System.err.println("ERROR: " + message);
			throwable.printStackTrace(System.err);
		}
		return true;
	}

	private static IFernflowerLogger getLogger() {
		try {
			return DecompilerContext.getLogger();
		} catch (Exception e) {
			return null;
		}
	}
}
