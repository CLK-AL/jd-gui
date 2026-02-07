// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericType;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.util.*;

/**
 * Helper class for rendering invocations to Java source code.
 */
public final class InvocationRenderer {

	private static final VarType JAVA_NIO_BUFFER = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/nio/Buffer");

	private InvocationRenderer() {
		// Utility class
	}

	/**
	 * Renders the instance part of the invocation.
	 */
	public static RenderInstanceResult renderInstance(InvocationExprent inv, int indent, TextBuffer buf) {
		String superQualifier = null;
		boolean isInstanceThis = false;
		Exprent instance = inv.getInstance();

		if (instance != null && instance.type == Exprent.EXPRENT_VAR) {
			VarExprent instVar = (VarExprent) instance;
			VarVersionPair varPair = new VarVersionPair(instVar);

			VarProcessor varProc = instVar.getProcessor();
			if (varProc == null) {
				MethodWrapper currentMethod =
					(MethodWrapper) DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
				if (currentMethod != null) {
					varProc = currentMethod.varproc;
				}
			}

			String thisClassname = null;
			if (varProc != null) {
				thisClassname = varProc.getThisVars().get(varPair);
			}

			if (thisClassname != null) {
				isInstanceThis = true;

				if (inv.getInvocationTyp() == InvocationExprent.INVOKE_SPECIAL) {
					if (!inv.getClassname().equals(thisClassname)) {
						StructClass cl = DecompilerContext.getStructContext().getClass(inv.getClassname());
						boolean isInterface = cl != null && cl.hasModifier(CodeConstants.ACC_INTERFACE);
						superQualifier = !isInterface ? thisClassname : inv.getClassname();
					}
				}
			}
		}

		return new RenderInstanceResult(superQualifier, isInstanceThis);
	}

	/**
	 * Renders the full instance expression with any necessary casts.
	 */
	public static void renderFullInstance(InvocationExprent inv,
	                                       int indent,
	                                       TextBuffer buf,
	                                       String superQualifier,
	                                       boolean isQualifier) {
		Exprent instance = inv.getInstance();
		if (superQualifier != null) {
			TextUtil.writeQualifiedSuper(buf, superQualifier);
		} else if (instance != null) {
			StructClass cl = DecompilerContext.getStructContext().getClass(inv.getClassname());

			VarType leftType = new VarType(CodeConstants.TYPE_OBJECT, 0, inv.getClassname());
			if (!inv.getGenericsMap().isEmpty() && cl != null && cl.getSignature() != null) {
				VarType _new = cl.getSignature().genericType.remap(inv.getGenericsMap());
				if (_new != cl.getSignature().genericType) {
					leftType = _new;
				}
			}

			instance.setInvocationInstance();
			VarType rightType = instance.getInferredExprType(leftType);

			if (InvocationBoxingHelper.isUnboxingCall(inv) && !inv.shouldForceUnboxing()) {
				// we don't print the unboxing call - no need to bother with the instance wrapping / casting
				buf.addBytecodeMapping(inv.bytecode);
				if (instance.type == Exprent.EXPRENT_FUNCTION) {
					FunctionExprent func = (FunctionExprent) instance;
					if (func.getFuncType() == FunctionExprent.FUNCTION_CAST &&
					    func.getLstOperands().get(1).type == Exprent.EXPRENT_CONST) {
						ConstExprent _const = (ConstExprent) func.getLstOperands().get(1);
						boolean skipCast = false;

						if (func.getLstOperands().get(0).type == Exprent.EXPRENT_VAR) {
							VarType inferred = func.getLstOperands().get(0).getInferredExprType(leftType);
							skipCast = (inferred.type != CodeConstants.TYPE_OBJECT &&
							            inferred.type != CodeConstants.TYPE_GENVAR) ||
							           DecompilerContext.getStructContext()
							                            .instanceOf(inferred.value, inv.getClassname());
						} else if (inv.getClassname().equals(_const.getConstType().value)) {
							skipCast = true;
						}

						if (skipCast) {
							buf.append(func.getLstOperands().get(0).toJava(indent));
							return;
						}
					}
				}
				buf.append(instance.toJava(indent));
				return;
			}

			instance.setIsQualifier();

			boolean pushedCallChainGroup = false;
			if (!isQualifier) {
				buf.pushNewlineGroup(indent, 1);
				pushedCallChainGroup = true;
			}
			TextBuffer res = instance.toJava(indent);

			boolean skippedCast = false;

			if (instance.type == Exprent.EXPRENT_FUNCTION &&
			    ((FunctionExprent) instance).getFuncType() == FunctionExprent.FUNCTION_CAST) {
				skippedCast = !((FunctionExprent) instance).doesCast();

				if (skippedCast) {
					VarType castType = instance.getExprType();
					Exprent exp = ((FunctionExprent) instance).getLstOperands().get(0);
					while (exp.type == Exprent.EXPRENT_FUNCTION &&
					       ((FunctionExprent) exp).getFuncType() == FunctionExprent.FUNCTION_CAST) {
						if (exp.getExprType().equals(castType)) {
							skippedCast = !((FunctionExprent) exp).doesCast();
							List<Exprent> ops = ((FunctionExprent) exp).getLstOperands();
							exp = ops.get(0);
						} else {
							break;
						}
					}
				}
			}

			if (rightType.equals(VarType.VARTYPE_OBJECT) && !leftType.equals(rightType)) {
				buf.append("((")
				   .append(ExprProcessor.getCastTypeName(leftType))
				   .append(")");

				if (instance.getPrecedence() >= FunctionExprent.getPrecedence(FunctionExprent.FUNCTION_CAST)) {
					res.enclose("(", ")");
				}
				buf.append(res).append(")");
			} else if (instance.getPrecedence() > inv.getPrecedence() && !skippedCast) {
				buf.append("(").append(res).append(")");
			} else if (JAVA_NIO_BUFFER.equals(inv.getDescriptor().ret) &&
			           !JAVA_NIO_BUFFER.equals(rightType) &&
			           DecompilerContext.getStructContext().instanceOf(rightType.value, JAVA_NIO_BUFFER.value)) {
				buf.append("((")
				   .append(ExprProcessor.getCastTypeName(JAVA_NIO_BUFFER))
				   .append(")")
				   .append(res)
				   .append(")");
			} else {
				buf.append(res);
			}
			if (instance.allowNewlineAfterQualifier()) {
				buf.appendPossibleNewline();
			}
			if (pushedCallChainGroup) {
				// Note: The caller should handle popNewlineGroup
			}
		}
	}

