// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.code.CodeConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for handling boxing and unboxing operations in invocations.
 */
public final class InvocationBoxingHelper {

	private static final Map<String, String> UNBOXING_METHODS;

	static {
		UNBOXING_METHODS = new HashMap<>();
		UNBOXING_METHODS.put("booleanValue", "java/lang/Boolean");
		UNBOXING_METHODS.put("byteValue", "java/lang/Byte");
		UNBOXING_METHODS.put("shortValue", "java/lang/Short");
		UNBOXING_METHODS.put("intValue", "java/lang/Integer");
		UNBOXING_METHODS.put("longValue", "java/lang/Long");
		UNBOXING_METHODS.put("floatValue", "java/lang/Float");
		UNBOXING_METHODS.put("doubleValue", "java/lang/Double");
		UNBOXING_METHODS.put("charValue", "java/lang/Character");
	}

	private InvocationBoxingHelper() {
		// Utility class
	}

	/**
	 * Checks if this invocation is a boxing call like Integer.valueOf(int).
	 */
	public static boolean isBoxingCall(InvocationExprent inv) {
		if (inv.isStatic() && "valueOf".equals(inv.getName()) && inv.getLstParameters().size() == 1) {
			int paramType = inv.getLstParameters().get(0).getExprType().type;
			List<Exprent> params = inv.getLstParameters();

			// special handling for ambiguous types
			if (params.get(0).type == Exprent.EXPRENT_CONST) {
				// 'Integer.valueOf(1)' has '1' type detected as TYPE_BYTECHAR
				// 'Integer.valueOf(40_000)' has '40_000' type detected as TYPE_CHAR
				// so we check the type family instead
				if (params.get(0).getExprType().typeFamily == CodeConstants.TYPE_FAMILY_INTEGER) {
					if (inv.getClassname().equals("java/lang/Integer")) {
						return true;
					}
				}

				if (paramType == CodeConstants.TYPE_BYTECHAR || paramType == CodeConstants.TYPE_SHORTCHAR) {
					if (inv.getClassname().equals("java/lang/Character") ||
					    inv.getClassname().equals("java/lang/Short")) {
						return true;
					}
				}
			}

			return inv.getClassname().equals(getClassNameForPrimitiveType(paramType));
		}

		return false;
	}

	/**
	 * Checks if this invocation is an unboxing call like intValue().
	 */
	public static boolean isUnboxingCall(InvocationExprent inv) {
		return !inv.isStatic() &&
		       inv.getLstParameters().isEmpty() &&
		       inv.getClassname().equals(UNBOXING_METHODS.get(inv.getName()));
	}

	/**
	 * Gets the wrapper class name for a primitive type.
	 */
	public static String getClassNameForPrimitiveType(int type) {
		switch (type) {
			case CodeConstants.TYPE_BOOLEAN:
				return "java/lang/Boolean";
			case CodeConstants.TYPE_BYTE:
			case CodeConstants.TYPE_BYTECHAR:
				return "java/lang/Byte";
			case CodeConstants.TYPE_CHAR:
				return "java/lang/Character";
			case CodeConstants.TYPE_SHORT:
			case CodeConstants.TYPE_SHORTCHAR:
				return "java/lang/Short";
			case CodeConstants.TYPE_INT:
				return "java/lang/Integer";
			case CodeConstants.TYPE_LONG:
				return "java/lang/Long";
			case CodeConstants.TYPE_FLOAT:
				return "java/lang/Float";
			case CodeConstants.TYPE_DOUBLE:
				return "java/lang/Double";
		}
		return null;
	}
}
