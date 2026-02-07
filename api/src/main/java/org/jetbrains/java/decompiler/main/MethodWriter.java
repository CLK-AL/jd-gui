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
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarTypeProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.modules.renamer.PoolInterceptor;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.*;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericMethodDescriptor;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.List;

/**
 * Helper class for writing methods to decompiled output.
 */
public final class MethodWriter {

	private static final int METHOD_ALLOWED = CodeConstants.ACC_PUBLIC
	                                          | CodeConstants.ACC_PROTECTED
	                                          | CodeConstants.ACC_PRIVATE
	                                          | CodeConstants.ACC_ABSTRACT
	                                          | CodeConstants.ACC_STATIC
	                                          | CodeConstants.ACC_FINAL
	                                          | CodeConstants.ACC_SYNCHRONIZED
	                                          | CodeConstants.ACC_NATIVE
	                                          | CodeConstants.ACC_STRICT;

	private static final int METHOD_EXCLUDED = CodeConstants.ACC_PUBLIC
	                                           | CodeConstants.ACC_ABSTRACT;

	private final PoolInterceptor interceptor;
	private final IFabricJavadocProvider javadocProvider;

	public MethodWriter() {
		interceptor = DecompilerContext.getPoolInterceptor();
		javadocProvider = (IFabricJavadocProvider) DecompilerContext.getProperty(IFabricJavadocProvider.PROPERTY_NAME);
	}

	/**
	 * Writes a lambda method body to the buffer.
	 */
	public static void methodLambdaToJava(ClassNode lambdaNode, ClassWrapper classWrapper, StructMethod mt,
	                                       TextBuffer buffer, int indent, boolean codeOnly) {
		MethodWrapper methodWrapper = classWrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());

