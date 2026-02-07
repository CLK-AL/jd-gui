// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.modules.decompiler.exps.AnnotationExprent;
import org.jetbrains.java.decompiler.struct.StructMember;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructAnnotationAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructAnnotationParameterAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructTypeAnnotationAttribute;
import org.jetbrains.java.decompiler.struct.attr.TypeAnnotation;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class for writing annotations to decompiled output.
 */
public final class AnnotationWriter {

	static final StructGeneralAttribute.Key<?>[] ANNOTATION_ATTRIBUTES = {
		StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS,
		StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS
	};

	static final StructGeneralAttribute.Key<?>[] PARAMETER_ANNOTATION_ATTRIBUTES = {
		StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS,
		StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS
	};

	static final StructGeneralAttribute.Key<?>[] TYPE_ANNOTATION_ATTRIBUTES = {
		StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_TYPE_ANNOTATIONS,
		StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS
	};

	private AnnotationWriter() {
		// Utility class
	}

	/**
	 * Appends annotations for a struct member to the buffer.
	 *
	 * @param buffer     the text buffer to append to
	 * @param indent     the indentation level (-1 for inline)
	 * @param mb         the struct member (class, field, or method)
	 * @param targetType the target type for type annotations
	 */
	public static void appendAnnotations(TextBuffer buffer, int indent, StructMember mb, int targetType) {
		Set<String> filter = new HashSet<>();

		for (StructGeneralAttribute.Key<?> key : ANNOTATION_ATTRIBUTES) {
			StructAnnotationAttribute attribute = (StructAnnotationAttribute) mb.getAttribute(key);
			if (attribute != null) {
				for (AnnotationExprent annotation : attribute.getAnnotations()) {
					String text = annotation.toJava(indent).convertToStringAndAllowDataDiscard();
					filter.add(text);
					buffer.append(text);
					if (indent < 0) {
						buffer.append(' ');
					} else {
						buffer.appendLineSeparator();
					}
				}
			}
		}

		appendTypeAnnotations(buffer, indent, mb, targetType, -1, filter);
	}

	/**
	 * Appends parameter annotations for a method parameter.
	 *
	 * @param buffer the text buffer to append to
	 * @param mt     the struct method
	 * @param param  the parameter index
	 */
	public static void appendParameterAnnotations(TextBuffer buffer, StructMethod mt, int param) {
		Set<String> filter = new HashSet<>();

		for (StructGeneralAttribute.Key<?> key : PARAMETER_ANNOTATION_ATTRIBUTES) {
			StructAnnotationParameterAttribute attribute = (StructAnnotationParameterAttribute) mt.getAttribute(key);
			if (attribute != null) {
				List<List<AnnotationExprent>> annotations = attribute.getParamAnnotations();
				if (param < annotations.size()) {
					for (AnnotationExprent annotation : annotations.get(param)) {
						String text = annotation.toJava(-1).convertToStringAndAllowDataDiscard();
						filter.add(text);
						buffer.append(text).append(' ');
					}
				}
			}
		}

		appendTypeAnnotations(buffer, -1, mt, TypeAnnotation.METHOD_PARAMETER, param, filter);
	}

	/**
	 * Appends type annotations for a struct member.
	 *
	 * @param buffer     the text buffer to append to
	 * @param indent     the indentation level (-1 for inline)
	 * @param mb         the struct member
	 * @param targetType the target type
	 * @param index      the index (-1 for all)
	 * @param filter     set of already-written annotations to skip
	 */
	public static void appendTypeAnnotations(TextBuffer buffer, int indent, StructMember mb,
	                                          int targetType, int index, Set<String> filter) {
		for (StructGeneralAttribute.Key<?> key : TYPE_ANNOTATION_ATTRIBUTES) {
			StructTypeAnnotationAttribute attribute = (StructTypeAnnotationAttribute) mb.getAttribute(key);
			if (attribute != null) {
				for (TypeAnnotation annotation : attribute.getAnnotations()) {
					if (annotation.isTopLevel() && annotation.getTargetType() == targetType
					    && (index < 0 || annotation.getIndex() == index)) {
						String text = annotation.getAnnotation().toJava(indent).convertToStringAndAllowDataDiscard();
						if (!filter.contains(text)) {
							buffer.append(text);
							if (indent < 0) {
								buffer.append(' ');
							} else {
								buffer.appendLineSeparator();
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Checks if a struct member contains the @Deprecated annotation.
	 *
	 * @param mb the struct member to check
	 * @return true if the member has a @Deprecated annotation
	 */
	public static boolean containsDeprecatedAnnotation(StructMember mb) {
		for (StructGeneralAttribute.Key<?> key : ANNOTATION_ATTRIBUTES) {
			StructAnnotationAttribute attribute = (StructAnnotationAttribute) mb.getAttribute(key);
			if (attribute != null) {
				for (AnnotationExprent annotation : attribute.getAnnotations()) {
					if (annotation.getClassName().equals("java/lang/Deprecated")) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
