// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.main;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.NewExprent;
import org.jetbrains.java.decompiler.modules.renamer.PoolInterceptor;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.attr.StructConstantValueAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.TypeAnnotation;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericFieldDescriptor;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.AbstractMap;
import java.util.Map;

/**
 * Helper class for writing fields to decompiled output.
 */
public final class FieldWriter {

	private static final int FIELD_ALLOWED = CodeConstants.ACC_PUBLIC
	                                         | CodeConstants.ACC_PROTECTED
	                                         | CodeConstants.ACC_PRIVATE
	                                         | CodeConstants.ACC_STATIC
	                                         | CodeConstants.ACC_FINAL
	                                         | CodeConstants.ACC_TRANSIENT
	                                         | CodeConstants.ACC_VOLATILE;

	private static final int FIELD_EXCLUDED = CodeConstants.ACC_PUBLIC
	                                          | CodeConstants.ACC_STATIC
	                                          | CodeConstants.ACC_FINAL;

	private final PoolInterceptor interceptor;
	private final IFabricJavadocProvider javadocProvider;

	public FieldWriter() {
		interceptor = DecompilerContext.getPoolInterceptor();
		javadocProvider = (IFabricJavadocProvider) DecompilerContext.getProperty(IFabricJavadocProvider.PROPERTY_NAME);
	}

	/**
	 * Writes a field to the buffer.
	 *
	 * @param wrapper the class wrapper
	 * @param cl      the struct class
	 * @param fd      the struct field
	 * @param buffer  the text buffer to write to
	 * @param indent  the indentation level
	 */
	public void fieldToJava(ClassWrapper wrapper, StructClass cl, StructField fd, TextBuffer buffer, int indent) {
		boolean isInterface = cl.hasModifier(CodeConstants.ACC_INTERFACE);
		boolean isDeprecated = fd.hasAttribute(StructGeneralAttribute.ATTRIBUTE_DEPRECATED);
		boolean isEnum = fd.hasModifier(CodeConstants.ACC_ENUM)
		                 && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);

		if (isDeprecated) {
			if (!AnnotationWriter.containsDeprecatedAnnotation(fd)) {
				CommentWriter.appendDeprecation(buffer, indent);
			}
		}

		if (interceptor != null) {
			String oldName = interceptor.getOldName(cl.qualifiedName + " " + fd.getName() + " " + fd.getDescriptor());
			CommentWriter.appendRenameComment(buffer, oldName, CommentWriter.MType.FIELD, indent);
		}

		if (fd.isSynthetic()) {
			CommentWriter.appendComment(buffer, "synthetic field", indent);
		}

		if (javadocProvider != null) {
			CommentWriter.appendJavadoc(buffer, javadocProvider.getFieldDoc(cl, fd), indent);
		}
		AnnotationWriter.appendAnnotations(buffer, indent, fd, TypeAnnotation.FIELD);

		buffer.appendIndent(indent);

		if (!isEnum) {
			CommentWriter.appendModifiers(buffer, fd.getAccessFlags(), FIELD_ALLOWED, isInterface, FIELD_EXCLUDED);
		}

		Map.Entry<VarType, GenericFieldDescriptor> fieldTypeData = getFieldTypeData(fd);
		VarType fieldType = fieldTypeData.getKey();
		GenericFieldDescriptor descriptor = fieldTypeData.getValue();

		if (!isEnum) {
			buffer.append(ExprProcessor.getCastTypeName(descriptor == null ? fieldType : descriptor.type));
			buffer.append(' ');
		}

		buffer.append(fd.getName());

		Exprent initializer;
		if (fd.hasModifier(CodeConstants.ACC_STATIC)) {
			initializer = wrapper.getStaticFieldInitializers()
			                     .getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
		} else {
			initializer = wrapper.getDynamicFieldInitializers()
			                     .getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
		}
		if (initializer != null) {
			if (isEnum && initializer.type == Exprent.EXPRENT_NEW) {
				NewExprent expr = (NewExprent) initializer;
				expr.setEnumConst(true);
				buffer.append(expr.toJava(indent));
			} else {
				buffer.append(" = ");

				if (initializer.type == Exprent.EXPRENT_CONST) {
					((ConstExprent) initializer).adjustConstType(fieldType);
				}

				ExprProcessor.getCastedExprent(initializer, descriptor == null ? fieldType : descriptor.type,
				                               buffer, indent, false);
			}
		} else if (fd.hasModifier(CodeConstants.ACC_FINAL) && fd.hasModifier(CodeConstants.ACC_STATIC)) {
			StructConstantValueAttribute attr = fd.getAttribute(StructGeneralAttribute.ATTRIBUTE_CONSTANT_VALUE);
			if (attr != null) {
				PrimitiveConstant constant = cl.getPool().getPrimitiveConstant(attr.getIndex());
				buffer.append(" = ");
				buffer.append(new ConstExprent(fieldType, constant.value, null).toJava(indent));
			}
		}

		if (!isEnum) {
			buffer.append(";").appendLineSeparator();
		}
	}

	/**
	 * Gets the field type data including generic information.
	 *
	 * @param fd the struct field
	 * @return a map entry containing the VarType and optional GenericFieldDescriptor
	 */
	public static Map.Entry<VarType, GenericFieldDescriptor> getFieldTypeData(StructField fd) {
		VarType fieldType = new VarType(fd.getDescriptor(), false);
		GenericFieldDescriptor descriptor = fd.getSignature();
		return new AbstractMap.SimpleImmutableEntry<>(fieldType, descriptor);
	}
}