	/**
	 * Renders the parameter list for the invocation.
	 */
	public static TextBuffer renderParamList(InvocationExprent inv,
	                                          int indent,
	                                          StructMethod desc,
	                                          Map<VarType, VarType> genericsMap) {
		TextBuffer buf = new TextBuffer();
		buf.pushNewlineGroup(indent, 1);

		List<VarVersionPair> mask = null;
		boolean isEnum = false;
		int functype = inv.getFunctype();
		String classname = inv.getClassname();
		String stringDescriptor = inv.getStringDescriptor();
		MethodDescriptor descriptor = inv.getDescriptor();
		List<Exprent> lstParameters = inv.getLstParameters();

		if (functype == InvocationExprent.TYP_INIT) {
			ClassNode newNode = DecompilerContext.getClassProcessor()
			                                     .getMapRootClasses()
			                                     .get(classname);
			if (newNode != null) {
				mask = ExprUtil.getSyntheticParametersMask(newNode, stringDescriptor, lstParameters.size());
				isEnum = newNode.classStruct.hasModifier(CodeConstants.ACC_ENUM) &&
				         DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
			}
		}

		ClassNode currCls = ((ClassNode) DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE));
		List<StructMethod> matches = InvocationMethodResolver.getMatchedDescriptors(
			classname, inv.getName(), stringDescriptor, descriptor);
		BitSet setAmbiguousParameters = InvocationMethodResolver.getAmbiguousParameters(
			classname, inv.getName(), stringDescriptor, descriptor, lstParameters, matches);

		// omit 'new Type[] {}' for the last parameter of a vararg method call
		if (lstParameters.size() == descriptor.params.length &&
		    InvocationMethodResolver.isVarArgCall(classname, inv.getName(), stringDescriptor, descriptor)) {
			Exprent lastParam = lstParameters.get(lstParameters.size() - 1);
			if (lastParam.type == Exprent.EXPRENT_NEW && lastParam.getExprType().arrayDim >= 1) {
				((NewExprent) lastParam).setVarArgParam(true);
			}
		}

		int start = isEnum ? 2 : 0;
		List<Exprent> parameters = new ArrayList<>(lstParameters);
		VarType[] types = Arrays.copyOf(descriptor.params, descriptor.params.length);

		// Process boxing/unboxing for parameters
		processParameterBoxing(inv, parameters, types, matches, currCls, start);

		// Infer generic types if needed
		if (desc == null) {
			inv.getInferredExprType(null);

			if (genericsMap.isEmpty() && inv.getInstance() != null &&
			    functype != InvocationExprent.TYP_INIT) {
				VarType instType = inv.getInstance().getInferredExprType(null);
				if (instType.isGeneric() && instType.type != CodeConstants.TYPE_GENVAR) {
					GenericType ginstance = (GenericType) instType;

					StructClass cls = DecompilerContext.getStructContext().getClass(instType.value);
					if (cls != null && cls.getSignature() != null) {
						cls.getSignature().genericType.mapGenVarsTo(ginstance, genericsMap);
					}
				}
			}
		}

