// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.main;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.SwitchHelper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.modules.renamer.PoolInterceptor;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.StructRecordComponent;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructModuleAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructPermittedSubclassesAttribute;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericClassDescriptor;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main class for writing decompiled Java class output.
 * Delegates to helper classes for specific writing tasks.
 */
public class ClassWriter {

	/**
	 * @deprecated Use {@link AnnotationWriter#ANNOTATION_ATTRIBUTES} instead
	 */
	@Deprecated
	public static final StructGeneralAttribute.Key<?>[] ANNOTATION_ATTRIBUTES = AnnotationWriter.ANNOTATION_ATTRIBUTES;

	/**
	 * @deprecated Use {@link AnnotationWriter#PARAMETER_ANNOTATION_ATTRIBUTES} instead
	 */
	@Deprecated
	public static final StructGeneralAttribute.Key<?>[] PARAMETER_ANNOTATION_ATTRIBUTES = AnnotationWriter.PARAMETER_ANNOTATION_ATTRIBUTES;

	/**
	 * @deprecated Use {@link AnnotationWriter#TYPE_ANNOTATION_ATTRIBUTES} instead
	 */
	@Deprecated
	public static final StructGeneralAttribute.Key<?>[] TYPE_ANNOTATION_ATTRIBUTES = AnnotationWriter.TYPE_ANNOTATION_ATTRIBUTES;

	private static final int CLASS_ALLOWED = CodeConstants.ACC_PUBLIC
	                                         | CodeConstants.ACC_PROTECTED
	                                         | CodeConstants.ACC_PRIVATE
	                                         | CodeConstants.ACC_ABSTRACT
	                                         | CodeConstants.ACC_STATIC
	                                         | CodeConstants.ACC_FINAL
	                                         | CodeConstants.ACC_STRICT;

	private static final int CLASS_EXCLUDED = CodeConstants.ACC_ABSTRACT
	                                          | CodeConstants.ACC_STATIC;

	private final PoolInterceptor interceptor;
	private final IFabricJavadocProvider javadocProvider;
	private final MethodWriter methodWriter;
	private final FieldWriter fieldWriter;

	public ClassWriter() {
		interceptor = DecompilerContext.getPoolInterceptor();
		javadocProvider = (IFabricJavadocProvider) DecompilerContext.getProperty(IFabricJavadocProvider.PROPERTY_NAME);
		methodWriter = new MethodWriter();
		fieldWriter = new FieldWriter();
	}

	/**
	 * Invokes processors on a class node.
	 */
	private static boolean invokeProcessors(TextBuffer buffer, ClassNode node) {
		ClassWrapper wrapper = node.getWrapper();
		if (wrapper == null) {
			buffer.append("/* $FF: Couldn't be decompiled. Class ")
			      .append(node.classStruct.qualifiedName)
			      .append(" wasn't processed yet! */")
			      .append("/* ");
			List<String> lines = new ArrayList<>(ErrorWriter.getErrorComment());
			for (String line : lines) {
				buffer.append("//");
				if (!line.isEmpty()) {
					buffer.append(' ').append(line);
				}
				buffer.appendLineSeparator();
			}
			return false;
		}
		StructClass cl = wrapper.getClassStruct();

		// Very late switch processing
		for (MethodWrapper method : wrapper.getMethods()) {
			if (method.root != null) {
				try {
					SwitchHelper.simplifySwitches(method.root, method.methodStruct);
				} catch (Throwable e) {
					DecompilerContext.getLogger().writeMessage(
						"Method " + method.methodStruct.getName() + " " + method.methodStruct.getDescriptor()
						+ " in class " + node.classStruct.qualifiedName + " couldn't be written.",
						IFernflowerLogger.Severity.WARN, e);
					method.decompileError = e;
				}
			}
		}

		try {
			InitializerProcessor.extractInitializers(wrapper);
			InitializerProcessor.hideInitalizers(wrapper);

			if (node.type == ClassNode.CLASS_ROOT && cl.getVersion().has14ClassReferences()
			    && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_CLASS_1_4)) {
				ClassReference14Processor.processClassReferences(node);
			}

			if (cl.hasModifier(CodeConstants.ACC_ENUM)
			    && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM)) {
				EnumProcessor.clearEnum(wrapper);
			}

