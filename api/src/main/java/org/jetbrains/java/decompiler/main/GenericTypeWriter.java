// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.List;

/**
 * Helper class for writing generic type information to decompiled output.
 */
public final class GenericTypeWriter {

	private GenericTypeWriter() {
		// Utility class
	}

	/**
	 * Appends type parameters (generics) to the buffer.
	 *
	 * @param buffer     the text buffer to append to
	 * @param parameters the list of type parameter names
	 * @param bounds     the list of bounds for each type parameter
	 */
	public static void appendTypeParameters(TextBuffer buffer, List<String> parameters, List<List<VarType>> bounds) {
		buffer.append('<');

		for (int i = 0; i < parameters.size(); i++) {
			if (i > 0) {
				buffer.append(", ");
			}

			buffer.append(parameters.get(i));

			List<VarType> parameterBounds = bounds.get(i);
			if (parameterBounds.size() > 1 || !"java/lang/Object".equals(parameterBounds.get(0).value)) {
				buffer.append(" extends ");
				buffer.append(ExprProcessor.getCastTypeName(parameterBounds.get(0)));
				for (int j = 1; j < parameterBounds.size(); j++) {
					buffer.append(" & ");
					buffer.append(ExprProcessor.getCastTypeName(parameterBounds.get(j)));
				}
			}
		}

		buffer.append('>');
	}

	/**
	 * Gets a printable type name, handling undefined types.
	 *
	 * @param type the VarType to get the name for
	 * @return the printable type name
	 */
	public static String getTypePrintOut(VarType type) {
		String typeText = ExprProcessor.getCastTypeName(type, false);
		if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(typeText)
		    && DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
			typeText = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT, false);
		}
		return typeText;
	}
}
