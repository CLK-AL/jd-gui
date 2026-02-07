// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class for writing comments, modifiers, and documentation to decompiled output.
 */
public final class CommentWriter {

	private static final Map<Integer, String> MODIFIERS;

	static {
		MODIFIERS = new LinkedHashMap<>();
		MODIFIERS.put(CodeConstants.ACC_PUBLIC, "public");
		MODIFIERS.put(CodeConstants.ACC_PROTECTED, "protected");
		MODIFIERS.put(CodeConstants.ACC_PRIVATE, "private");
		MODIFIERS.put(CodeConstants.ACC_ABSTRACT, "abstract");
		MODIFIERS.put(CodeConstants.ACC_STATIC, "static");
		MODIFIERS.put(CodeConstants.ACC_FINAL, "final");
		MODIFIERS.put(CodeConstants.ACC_STRICT, "strictfp");
		MODIFIERS.put(CodeConstants.ACC_TRANSIENT, "transient");
		MODIFIERS.put(CodeConstants.ACC_VOLATILE, "volatile");
		MODIFIERS.put(CodeConstants.ACC_SYNCHRONIZED, "synchronized");
		MODIFIERS.put(CodeConstants.ACC_NATIVE, "native");
	}

	/**
	 * Enum for member types used in rename comments.
	 */
	public enum MType {
		CLASS,
		FIELD,
		METHOD
	}

	private CommentWriter() {
		// Utility class
	}

	/**
	 * Appends a deprecation comment to the buffer.
	 *
	 * @param buffer the text buffer to append to
	 * @param indent the indentation level
	 */
	public static void appendDeprecation(TextBuffer buffer, int indent) {
		buffer.appendIndent(indent).append("/** @deprecated */").appendLineSeparator();
	}

	/**
	 * Appends a rename comment showing the original name before renaming.
	 *
	 * @param buffer  the text buffer to append to
	 * @param oldName the original name
	 * @param type    the member type (CLASS, FIELD, or METHOD)
	 * @param indent  the indentation level
	 */
	public static void appendRenameComment(TextBuffer buffer, String oldName, MType type, int indent) {
		if (oldName == null) {
			return;
		}

		buffer.appendIndent(indent);
		buffer.append("// $FF: renamed from: ");

		switch (type) {
			case CLASS:
				buffer.append(ExprProcessor.buildJavaClassName(oldName));
				break;

			case FIELD:
				String[] fParts = oldName.split(" ");
				FieldDescriptor fd = FieldDescriptor.parseDescriptor(fParts[2]);
				buffer.append(fParts[1]);
				buffer.append(' ');
				buffer.append(GenericTypeWriter.getTypePrintOut(fd.type));
				break;

			default:
				String[] mParts = oldName.split(" ");
				MethodDescriptor md = MethodDescriptor.parseDescriptor(mParts[2]);
				buffer.append(mParts[1]);
				buffer.append(" (");
				boolean first = true;
				for (VarType paramType : md.params) {
					if (!first) {
						buffer.append(", ");
					}
					first = false;
					buffer.append(GenericTypeWriter.getTypePrintOut(paramType));
				}
				buffer.append(") ");
				buffer.append(GenericTypeWriter.getTypePrintOut(md.ret));
		}

		buffer.appendLineSeparator();
	}

	/**
	 * Appends a general comment to the buffer.
	 *
	 * @param buffer  the text buffer to append to
	 * @param comment the comment text
	 * @param indent  the indentation level
	 */
	public static void appendComment(TextBuffer buffer, String comment, int indent) {
		buffer.appendIndent(indent).append("// $FF: ").append(comment).appendLineSeparator();
	}

	/**
	 * Appends javadoc to the buffer.
	 *
	 * @param buffer  the text buffer to append to
	 * @param javaDoc the javadoc content
	 * @param indent  the indentation level
	 */
	public static void appendJavadoc(TextBuffer buffer, String javaDoc, int indent) {
		if (javaDoc == null) {
			return;
		}
		buffer.appendIndent(indent).append("/**").appendLineSeparator();
		for (String s : javaDoc.split("\n")) {
			buffer.appendIndent(indent).append(" * ").append(s).appendLineSeparator();
		}
		buffer.appendIndent(indent).append(" */").appendLineSeparator();
	}

	/**
	 * Appends modifiers to the buffer.
	 *
	 * @param buffer      the text buffer to append to
	 * @param flags       the access flags
	 * @param allowed     mask of allowed modifiers
	 * @param isInterface whether the context is an interface
	 * @param excluded    mask of modifiers to exclude for interfaces
	 */
	public static void appendModifiers(TextBuffer buffer, int flags, int allowed, boolean isInterface, int excluded) {
		flags &= allowed;
		if (!isInterface) {
			excluded = 0;
		}
		for (int modifier : MODIFIERS.keySet()) {
			if ((flags & modifier) == modifier && (modifier & excluded) == 0) {
				buffer.append(MODIFIERS.get(modifier)).append(' ');
			}
		}
	}

	/**
	 * Gets a string representation of the given access flags.
	 *
	 * @param flags the access flags
	 * @return a space-separated string of modifier names
	 */
	public static String getModifiers(int flags) {
		return MODIFIERS.entrySet().stream()
		                .filter(e -> (e.getKey() & flags) != 0)
		                .map(Map.Entry::getValue)
		                .collect(Collectors.joining(" "));
	}
}