			if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ASSERTIONS)) {
				AssertProcessor.buildAssertions(node);
			}
		} catch (Throwable t) {
			DecompilerContext.getLogger().writeMessage(
				"Class " + node.simpleName + " couldn't be written.",
				IFernflowerLogger.Severity.WARN, t);
			buffer.append("// $FF: Couldn't be decompiled").appendLineSeparator();
			if (DecompilerContext.getOption(IFernflowerPreferences.DUMP_EXCEPTION_ON_ERROR)) {
				List<String> lines = new ArrayList<>(ErrorWriter.getErrorComment());
				ErrorWriter.collectErrorLines(t, lines);
				for (String line : lines) {
					buffer.append("//");
					if (!line.isEmpty()) {
						buffer.append(' ').append(line);
					}
					buffer.appendLineSeparator();
				}
			}
			return false;
		}

		return true;
	}

	/**
	 * Writes package-info.java content.
	 */
	public static void packageInfoToJava(StructClass cl, TextBuffer buffer) {
		AnnotationWriter.appendAnnotations(buffer, 0, cl, -1);

		int index = cl.qualifiedName.lastIndexOf('/');
		String packageName = cl.qualifiedName.substring(0, index).replace('/', '.');
		buffer.append("package ").append(packageName).append(';')
		      .appendLineSeparator().appendLineSeparator();
	}

	/**
	 * Writes module-info.java content.
	 */
	public static void moduleInfoToJava(StructClass cl, TextBuffer buffer) {
		AnnotationWriter.appendAnnotations(buffer, 0, cl, -1);

		StructModuleAttribute moduleAttribute = cl.getAttribute(StructGeneralAttribute.ATTRIBUTE_MODULE);

		if ((moduleAttribute.moduleFlags & CodeConstants.ACC_OPEN) != 0) {
			buffer.append("open ");
		}

		buffer.append("module ").append(moduleAttribute.moduleName).append(" {").appendLineSeparator();

		writeModuleInfoBody(buffer, moduleAttribute);

		buffer.append('}').appendLineSeparator();
	}

	private static void writeModuleInfoBody(TextBuffer buffer, StructModuleAttribute moduleAttribute) {
		boolean newLineNeeded = false;

		List<StructModuleAttribute.RequiresEntry> requiresEntries = moduleAttribute.requires;
		if (!requiresEntries.isEmpty()) {
			for (StructModuleAttribute.RequiresEntry requires : requiresEntries) {
				if (!isGenerated(requires.flags)) {
					buffer.appendIndent(1).append("requires ")
					      .append(requires.moduleName.replace('/', '.'))
					      .append(';').appendLineSeparator();
					newLineNeeded = true;
				}
			}
		}

		List<StructModuleAttribute.ExportsEntry> exportsEntries = moduleAttribute.exports;
		if (!exportsEntries.isEmpty()) {
			if (newLineNeeded) {
				buffer.appendLineSeparator();
			}
			for (StructModuleAttribute.ExportsEntry exports : exportsEntries) {
				if (!isGenerated(exports.flags)) {
					buffer.appendIndent(1).append("exports ")
					      .append(exports.packageName.replace('/', '.'));
					List<String> exportToModules = exports.exportToModules;
					if (exportToModules.size() > 0) {
						buffer.append(" to").appendLineSeparator();
						appendFQClassNames(buffer, exportToModules);
					}
					buffer.append(';').appendLineSeparator();
					newLineNeeded = true;
				}
			}
		}

		List<StructModuleAttribute.OpensEntry> opensEntries = moduleAttribute.opens;
		if (!opensEntries.isEmpty()) {
			if (newLineNeeded) {
				buffer.appendLineSeparator();
			}
			for (StructModuleAttribute.OpensEntry opens : opensEntries) {
				if (!isGenerated(opens.flags)) {
					buffer.appendIndent(1).append("opens ")
					      .append(opens.packageName.replace('/', '.'));
					List<String> opensToModules = opens.opensToModules;
					if (opensToModules.size() > 0) {
						buffer.append(" to").appendLineSeparator();
						appendFQClassNames(buffer, opensToModules);
					}
					buffer.append(';').appendLineSeparator();
					newLineNeeded = true;
				}
			}
		}

		List<String> usesEntries = moduleAttribute.uses;
		if (!usesEntries.isEmpty()) {
			if (newLineNeeded) {
				buffer.appendLineSeparator();
			}
			for (String uses : usesEntries) {
				buffer.appendIndent(1).append("uses ")
				      .append(ExprProcessor.buildJavaClassName(uses))
				      .append(';').appendLineSeparator();
			}
			newLineNeeded = true;
		}

		List<StructModuleAttribute.ProvidesEntry> providesEntries = moduleAttribute.provides;
		if (!providesEntries.isEmpty()) {
			if (newLineNeeded) {
				buffer.appendLineSeparator();
			}
			for (StructModuleAttribute.ProvidesEntry provides : providesEntries) {
				buffer.appendIndent(1).append("provides ")
				      .append(ExprProcessor.buildJavaClassName(provides.interfaceName))
				      .append(" with").appendLineSeparator();
				appendFQClassNames(buffer, provides.implementationNames.stream()
				                                                       .map(ExprProcessor::buildJavaClassName)
				                                                       .collect(Collectors.toList()));
				buffer.append(';').appendLineSeparator();
			}
		}
	}

	private static boolean isGenerated(int flags) {
		return (flags & (CodeConstants.ACC_SYNTHETIC | CodeConstants.ACC_MANDATED)) != 0;
	}

	private static boolean isSuperClassSealed(StructClass cl) {
		if (cl.superClass != null) {
			StructClass superClass = DecompilerContext.getStructContext().getClass((String) cl.superClass.value);
			if (superClass != null && superClass.hasAttribute(StructGeneralAttribute.ATTRIBUTE_PERMITTED_SUBCLASSES)) {
				return true;
			}
		}
		for (String iface : cl.getInterfaceNames()) {
			StructClass ifaceClass = DecompilerContext.getStructContext().getClass(iface);
			if (ifaceClass != null && ifaceClass.hasAttribute(StructGeneralAttribute.ATTRIBUTE_PERMITTED_SUBCLASSES)) {
				return true;
			}
		}
		return false;
	}

	private static void appendFQClassNames(TextBuffer buffer, List<String> names) {
		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i);
			buffer.appendIndent(2).append(name);
			if (i < names.size() - 1) {
				buffer.append(',').appendLineSeparator();
			}
		}
	}

	/**
	 * Writes a lambda class to the buffer.
	 */
	public void classLambdaToJava(ClassNode node, TextBuffer buffer, Exprent method_object, int indent) {
		ClassWrapper wrapper = node.getWrapper();
		if (wrapper == null) {
			return;
		}

		boolean lambdaToAnonymous = DecompilerContext.getOption(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS);

		ClassNode outerNode = (ClassNode) DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
		DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, node);

		try {
			StructClass cl = wrapper.getClassStruct();

			DecompilerContext.getLogger().startWriteClass(node.simpleName);

			if (node.lambdaInformation.is_method_reference) {
				if (!node.lambdaInformation.is_content_method_static && method_object != null) {
					method_object.getInferredExprType(new VarType(CodeConstants.TYPE_OBJECT, 0,
					                                              node.lambdaInformation.content_class_name));
					TextBuffer instance = method_object.toJava(indent);
					if (method_object.type == Exprent.EXPRENT_FUNCTION
					    && ((FunctionExprent) method_object).getFuncType() == FunctionExprent.FUNCTION_CAST
					    && ((FunctionExprent) method_object).doesCast()) {
						buffer.append('(').append(instance).append(')');
					} else {
						buffer.append(instance);
					}
				} else {
					buffer.append(ExprProcessor.getCastTypeName(new VarType(node.lambdaInformation.content_class_name, true)));
				}

				buffer.append("::").append(CodeConstants.INIT_NAME.equals(node.lambdaInformation.content_method_name)
				                           ? "new" : node.lambdaInformation.content_method_name);
			} else {
				StructMethod mt = cl.getMethod(node.lambdaInformation.content_method_key);
				MethodWrapper methodWrapper = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());
				MethodDescriptor md_content = MethodDescriptor.parseDescriptor(node.lambdaInformation.content_method_descriptor);
				MethodDescriptor md_lambda = MethodDescriptor.parseDescriptor(node.lambdaInformation.method_descriptor);

				boolean simpleLambda = false;

				if (!lambdaToAnonymous) {
					boolean lambdaParametersNeedParentheses = md_lambda.params.length != 1;

					if (lambdaParametersNeedParentheses) {
						buffer.append('(');
					}

					boolean firstParameter = true;
					int index = node.lambdaInformation.is_content_method_static ? 0 : 1;
					int start_index = md_content.params.length - md_lambda.params.length;

					for (int i = 0; i < md_content.params.length; i++) {
						if (i >= start_index) {
							if (!firstParameter) {
								buffer.append(", ");
							}

							String parameterName = methodWrapper.varproc.getVarName(new VarVersionPair(index, 0));
							buffer.append(parameterName == null ? "param" + index : parameterName);

							firstParameter = false;
						}

						index += md_content.params[i].stackSize;
					}

					if (lambdaParametersNeedParentheses) {
						buffer.append(")");
					}
					buffer.append(" ->");

					RootStatement root = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor()).root;
					if (DecompilerContext.getOption(IFernflowerPreferences.INLINE_SIMPLE_LAMBDAS)
					    && methodWrapper.decompileError == null && root != null) {
						Statement firstStat = root.getFirst();
						if (firstStat.type == Statement.TYPE_BASICBLOCK
						    && firstStat.getExprents() != null
						    && firstStat.getExprents().size() == 1) {
							Exprent firstExpr = firstStat.getExprents().get(0);
							boolean isVarDefinition = firstExpr.type == Exprent.EXPRENT_ASSIGNMENT
							                          && ((AssignmentExprent) firstExpr).getLeft().type == Exprent.EXPRENT_VAR
							                          && ((VarExprent) ((AssignmentExprent) firstExpr).getLeft()).isDefinition();

							boolean isThrow = firstExpr.type == Exprent.EXPRENT_EXIT
							                  && ((ExitExprent) firstExpr).getExitType() == ExitExprent.EXIT_THROW;

							if (!isVarDefinition && !isThrow) {
								simpleLambda = true;
								MethodWrapper outerWrapper = (MethodWrapper) DecompilerContext.getProperty(
									DecompilerContext.CURRENT_METHOD_WRAPPER);
								DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methodWrapper);
								try {
									TextBuffer codeBuffer = firstExpr.toJava(indent + 1);

									if (firstExpr.type == Exprent.EXPRENT_EXIT) {
										codeBuffer.setStart(6);
									} else {
										codeBuffer.prepend(" ");
									}

									codeBuffer.addBytecodeMapping(root.getDummyExit().bytecode);
									buffer.append(codeBuffer, node.classStruct.qualifiedName,
									              InterpreterUtil.makeUniqueKey(methodWrapper.methodStruct.getName(),
									                                            methodWrapper.methodStruct.getDescriptor()));
								} catch (Throwable ex) {
									DecompilerContext.getLogger().writeMessage(
										"Method " + mt.getName() + " " + mt.getDescriptor()
										+ " in class " + node.classStruct.qualifiedName + " couldn't be written.",
										IFernflowerLogger.Severity.WARN, ex);
									methodWrapper.decompileError = ex;
									buffer.append(" // $FF: Couldn't be decompiled");
								} finally {
									DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, outerWrapper);
								}
							}
						}
					}
				}

				if (!simpleLambda) {
					buffer.append(" {").appendLineSeparator();

					MethodWriter.methodLambdaToJava(node, wrapper, mt, buffer, indent + 1, !lambdaToAnonymous);

					buffer.appendIndent(indent).append("}");
				}
			}
		} finally {
			DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, outerNode);
		}

		DecompilerContext.getLogger().endWriteClass();
	}

	/**
	 * Writes a class to the buffer.
	 */
	public void classToJava(ClassNode node, TextBuffer buffer, int indent) {
		ClassNode outerNode = (ClassNode) DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
		DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, node);

		try {
			boolean ok = invokeProcessors(buffer, node);

			if (!ok) {
				return;
			}

			ClassWrapper wrapper = node.getWrapper();
			StructClass cl = wrapper.getClassStruct();

			DecompilerContext.getLogger().startWriteClass(cl.qualifiedName);

			writeClassDefinition(node, buffer, indent);

			boolean hasContent = false;
			boolean enumFields = false;

			List<StructRecordComponent> components = cl.getRecordComponents();

			// fields
			for (org.jetbrains.java.decompiler.struct.StructField fd : cl.getFields()) {
				boolean hide = fd.isSynthetic() && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC)
				               || wrapper.getHiddenMembers().contains(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
				if (hide) {
					continue;
				}

				if (components != null
				    && fd.getAccessFlags() == (CodeConstants.ACC_FINAL | CodeConstants.ACC_PRIVATE)
				    && components.stream().anyMatch(c -> c.getName().equals(fd.getName())
				                                         && c.getDescriptor().equals(fd.getDescriptor()))) {
					continue;
				}

				boolean isEnum = fd.hasModifier(CodeConstants.ACC_ENUM)
				                 && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
				if (isEnum) {
					if (enumFields) {
						buffer.append(',').appendLineSeparator();
					}
					enumFields = true;
				} else if (enumFields) {
					buffer.append(';');
					buffer.appendLineSeparator();
					buffer.appendLineSeparator();
					enumFields = false;
				}

				TextBuffer fieldBuffer = new TextBuffer();
				fieldWriter.fieldToJava(wrapper, cl, fd, fieldBuffer, indent + 1);
				fieldBuffer.clearUnassignedBytecodeMappingData();
				buffer.append(fieldBuffer);

				hasContent = true;
			}

			if (enumFields) {
				buffer.append(';').appendLineSeparator();
			}

			// methods
			VBStyleCollection<StructMethod, String> methods = cl.getMethods();
			for (int i = 0; i < methods.size(); i++) {
				StructMethod mt = methods.get(i);
				boolean hide = mt.isSynthetic() && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC)
				               || mt.hasModifier(CodeConstants.ACC_BRIDGE)
				                  && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_BRIDGE)
				               || wrapper.getHiddenMembers().contains(InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));
				if (hide) {
					continue;
				}

				TextBuffer methodBuffer = new TextBuffer();
				boolean methodSkipped = !methodWriter.methodToJava(node, mt, i, methodBuffer, indent + 1);
				if (!methodSkipped) {
					if (hasContent) {
						buffer.appendLineSeparator();
					}
					hasContent = true;
					buffer.append(methodBuffer);
				}
			}

			// member classes
			for (ClassNode inner : node.nested) {
				if (inner.type == ClassNode.CLASS_MEMBER) {
					StructClass innerCl = inner.classStruct;
					boolean isSynthetic = (inner.access & CodeConstants.ACC_SYNTHETIC) != 0 || innerCl.isSynthetic();
					boolean hide = isSynthetic && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC)
					               || wrapper.getHiddenMembers().contains(innerCl.qualifiedName);
					if (hide) {
						continue;
					}

					if (hasContent) {
						buffer.appendLineSeparator();
					}
					classToJava(inner, buffer, indent + 1);

					hasContent = true;
				}
			}

			buffer.appendIndent(indent).append('}');

			if (node.type != ClassNode.CLASS_ANONYMOUS) {
				buffer.appendLineSeparator();
			}
		} finally {
			DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, outerNode);
		}

		DecompilerContext.getLogger().endWriteClass();
	}

	private void writeClassDefinition(ClassNode node, TextBuffer buffer, int indent) {
		if (node.type == ClassNode.CLASS_ANONYMOUS) {
			buffer.append(" {").appendLineSeparator();
			return;
		}

		ClassWrapper wrapper = node.getWrapper();
		StructClass cl = wrapper.getClassStruct();

		int flags = node.type == ClassNode.CLASS_ROOT ? cl.getAccessFlags() : node.access;
		boolean isDeprecated = cl.hasAttribute(StructGeneralAttribute.ATTRIBUTE_DEPRECATED);
		boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0
		                      || cl.hasAttribute(StructGeneralAttribute.ATTRIBUTE_SYNTHETIC);
		boolean isEnum = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM)
		                 && (flags & CodeConstants.ACC_ENUM) != 0;
		boolean isInterface = (flags & CodeConstants.ACC_INTERFACE) != 0;
		boolean isAnnotation = (flags & CodeConstants.ACC_ANNOTATION) != 0;
		boolean isModuleInfo = (flags & CodeConstants.ACC_MODULE) != 0
		                       && cl.hasAttribute(StructGeneralAttribute.ATTRIBUTE_MODULE);
		StructPermittedSubclassesAttribute permittedSubClassesAttr = cl.getAttribute(
			StructGeneralAttribute.ATTRIBUTE_PERMITTED_SUBCLASSES);
		List<String> permittedSubClasses = permittedSubClassesAttr != null
		                                   ? permittedSubClassesAttr.getClasses() : Collections.emptyList();
		boolean isSealed = permittedSubClassesAttr != null && !permittedSubClasses.isEmpty();
		boolean isNonSealed = !isSealed && cl.getVersion().hasSealedClasses() && isSuperClassSealed(cl);

		if (isDeprecated) {
			if (!AnnotationWriter.containsDeprecatedAnnotation(cl)) {
				CommentWriter.appendDeprecation(buffer, indent);
			}
		}

		if (interceptor != null) {
			String oldName = interceptor.getOldName(cl.qualifiedName);
			CommentWriter.appendRenameComment(buffer, oldName, CommentWriter.MType.CLASS, indent);
		}

		if (isSynthetic) {
			CommentWriter.appendComment(buffer, "synthetic class", indent);
		}

		if (javadocProvider != null) {
			CommentWriter.appendJavadoc(buffer, javadocProvider.getClassDoc(cl), indent);
		}

		AnnotationWriter.appendAnnotations(buffer, indent, cl, -1);

		buffer.appendIndent(indent);

		if (isEnum) {
			flags &= ~CodeConstants.ACC_ABSTRACT;
			flags &= ~CodeConstants.ACC_FINAL;

			if (node.type == ClassNode.CLASS_LOCAL) {
				flags &= ~CodeConstants.ACC_STATIC;
			}
		}

		List<StructRecordComponent> components = cl.getRecordComponents();

		if (components != null) {
			flags &= ~CodeConstants.ACC_FINAL;
		}

		CommentWriter.appendModifiers(buffer, flags, CLASS_ALLOWED, isInterface, CLASS_EXCLUDED);

		if (!isEnum && isSealed) {
			buffer.append("sealed ");
		} else if (isNonSealed) {
			buffer.append("non-sealed ");
		}
		if (isEnum) {
			buffer.append("enum ");
		} else if (isInterface) {
			if (isAnnotation) {
				buffer.append('@');
			}
			buffer.append("interface ");
		} else if (isModuleInfo) {
			StructModuleAttribute moduleAttribute = cl.getAttribute(StructGeneralAttribute.ATTRIBUTE_MODULE);

			if ((moduleAttribute.moduleFlags & CodeConstants.ACC_OPEN) != 0) {
				buffer.append("open ");
			}

			buffer.append("module ");
			buffer.append(moduleAttribute.moduleName);
		} else if (components != null) {
			buffer.append("record ");
		} else {
			buffer.append("class ");
		}
		buffer.append(node.simpleName);

		GenericClassDescriptor descriptor = cl.getSignature();
		if (descriptor != null && !descriptor.fparameters.isEmpty()) {
			GenericTypeWriter.appendTypeParameters(buffer, descriptor.fparameters, descriptor.fbounds);
		}

		if (components != null) {
			buffer.append('(');
			RecordHelper.appendRecordComponents(buffer, cl, components, indent);
			buffer.append(')');
		}

		buffer.pushNewlineGroup(indent, 1);

		if (!isEnum && !isInterface && components == null && cl.superClass != null) {
			VarType supertype = new VarType(cl.superClass.getString(), true);
			if (!VarType.VARTYPE_OBJECT.equals(supertype)) {
				buffer.appendPossibleNewline(" ");
				buffer.append("extends ");
				buffer.append(ExprProcessor.getCastTypeName(descriptor == null ? supertype : descriptor.superclass));
			}
		}

		if (!isAnnotation) {
			int[] interfaces = cl.getInterfaces();
			if (interfaces.length > 0) {
				buffer.appendPossibleNewline(" ");
				buffer.append(isInterface ? "extends " : "implements ");
				for (int i = 0; i < interfaces.length; i++) {
					if (i > 0) {
						buffer.append(",");
						buffer.appendPossibleNewline(" ");
					}
					buffer.append(ExprProcessor.getCastTypeName(descriptor == null
					                                            ? new VarType(cl.getInterface(i), true)
					                                            : descriptor.superinterfaces.get(i)));
				}
			}
		}

		if (!isEnum && isSealed) {
			buffer.appendPossibleNewline(" ");
			buffer.append("permits ");
			for (int i = 0; i < permittedSubClasses.size(); i++) {
				if (i > 0) {
					buffer.append(",");
					buffer.appendPossibleNewline(" ");
				}
				buffer.append(ExprProcessor.getCastTypeName(new VarType(permittedSubClasses.get(i), true)));
			}
		}

		buffer.popNewlineGroup();

		buffer.append(" {").appendLineSeparator();
	}

	// Delegate methods for backwards compatibility

	/**
	 * @deprecated Use {@link AnnotationWriter#appendAnnotations} instead
	 */
	@Deprecated
	public static void appendAnnotations(TextBuffer buffer, int indent, org.jetbrains.java.decompiler.struct.StructMember mb, int targetType) {
		AnnotationWriter.appendAnnotations(buffer, indent, mb, targetType);
	}

	/**
	 * @deprecated Use {@link GenericTypeWriter#appendTypeParameters} instead
	 */
	@Deprecated
	public static void appendTypeParameters(TextBuffer buffer, List<String> parameters, List<List<VarType>> bounds) {
		GenericTypeWriter.appendTypeParameters(buffer, parameters, bounds);
	}

	/**
	 * @deprecated Use {@link CommentWriter#getModifiers} instead
	 */
	@Deprecated
	public static String getModifiers(int flags) {
		return CommentWriter.getModifiers(flags);
	}

	/**
	 * @deprecated Use {@link ErrorWriter#getErrorComment} instead
	 */
	@Deprecated
	public static List<String> getErrorComment() {
		return ErrorWriter.getErrorComment();
	}

	/**
	 * @deprecated Use {@link ErrorWriter#collectErrorLines} instead
	 */
	@Deprecated
	public static void collectErrorLines(Throwable error, List<String> lines) {
		ErrorWriter.collectErrorLines(error, lines);
	}
}