		MethodWrapper outerWrapper = (MethodWrapper) DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
		DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methodWrapper);

		try {
			String method_name = lambdaNode.lambdaInformation.method_name;
			MethodDescriptor md_content = MethodDescriptor.parseDescriptor(lambdaNode.lambdaInformation.content_method_descriptor);
			MethodDescriptor md_lambda = MethodDescriptor.parseDescriptor(lambdaNode.lambdaInformation.method_descriptor);

			if (!codeOnly) {
				buffer.appendIndent(indent);
				buffer.append("public ");
				buffer.append(method_name);
				buffer.append("(");

				boolean firstParameter = true;
				int index = lambdaNode.lambdaInformation.is_content_method_static ? 0 : 1;
				int start_index = md_content.params.length - md_lambda.params.length;

				for (int i = 0; i < md_content.params.length; i++) {
					if (i >= start_index) {
						if (!firstParameter) {
							buffer.append(", ");
						}

						String typeName = ExprProcessor.getCastTypeName(md_content.params[i].copy());
						if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(typeName)
						    && DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
							typeName = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT);
						}

						buffer.append(typeName);
						buffer.append(" ");

						String parameterName = methodWrapper.varproc.getVarName(new VarVersionPair(index, 0));
						buffer.append(parameterName == null ? "param" + index : parameterName);

						firstParameter = false;
					}

					index += md_content.params[i].stackSize;
				}

				buffer.append(") {").appendLineSeparator();

				indent += 1;
			}

			RootStatement root = classWrapper.getMethodWrapper(mt.getName(), mt.getDescriptor()).root;
			if (methodWrapper.decompileError == null) {
				if (root != null) {
					try {
						TextBuffer childBuf = root.toJava(indent);
						childBuf.addBytecodeMapping(root.getDummyExit().bytecode);
						buffer.append(childBuf, classWrapper.getClassStruct().qualifiedName,
						              InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));
					} catch (Throwable t) {
						String message = "Method " + mt.getName() + " " + mt.getDescriptor()
						                 + " in class " + lambdaNode.classStruct.qualifiedName + " couldn't be written.";
						DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN, t);
						methodWrapper.decompileError = t;
					}
				}
			}

			if (methodWrapper.decompileError != null) {
				ErrorWriter.dumpError(buffer, methodWrapper, indent);
			}

			if (!codeOnly) {
				indent -= 1;
				buffer.appendIndent(indent).append('}').appendLineSeparator();
			}
		} finally {
			DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, outerWrapper);
		}
	}

	/**
	 * Writes a method to the buffer.
	 *
	 * @return true if the method should be included in output, false if it should be hidden
	 */
	public boolean methodToJava(ClassNode node, StructMethod mt, int methodIndex, TextBuffer buffer, int indent) {
		ClassWrapper wrapper = node.getWrapper();
		StructClass cl = wrapper.getClassStruct();
		MethodWrapper methodWrapper = wrapper.getMethodWrapper(methodIndex);

		boolean hideMethod = false;

		MethodWrapper outerWrapper = (MethodWrapper) DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
		DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methodWrapper);

		try {
			boolean isInterface = cl.hasModifier(CodeConstants.ACC_INTERFACE);
			boolean isAnnotation = cl.hasModifier(CodeConstants.ACC_ANNOTATION);
			boolean isEnum = cl.hasModifier(CodeConstants.ACC_ENUM)
			                 && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
			boolean isDeprecated = mt.hasAttribute(StructGeneralAttribute.ATTRIBUTE_DEPRECATED);
			boolean clInit = false, init = false, dInit = false;

			MethodDescriptor md = MethodDescriptor.parseDescriptor(mt, node);

			int flags = mt.getAccessFlags();
			if ((flags & CodeConstants.ACC_NATIVE) != 0) {
				flags &= ~CodeConstants.ACC_STRICT;
			}
			if (CodeConstants.CLINIT_NAME.equals(mt.getName())) {
				flags &= CodeConstants.ACC_STATIC;
			}

			if (isDeprecated) {
				if (!AnnotationWriter.containsDeprecatedAnnotation(mt)) {
					CommentWriter.appendDeprecation(buffer, indent);
				}
			}

			if (interceptor != null) {
				String oldName = interceptor.getOldName(cl.qualifiedName + " " + mt.getName() + " " + mt.getDescriptor());
				CommentWriter.appendRenameComment(buffer, oldName, CommentWriter.MType.METHOD, indent);
			}

			boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0
			                      || mt.hasAttribute(StructGeneralAttribute.ATTRIBUTE_SYNTHETIC);
			boolean isBridge = (flags & CodeConstants.ACC_BRIDGE) != 0;
			if (isSynthetic) {
				CommentWriter.appendComment(buffer, "synthetic method", indent);
			}
			if (isBridge) {
				CommentWriter.appendComment(buffer, "bridge method", indent);
			}

			if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILER_COMMENTS) && methodWrapper.addErrorComment
			    || methodWrapper.commentLines != null) {
				if (methodWrapper.addErrorComment) {
					for (String s : ErrorWriter.getErrorComment()) {
						methodWrapper.addComment(s);
					}
				}

				for (String s : methodWrapper.commentLines) {
					buffer.appendIndent(indent).append("// " + s).appendLineSeparator();
				}
			}

			if (javadocProvider != null) {
				CommentWriter.appendJavadoc(buffer, javadocProvider.getMethodDoc(cl, mt), indent);
			}

			AnnotationWriter.appendAnnotations(buffer, indent, mt, TypeAnnotation.METHOD_RETURN_TYPE);

			// Try append @Override after all other annotations
			if (DecompilerContext.getOption(IFernflowerPreferences.OVERRIDE_ANNOTATION)
			    && mt.getBytecodeVersion().hasOverride()
			    && !CodeConstants.INIT_NAME.equals(mt.getName())
			    && !CodeConstants.CLINIT_NAME.equals(mt.getName())
			    && !mt.hasModifier(CodeConstants.ACC_STATIC)
			    && !mt.hasModifier(CodeConstants.ACC_PRIVATE)) {
				boolean isOverride = searchForMethod(cl, mt.getName(), md, false);
				if (isOverride) {
					buffer.appendIndent(indent);
					buffer.append("@Override");
					buffer.appendLineSeparator();
				}
			}

			buffer.appendIndent(indent);

			CommentWriter.appendModifiers(buffer, flags, METHOD_ALLOWED, isInterface, METHOD_EXCLUDED);

			if (isInterface
			    && !mt.hasModifier(CodeConstants.ACC_STATIC)
			    && mt.containsCode()
			    && (flags & CodeConstants.ACC_PRIVATE) == 0) {
				buffer.append("default ");
			}

			String name = mt.getName();
			if (CodeConstants.INIT_NAME.equals(name)) {
				if (node.type == ClassNode.CLASS_ANONYMOUS) {
					name = "";
					dInit = true;
				} else {
					name = node.simpleName;
					init = true;
				}
			} else if (CodeConstants.CLINIT_NAME.equals(name)) {
				name = "";
				clInit = true;
			}

			GenericMethodDescriptor descriptor = mt.getSignature();
			boolean throwsExceptions = false;
			int paramCount = 0;

			if (!clInit && !dInit) {
				boolean thisVar = !mt.hasModifier(CodeConstants.ACC_STATIC);

				if (descriptor != null && !descriptor.typeParameters.isEmpty()) {
					GenericTypeWriter.appendTypeParameters(buffer, descriptor.typeParameters, descriptor.typeParameterBounds);
					buffer.append(' ');
				}

				if (!init) {
					buffer.append(ExprProcessor.getCastTypeName(descriptor == null ? md.ret : descriptor.returnType));
					buffer.append(' ');
				}

				buffer.append(toValidJavaIdentifier(name));
				buffer.append('(');

				List<VarVersionPair> mask = methodWrapper.synthParameters;

				int lastVisibleParameterIndex = -1;
				for (int i = 0; i < md.params.length; i++) {
					if (mask == null || mask.get(i) == null) {
						lastVisibleParameterIndex = i;
					}
				}
				if (lastVisibleParameterIndex != -1) {
					buffer.pushNewlineGroup(indent, 1);
					buffer.appendPossibleNewline();
				}

				List<StructMethodParametersAttribute.Entry> methodParameters = null;
				if (DecompilerContext.getOption(IFernflowerPreferences.USE_METHOD_PARAMETERS)) {
					StructMethodParametersAttribute attr = mt.getAttribute(StructGeneralAttribute.ATTRIBUTE_METHOD_PARAMETERS);
					if (attr != null) {
						methodParameters = attr.getEntries();
					}
				}

				int index = isEnum && init ? 3 : thisVar ? 1 : 0;
				int start = isEnum && init ? 2 : 0;
				boolean hasDescriptor = descriptor != null;

				buffer.pushNewlineGroup(indent, 0);
				for (int i = start; i < md.params.length; i++) {
					VarType parameterType = hasDescriptor && paramCount < descriptor.parameterTypes.size()
					                        ? descriptor.parameterTypes.get(paramCount) : md.params[i];
					if (mask == null || mask.get(i) == null) {
						if (paramCount > 0) {
							buffer.append(",");
							buffer.appendPossibleNewline(" ");
						}

						AnnotationWriter.appendParameterAnnotations(buffer, mt, paramCount);

						if (methodParameters != null && i < methodParameters.size()) {
							CommentWriter.appendModifiers(buffer, methodParameters.get(i).myAccessFlags,
							                              CodeConstants.ACC_FINAL, isInterface, 0);
						} else if (methodWrapper.varproc.getVarFinal(new VarVersionPair(index, 0))
						           == VarTypeProcessor.VAR_EXPLICIT_FINAL) {
							buffer.append("final ");
						}

						String typeName;
						boolean isVarArg = i == lastVisibleParameterIndex
						                   && mt.hasModifier(CodeConstants.ACC_VARARGS)
						                   && parameterType.arrayDim > 0;
						if (isVarArg) {
							parameterType = parameterType.decreaseArrayDim();
						}
						typeName = ExprProcessor.getCastTypeName(parameterType);

						if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(typeName)
						    && DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
							typeName = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT);
						}
						buffer.append(typeName);
						if (isVarArg) {
							buffer.append("...");
						}

						buffer.append(' ');

						String parameterName;
						if (methodParameters != null && i < methodParameters.size()) {
							parameterName = methodParameters.get(i).myName;
						} else {
							parameterName = methodWrapper.varproc.getVarName(new VarVersionPair(index, 0));
						}

						if ((flags & (CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_NATIVE)) != 0) {
							String newParameterName = methodWrapper.methodStruct.getVariableNamer()
							                                                    .renameAbstractParameter(parameterName, index);
							parameterName = !newParameterName.equals(parameterName)
							                ? newParameterName
							                : DecompilerContext.getStructContext()
							                                   .renameAbstractParameter(
								                                   methodWrapper.methodStruct.getClassQualifiedName(),
								                                   mt.getName(), mt.getDescriptor(),
								                                   index - (((flags & CodeConstants.ACC_STATIC) == 0) ? 1 : 0),
								                                   parameterName);
						}

						buffer.append(parameterName == null ? "param" + index : parameterName);

						paramCount++;
					}

					index += parameterType.stackSize;
				}
				buffer.popNewlineGroup();

				if (lastVisibleParameterIndex != -1) {
					buffer.appendPossibleNewline("", true);
					buffer.popNewlineGroup();
				}
				buffer.append(')');

				StructExceptionsAttribute attr = mt.getAttribute(StructGeneralAttribute.ATTRIBUTE_EXCEPTIONS);
				if ((descriptor != null && !descriptor.exceptionTypes.isEmpty()) || attr != null) {
					throwsExceptions = true;
					buffer.append(" throws ");

					boolean useDescriptor = hasDescriptor && !descriptor.exceptionTypes.isEmpty();
					for (int i = 0; i < attr.getThrowsExceptions().size(); i++) {
						if (i > 0) {
							buffer.append(", ");
						}
						VarType type = useDescriptor
						               ? descriptor.exceptionTypes.get(i)
						               : new VarType(attr.getExcClassname(i, cl.getPool()), true);
						buffer.append(ExprProcessor.getCastTypeName(type));
					}
				}
			}

			if ((flags & (CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_NATIVE)) != 0) {
				if (isAnnotation) {
					StructAnnDefaultAttribute attr = mt.getAttribute(StructGeneralAttribute.ATTRIBUTE_ANNOTATION_DEFAULT);
					if (attr != null) {
						buffer.append(" default ");
						buffer.append(attr.getDefaultValue().toJava(0));
					}
				}

				buffer.append(';');
				buffer.appendLineSeparator();
			} else {
				if (!clInit && !dInit) {
					buffer.append(' ');
				}

				buffer.append('{').appendLineSeparator();

				RootStatement root = methodWrapper.root;

				if (root != null && methodWrapper.decompileError == null) {
					try {
						if (RecordHelper.isHiddenRecordMethod(cl, mt, root)) {
							hideMethod = true;
						} else {
							TextBuffer code = root.toJava(indent + 1);
							code.addBytecodeMapping(root.getDummyExit().bytecode);
							hideMethod = code.length() == 0 && (clInit || dInit || hideConstructor(node, init,
							                                                                        throwsExceptions, paramCount, flags));
							buffer.append(code, cl.qualifiedName,
							              InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));
						}
					} catch (Throwable t) {
						String message = "Method " + mt.getName() + " " + mt.getDescriptor()
						                 + " in class " + node.classStruct.qualifiedName + " couldn't be written.";
						DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN, t);
						methodWrapper.decompileError = t;
					}
				}

				if (methodWrapper.decompileError != null) {
					ErrorWriter.dumpError(buffer, methodWrapper, indent + 1);
				}
				buffer.appendIndent(indent).append('}').appendLineSeparator();
			}
		} finally {
			DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, outerWrapper);
		}

		return !hideMethod;
	}

	/**
	 * Converts a name to a valid Java identifier.
	 */
	public static String toValidJavaIdentifier(String name) {
		if (name == null || name.isEmpty()) {
			return name;
		}

		boolean changed = false;
		StringBuilder res = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if ((i == 0 && !Character.isJavaIdentifierStart(c)) || (i > 0 && !Character.isJavaIdentifierPart(c))) {
				changed = true;
				res.append("_");
			} else {
				res.append(c);
			}
		}
		if (!changed) {
			return name;
		}
		return res.append("/* $FF was: ").append(name).append("*/").toString();
	}

	/**
	 * Determines if a constructor should be hidden from the output.
	 */
	public static boolean hideConstructor(ClassNode node, boolean init, boolean throwsExceptions,
	                                        int paramCount, int methodAccessFlags) {
		if (!init || throwsExceptions || paramCount > 0
		    || !DecompilerContext.getOption(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR)) {
			return false;
		}

		ClassWrapper wrapper = node.getWrapper();
		StructClass cl = wrapper.getClassStruct();

		int classAccessFlags = node.type == ClassNode.CLASS_ROOT ? cl.getAccessFlags() : node.access;
		boolean isEnum = cl.hasModifier(CodeConstants.ACC_ENUM)
		                 && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);

		int accessibilityFlags = CodeConstants.ACC_PUBLIC | CodeConstants.ACC_PROTECTED | CodeConstants.ACC_PRIVATE;

		if (!isEnum && ((classAccessFlags & accessibilityFlags) != (methodAccessFlags & accessibilityFlags))) {
			return false;
		}

		int count = 0;
		for (StructMethod mt : cl.getMethods()) {
			if (CodeConstants.INIT_NAME.equals(mt.getName())) {
				if (++count > 1) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Searches for a method with the given name and descriptor in the inheritance tree.
	 */
	public static boolean searchForMethod(StructClass cl, String name, MethodDescriptor md, boolean search) {
		if (cl == null) {
			return false;
		}

		VBStyleCollection<StructMethod, String> methods = cl.getMethods();

		if (search) {
			for (StructMethod method : methods) {
				if (md.equals(MethodDescriptor.parseDescriptor(method.getDescriptor()))
				    && name.equals(method.getName())
				    && !method.hasModifier(CodeConstants.ACC_STATIC)) {
					return true;
				}
			}
		}

		if (cl.superClass != null) {
			StructClass superClass = DecompilerContext.getStructContext().getClass((String) cl.superClass.value);
			boolean foundInSuperClass = searchForMethod(superClass, name, md, true);
			if (foundInSuperClass) {
				return true;
			}
		}

		for (String ifaceName : cl.getInterfaceNames()) {
			StructClass iface = DecompilerContext.getStructContext().getClass(ifaceName);
			boolean foundInIface = searchForMethod(iface, name, md, true);
			if (foundInIface) {
				return true;
			}
		}

		return false;
	}
}