		if (desc != null && desc.getSignature() != null) {
			Set<VarType> namedGens = inv.getNamedGenerics().keySet();
			int y = 0;
			for (int x = start; x < types.length; x++) {
				if (mask == null || mask.get(x) == null) {
					VarType type = desc.getSignature().parameterTypes.get(y++).remap(genericsMap);
					if (type != null && !(type.isGeneric() && ((GenericType) type).hasUnknownGenericType(namedGens))) {
						types[x] = type;
					}
				}
			}
		}

		boolean firstParameter = true;
		buf.appendPossibleNewline();
		buf.pushNewlineGroup(indent, 0);

		for (int i = start; i < lstParameters.size(); i++) {
			if (mask == null || mask.get(i) == null) {
				TextBuffer buff = new TextBuffer();
				boolean ambiguous = setAmbiguousParameters.get(i);

				if (i == parameters.size() - 1 &&
				    lstParameters.get(i).getExprType() == VarType.VARTYPE_NULL &&
				    NewExprent.probablySyntheticParameter(descriptor.params[i].value)) {
					break;  // skip last parameter of synthetic constructor call
				}

				ExprProcessor.getCastedExprent(lstParameters.get(i), types[i], buff, indent,
				                               true, ambiguous, true, true);

				if (buff.length() > 0) {
					if (!firstParameter) {
						buf.append(",").appendPossibleNewline(" ");
					}
					buf.append(buff);
				}

				firstParameter = false;
			}
		}

		buf.popNewlineGroup();
		buf.appendPossibleNewline("", true);
		buf.popNewlineGroup();

		return buf;
	}

	/**
	 * Processes boxing and unboxing for parameters.
	 */
	private static void processParameterBoxing(InvocationExprent inv,
	                                           List<Exprent> parameters,
	                                           VarType[] types,
	                                           List<StructMethod> matches,
	                                           ClassNode currCls,
	                                           int start) {
		String classname = inv.getClassname();
		String name = inv.getName();
		String stringDescriptor = inv.getStringDescriptor();
		MethodDescriptor descriptor = inv.getDescriptor();
		List<Exprent> lstParameters = inv.getLstParameters();

		for (int i = start; i < parameters.size(); i++) {
			Exprent par = parameters.get(i);
			if (par.type == Exprent.EXPRENT_INVOCATION) {
				InvocationExprent invPar = (InvocationExprent) par;

				if (InvocationBoxingHelper.isBoxingCall(invPar)) {
					Exprent value = invPar.getLstParameters().get(0);
					types[i] = value.getExprType();

					if (types[i].typeFamily == CodeConstants.TYPE_FAMILY_INTEGER) {
						types[i] = "java/lang/Short".equals(invPar.getClassname())
						           ? VarType.VARTYPE_SHORT
						           : "java/lang/Byte".equals(invPar.getClassname())
						             ? VarType.VARTYPE_BYTE
						             : "java/lang/Integer".equals(invPar.getClassname())
						               ? VarType.VARTYPE_INT
						               : VarType.VARTYPE_CHAR;
					}

					int count = 0;
					StructClass stClass = DecompilerContext.getStructContext().getClass(classname);
					if (stClass != null) {
						nextMethod:
						for (StructMethod mt : stClass.getMethods()) {
							if (name.equals(mt.getName()) &&
							    (currCls == null || InvocationMethodResolver.canAccess(currCls.classStruct, mt))) {
								MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());
								if (md.params.length == descriptor.params.length) {
									for (int x = 0; x < md.params.length; x++) {
										if (md.params[x].typeFamily != descriptor.params[x].typeFamily &&
										    md.params[x].typeFamily != types[x].typeFamily) {
											continue nextMethod;
										}
									}
									count++;
								}
							}
						}
					}

					if (count != matches.size()) {
						types[i] = descriptor.params[i];
						invPar.setForceBoxing(true);
					} else {
						value.addBytecodeOffsets(invPar.bytecode);
						parameters.set(i, value);
					}
				} else if (InvocationBoxingHelper.isUnboxingCall(invPar) && !invPar.shouldForceUnboxing()) {
					StructClass stClass = DecompilerContext.getStructContext().getClass(classname);

					if (stClass != null) {
						for (StructMethod mt : stClass.getMethods()) {
							if (name.equals(mt.getName()) &&
							    (currCls == null || InvocationMethodResolver.canAccess(currCls.classStruct, mt)) &&
							    !stringDescriptor.equals(mt.getDescriptor())) {
								MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());
								if (md.params.length == descriptor.params.length) {
									if (md.params[i].type == CodeConstants.TYPE_OBJECT) {
										if (DecompilerContext.getStructContext()
										                     .instanceOf(invPar.getInstance().getExprType().value,
										                                 md.params[i].value)) {
											invPar.forceUnboxing(true);
											break;
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Result of rendering the instance part.
	 */
	public static class RenderInstanceResult {
		public final String superQualifier;
		public final boolean isInstanceThis;

		public RenderInstanceResult(String superQualifier, boolean isInstanceThis) {
			this.superQualifier = superQualifier;
			this.isInstanceThis = isInstanceThis;
		}
	}
}
