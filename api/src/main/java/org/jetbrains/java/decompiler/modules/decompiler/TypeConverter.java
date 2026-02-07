// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericType;
import org.jetbrains.java.decompiler.util.TextUtil;

/**
 * Utility class for type name conversion and formatting.
 * Handles conversion between internal JVM type descriptors and
 * human-readable Java type names.
 */
public final class TypeConverter {

	public static final String UNDEFINED_TYPE_STRING = "<undefinedtype>";
	public static final String UNKNOWN_TYPE_STRING   = "<unknown>";
	public static final String NULL_TYPE_STRING      = "<null>";

	private TypeConverter() {
		// Utility class - prevent instantiation
	}

	/**
	 * Gets the simple type name for a VarType.
	 *
	 * @param type the type to convert
	 * @return the Java type name
	 */
	public static String getTypeName(VarType type) {
		return getTypeName(type, true);
	}

	/**
	 * Gets the type name for a VarType with optional short name conversion.
	 *
	 * @param type     the type to convert
	 * @param getShort if true, return short class names (without package)
	 * @return the Java type name
	 */
	public static String getTypeName(VarType type, boolean getShort) {
		int tp = type.type;
		if (tp <= CodeConstants.TYPE_BOOLEAN) {
			return InstructionTables.TYPE_NAMES[tp];
		} else if (tp == CodeConstants.TYPE_UNKNOWN) {
			return UNKNOWN_TYPE_STRING; // INFO: should not occur
		} else if (tp == CodeConstants.TYPE_NULL) {
			return NULL_TYPE_STRING; // INFO: should not occur
		} else if (tp == CodeConstants.TYPE_VOID) {
			return "void";
		} else if (tp == CodeConstants.TYPE_GENVAR && type.isGeneric()) {
			return type.value;
		} else if (tp == CodeConstants.TYPE_OBJECT) {
			if (type.isGeneric()) {
				return ((GenericType) type).getCastName();
			}
			String ret = buildJavaClassName(type.value);
			if (getShort) {
				ret = DecompilerContext.getImportCollector()
				                       .getShortName(ret);
			}

			if (ret == null) {
				// FIXME: a warning should be logged
				ret = UNDEFINED_TYPE_STRING;
			}
			return ret;
		}

		throw new RuntimeException("invalid type: " + tp);
	}

	/**
	 * Gets the cast type name with array dimensions.
	 *
	 * @param type the type to convert
	 * @return the cast type name with [] suffixes for arrays
	 */
	public static String getCastTypeName(VarType type) {
		return getCastTypeName(type, true);
	}

	/**
	 * Gets the cast type name with array dimensions and optional short name.
	 *
	 * @param type     the type to convert
	 * @param getShort if true, return short class names (without package)
	 * @return the cast type name with [] suffixes for arrays
	 */
	public static String getCastTypeName(VarType type, boolean getShort) {
		StringBuilder s = new StringBuilder(getTypeName(type, getShort));
		TextUtil.append(s, "[]", type.arrayDim);
		return s.toString();
	}

	/**
	 * Converts an internal JVM class name to a Java source class name.
	 * Replaces '/' with '.' and handles inner class '$' separators.
	 *
	 * @param name the internal class name (e.g., "java/lang/String")
	 * @return the Java source name (e.g., "java.lang.String")
	 */
	public static String buildJavaClassName(String name) {
		String res = name.replace('/', '.');

		if (res.contains("$")) { // attempt to invoke foreign member
			// classes correctly
			StructClass cl = DecompilerContext.getStructContext()
			                                  .getClass(name);
			if (cl == null || !cl.isOwn()) {
				res = res.replace('$', '.');
			}
		}

		return res;
	}
}
