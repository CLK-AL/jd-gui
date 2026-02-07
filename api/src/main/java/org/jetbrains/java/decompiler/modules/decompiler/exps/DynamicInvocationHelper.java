// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.List;

/**
 * Helper class for handling invokedynamic and constant dynamic operations.
 */
public final class DynamicInvocationHelper {

	private DynamicInvocationHelper() {
		// Utility class
	}

	/**
	 * Appends a bootstrap argument to the text buffer.
	 */
	public static void appendBootstrapArgument(TextBuffer buf, PooledConstant arg) {
		if (arg instanceof PrimitiveConstant) {
			PrimitiveConstant prim = ((PrimitiveConstant) arg);
			Object value = prim.value;
			String stringValue = String.valueOf(value);
			if (prim.type == CodeConstants.CONSTANT_Class) {
				buf.append(ExprProcessor.getCastTypeName(new VarType(stringValue)));
			} else if (prim.type == CodeConstants.CONSTANT_String) {
				buf.append('"')
				   .append(ConstExprent.convertStringToJava(stringValue, false))
				   .append('"');
			} else {
				buf.append(stringValue);
			}
		} else if (arg instanceof LinkConstant) {
			VarType cls = new VarType(((LinkConstant) arg).classname);
			buf.append(ExprProcessor.getCastTypeName(cls))
			   .append("::")
			   .append(((LinkConstant) arg).elementname);
		}
	}

	/**
	 * Renders the dynamic invocation name and bootstrap information.
	 */
	public static void renderDynamicInvocation(TextBuffer buf,
	                                           String name,
	                                           int invocationTyp,
	                                           LinkConstant bootstrapMethod,
	                                           List<PooledConstant> bootstrapArguments) {
		if (bootstrapMethod == null) {
			buf.append("<")
			   .append(name);
			if (invocationTyp == InvocationExprent.INVOKE_DYNAMIC) {
				buf.append(">invokedynamic");
			} else {
				buf.append(">ldc");
			}
		} else {
			buf.append(bootstrapMethod.elementname);
			buf.append("<\"")
			   .append(name)
			   .append('"');
			for (PooledConstant arg : bootstrapArguments) {
				buf.append(',');
				appendBootstrapArgument(buf, arg);
			}
			buf.append('>');
		}
	}
}
