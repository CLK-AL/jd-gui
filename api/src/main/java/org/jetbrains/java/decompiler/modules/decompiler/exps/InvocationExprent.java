// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericType;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.ListStack;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.*;
import java.util.Map.Entry;

/**
 * Represents a method invocation expression in the decompiled code.
 * Delegates to helper classes for boxing, dynamic invocation, method resolution, and rendering.
 */
public class InvocationExprent extends Exprent {

	public static final int INVOKE_SPECIAL = 1;
	public static final int INVOKE_VIRTUAL = 2;
	public static final int INVOKE_STATIC = 3;
	public static final int INVOKE_INTERFACE = 4;
	public static final int INVOKE_DYNAMIC = 5;
	public static final int CONSTANT_DYNAMIC = 6;

	public static final int TYP_GENERAL = 1;
	public static final int TYP_INIT = 2;
	public static final int TYP_CLINIT = 3;

	private final List<VarType> genericArgs = new ArrayList<>();
	private final Map<VarType, VarType> genericsMap = new HashMap<>();

	private String name;
	private String classname;
	private boolean isStatic;
	private boolean canIgnoreBoxing = true;
	private int functype = TYP_GENERAL;
	private Exprent instance;
	private StructMethod desc = null;
	private MethodDescriptor descriptor;
	private String stringDescriptor;
	private String invokeDynamicClassSuffix;
	private int invocationTyp = INVOKE_VIRTUAL;
	private List<Exprent> lstParameters = new ArrayList<>();
	private LinkConstant bootstrapMethod;
	private List<PooledConstant> bootstrapArguments;
	private boolean isInvocationInstance = false;
	private boolean isQualifier = false;
	private boolean forceBoxing = false;
	private boolean forceUnboxing = false;
	private boolean isSyntheticNullCheck = false;

	public InvocationExprent() {
		super(EXPRENT_INVOCATION);
	}

	public InvocationExprent(int opcode, LinkConstant cn, LinkConstant bootstrapMethod,
	                         List<PooledConstant> bootstrapArguments,
	                         ListStack<? extends Exprent> stack, BitSet bytecodeOffsets) {
		this();
		name = cn.elementname;
		classname = cn.classname;
		this.bootstrapMethod = bootstrapMethod;
		this.bootstrapArguments = bootstrapArguments;

		initInvocationType(opcode, cn);
		initFuncType();
		initDescriptor(cn);
		initParameters(stack, opcode);
		addBytecodeOffsets(bytecodeOffsets);
	}

	private void initInvocationType(int opcode, LinkConstant cn) {
		switch (opcode) {
			case CodeConstants.opc_invokestatic:
				invocationTyp = INVOKE_STATIC;
				break;
			case CodeConstants.opc_invokespecial:
				invocationTyp = INVOKE_SPECIAL;
				break;
			case CodeConstants.opc_invokevirtual:
				invocationTyp = INVOKE_VIRTUAL;
				break;
			case CodeConstants.opc_invokeinterface:
				invocationTyp = INVOKE_INTERFACE;
				break;
			case CodeConstants.opc_invokedynamic:
				invocationTyp = INVOKE_DYNAMIC;
				classname = bootstrapMethod.classname;
				invokeDynamicClassSuffix = "##Lambda_" + cn.index1 + "_" + cn.index2;
				break;
			case CodeConstants.opc_ldc:
			case CodeConstants.opc_ldc_w:
			case CodeConstants.opc_ldc2_w:
				invocationTyp = CONSTANT_DYNAMIC;
				classname = bootstrapMethod.classname;
				invokeDynamicClassSuffix = "##Condy_" + cn.index1 + "_" + cn.index2;
				break;
		}
	}

	private void initFuncType() {
		if (CodeConstants.INIT_NAME.equals(name)) {
			functype = TYP_INIT;
		} else if (CodeConstants.CLINIT_NAME.equals(name)) {
			functype = TYP_CLINIT;
		}
	}

	private void initDescriptor(LinkConstant cn) {
		stringDescriptor = cn.descriptor;
		if (invocationTyp == CONSTANT_DYNAMIC) {
			stringDescriptor = "()" + stringDescriptor;
		}
		descriptor = MethodDescriptor.parseDescriptor(stringDescriptor);
	}

	private void initParameters(ListStack<? extends Exprent> stack, int opcode) {
		for (VarType ignored : descriptor.params) {
			lstParameters.add(0, stack.pop());
		}

		if (opcode == CodeConstants.opc_invokedynamic || invocationTyp == CONSTANT_DYNAMIC) {
			int dynamicInvocationType = -1;
			if (bootstrapArguments != null && bootstrapArguments.size() > 1) {
				PooledConstant link = bootstrapArguments.get(1);
				if (link instanceof LinkConstant) {
					dynamicInvocationType = ((LinkConstant) link).index1;
				}
			}
			if (dynamicInvocationType == CodeConstants.CONSTANT_MethodHandle_REF_invokeStatic) {
				isStatic = true;
			} else if (!lstParameters.isEmpty()) {
				instance = lstParameters.get(0);
			}
		} else if (opcode == CodeConstants.opc_invokestatic) {
			isStatic = true;
		} else {
			instance = stack.pop();
		}
	}

	private InvocationExprent(InvocationExprent expr) {
		this();
		name = expr.getName();
		classname = expr.getClassname();
		isStatic = expr.isStatic();
		canIgnoreBoxing = expr.canIgnoreBoxing;
		functype = expr.getFunctype();
		instance = expr.getInstance();
		if (instance != null) {
			instance = instance.copy();
		}
		invocationTyp = expr.getInvocationTyp();
		invokeDynamicClassSuffix = expr.getInvokeDynamicClassSuffix();
		stringDescriptor = expr.getStringDescriptor();
		descriptor = expr.getDescriptor();
		lstParameters = new ArrayList<>(expr.getLstParameters());
		ExprProcessor.copyEntries(lstParameters);
		addBytecodeOffsets(expr.bytecode);
		bootstrapMethod = expr.getBootstrapMethod();
		bootstrapArguments = expr.getBootstrapArguments();
		isSyntheticNullCheck = expr.isSyntheticNullCheck();

		if (invocationTyp == INVOKE_DYNAMIC && !isStatic && instance != null && !lstParameters.isEmpty()) {
			instance = lstParameters.get(0);
		}
	}

	@Override
	public VarType getExprType() {
		return descriptor.ret;
	}

	@Override
	public VarType getInferredExprType(VarType upperBound) {
		if (desc == null) {
			StructClass cl = DecompilerContext.getStructContext().getClass(classname);
			desc = cl != null ? cl.getMethodRecursive(name, stringDescriptor) : null;
		}

		genericArgs.clear();
		genericsMap.clear();

		StructClass mthCls = DecompilerContext.getStructContext().getClass(classname);
		if (desc != null && mthCls != null) {
			return inferGenericExprType(upperBound, mthCls);
		}
		return getExprType();
	}

	private VarType inferGenericExprType(VarType upperBound, StructClass mthCls) {
		boolean isNew = functype == TYP_INIT;
		boolean isGenNew = isNew && mthCls.getSignature() != null;
		if (desc.getSignature() == null && !isGenNew) {
			return getExprType();
		}

		Map<VarType, List<VarType>> named = getNamedGenerics();
		Map<VarType, List<VarType>> bounds = InvocationGenericHelper.getGenericBounds(desc, mthCls);

		List<String> fparams = isGenNew ? mthCls.getSignature().fparameters : desc.getSignature().typeParameters;
		VarType ret = isGenNew ? mthCls.getSignature().genericType : desc.getSignature().returnType;

		Map<VarType, VarType> tempMap = new HashMap<>();
		Map<VarType, VarType> upperBoundsMap = new HashMap<>();
		Map<VarType, VarType> hierarchyMap = new HashMap<>();

		processHierarchyMapping(mthCls, bounds, hierarchyMap);
		processUpperBound(upperBound, ret, named, bounds, upperBoundsMap, tempMap);
		addDummyMappings(fparams, mthCls, upperBoundsMap);
		processInstanceGenerics(mthCls, isNew, named, bounds, fparams, upperBoundsMap, tempMap);
		processInitGenerics(upperBound, isGenNew, mthCls);
		applyUpperBoundMappings(fparams, upperBoundsMap);
		processParameterGenerics(isNew, named, bounds, fparams, hierarchyMap, upperBoundsMap, tempMap);
		applyRemainingUpperBounds(fparams, named, bounds, upperBoundsMap);

		if (!genericsMap.isEmpty()) {
			return computeFinalReturnType(ret, hierarchyMap, fparams, isNew, isGenNew, named, upperBound, bounds);
		}

		if (ret.isGeneric() && ((GenericType) ret).getAllGenericVars().isEmpty()) {
			return ret;
		}
		return getExprType();
	}

	private void processHierarchyMapping(StructClass mthCls, Map<VarType, List<VarType>> bounds,
	                                     Map<VarType, VarType> hierarchyMap) {
		if (!classname.equals(desc.getClassQualifiedName())) {
			Map<String, Map<VarType, VarType>> hierarchy = mthCls.getAllGenerics();
			if (hierarchy.containsKey(desc.getClassQualifiedName())) {
				hierarchyMap.putAll(hierarchy.get(desc.getClassQualifiedName()));
				hierarchyMap.forEach((from, to) -> {
					if (to.type == CodeConstants.TYPE_GENVAR) {
						if (bounds.containsKey(to) && !bounds.containsKey(from)) {
							bounds.put(from, bounds.get(to));
						}
					} else if (!bounds.containsKey(from)) {
						genericsMap.put(from, to);
					}
				});
			}
		}
	}

	private void processUpperBound(VarType upperBound, VarType ret, Map<VarType, List<VarType>> named,
	                               Map<VarType, List<VarType>> bounds, Map<VarType, VarType> upperBoundsMap,
	                               Map<VarType, VarType> tempMap) {
		if (upperBound == null || upperBound.equals(VarType.VARTYPE_OBJECT) ||
		    (upperBound.type == CodeConstants.TYPE_GENVAR && !named.containsKey(upperBound))) {
			return;
		}

		VarType ub = upperBound;
		VarType r = ret;
		if (ub.type != CodeConstants.TYPE_GENVAR && r.type != CodeConstants.TYPE_GENVAR && !ub.value.equals(r.value)) {
			if (DecompilerContext.getStructContext().instanceOf(ub.value, r.value)) {
				ub = GenericType.getGenericSuperType(ub, r);
			} else {
				r = GenericType.getGenericSuperType(r, ub);
			}
		}

		if (r.type == CodeConstants.TYPE_GENVAR) {
			upperBoundsMap.put(r.resizeArrayDim(0), upperBound.resizeArrayDim(upperBound.arrayDim - r.arrayDim));
		} else {
			gatherGenerics(ub, r, tempMap);
			tempMap.forEach((from, to) -> {
				if (!genericsMap.containsKey(from) && to != null &&
				    (to.type != CodeConstants.TYPE_GENVAR || named.containsKey(to))) {
					if (InvocationGenericHelper.isMappingInBounds(from, to, named, bounds, genericsMap)) {
						upperBoundsMap.put(from, to);
					}
				}
			});
			tempMap.clear();
		}
	}

	private void addDummyMappings(List<String> fparams, StructClass mthCls, Map<VarType, VarType> upperBoundsMap) {
		fparams.stream().map(p -> "T" + p + ";").map(GenericType::parse)
		       .filter(t -> !upperBoundsMap.containsKey(t))
		       .forEach(t -> upperBoundsMap.put(t, GenericType.DUMMY_VAR));
		if (mthCls.getSignature() != null) {
			mthCls.getSignature().fparameters.stream().map(p -> "T" + p + ";").map(GenericType::parse)
			      .filter(t -> !upperBoundsMap.containsKey(t))
			      .forEach(t -> upperBoundsMap.put(t, GenericType.DUMMY_VAR));
		}
	}

	private void processInstanceGenerics(StructClass mthCls, boolean isNew, Map<VarType, List<VarType>> named,
	                                     Map<VarType, List<VarType>> bounds, List<String> fparams,
	                                     Map<VarType, VarType> upperBoundsMap, Map<VarType, VarType> tempMap) {
		if (instance == null || isNew) return;

		instance.setInvocationInstance();
		VarType instUB = mthCls.getSignature() != null
		                 ? mthCls.getSignature().genericType.remap(upperBoundsMap) : null;
		VarType instType;

		if (instance.type == EXPRENT_FUNCTION &&
		    ((FunctionExprent) instance).getFuncType() == FunctionExprent.FUNCTION_CAST) {
			instType = ((FunctionExprent) instance).getLstOperands().get(0).getInferredExprType(instUB);
		} else {
			instType = instance.getInferredExprType(instUB);
		}

		if (instType.type == CodeConstants.TYPE_GENVAR && named.containsKey(instType)) {
			instType = named.get(instType).get(0);
		}

		if (instType.isGeneric() && instType.type != CodeConstants.TYPE_GENVAR) {
			GenericType ginstance = (GenericType) instType;
			StructClass cls = DecompilerContext.getStructContext().getClass(instType.value);
			if (cls != null && cls.getSignature() != null) {
				cls.getSignature().genericType.mapGenVarsTo(ginstance, tempMap);
				tempMap.forEach((from, to) -> {
					if (!fparams.contains(from.value)) {
						InvocationGenericHelper.processGenericMapping(from, to, named, bounds, genericsMap);
					}
				});
				tempMap.clear();
			}
		}
	}

	private void processInitGenerics(VarType upperBound, boolean isGenNew, StructClass mthCls) {
		if (upperBound != null || !isGenNew) return;

		ClassNode currentCls = (ClassNode) DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
		if (currentCls != null) {
			if (mthCls.equals(currentCls.classStruct)) {
				mthCls.getSignature().genericType.getAllGenericVars().forEach(var -> genericsMap.put(var, var));
			} else {
				Map<String, Map<VarType, VarType>> hierarchy = currentCls.classStruct.getAllGenerics();
				if (hierarchy.containsKey(mthCls.qualifiedName)) {
					hierarchy.get(mthCls.qualifiedName).forEach(genericsMap::put);
				}
			}
		}
	}

	private void applyUpperBoundMappings(List<String> fparams, Map<VarType, VarType> upperBoundsMap) {
		if (!isInvocationInstance) {
			upperBoundsMap.forEach((k, v) -> {
				if (fparams.contains(k.value) && !GenericType.DUMMY_VAR.equals(v) && !genericsMap.containsKey(k)) {
					genericsMap.put(k, v);
				}
			});
		}
	}

	private void processParameterGenerics(boolean isNew, Map<VarType, List<VarType>> named,
	                                      Map<VarType, List<VarType>> bounds, List<String> fparams,
	                                      Map<VarType, VarType> hierarchyMap, Map<VarType, VarType> upperBoundsMap,
	                                      Map<VarType, VarType> tempMap) {
		if (lstParameters.isEmpty() || desc.getSignature() == null) return;

		List<VarVersionPair> mask = null;
		int start = 0;
		ClassNode newNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(classname);
		if (newNode != null) {
			if (isNew) {
				mask = ExprUtil.getSyntheticParametersMask(newNode, stringDescriptor, lstParameters.size());
				start = newNode.classStruct.hasModifier(CodeConstants.ACC_ENUM) ? 2 : 0;
			} else if (!newNode.enclosingClasses.isEmpty()) {
				start = !newNode.classStruct.hasModifier(CodeConstants.ACC_STATIC) ? 1 : 0;
			}
		}

		int j = 0;
		for (int i = start; i < lstParameters.size(); ++i) {
			if (mask == null || mask.get(i) == null) {
				VarType paramType = desc.getSignature().parameterTypes.get(j++);
				if (paramType.isGeneric()) {
					processParameterGeneric(i, paramType, named, bounds, fparams, hierarchyMap, upperBoundsMap, tempMap);
				}
			}
		}
	}

	private void processParameterGeneric(int i, VarType paramType, Map<VarType, List<VarType>> named,
	                                     Map<VarType, List<VarType>> bounds, List<String> fparams,
	                                     Map<VarType, VarType> hierarchyMap, Map<VarType, VarType> upperBoundsMap,
	                                     Map<VarType, VarType> tempMap) {
		Map<VarType, VarType> combined = new HashMap<>(genericsMap);
		upperBoundsMap.forEach((k, v) -> combined.putIfAbsent(k, v));
		VarType paramUB = paramType.remap(hierarchyMap).remap(combined);

		VarType argtype;
		if (lstParameters.get(i).type == EXPRENT_FUNCTION &&
		    ((FunctionExprent) lstParameters.get(i)).getFuncType() == FunctionExprent.FUNCTION_CAST) {
			argtype = ((FunctionExprent) lstParameters.get(i)).getLstOperands().get(0).getInferredExprType(paramUB);
		} else {
			argtype = lstParameters.get(i).getInferredExprType(paramUB);
		}

		StructClass paramCls = DecompilerContext.getStructContext().getClass(paramType.value);
		StructClass cls = argtype.type != CodeConstants.TYPE_GENVAR
		                  ? DecompilerContext.getStructContext().getClass(argtype.value) : null;

		if (cls != null && paramCls != null) {
			if (paramType.isGeneric() && !paramType.value.equals(argtype.value)) {
				argtype = GenericType.getGenericSuperType(argtype, paramType);
			}
			if (paramType.isGeneric() && argtype.isGeneric()) {
				((GenericType) paramType).mapGenVarsTo((GenericType) argtype, tempMap);
				tempMap.forEach((from, to) -> InvocationGenericHelper.processGenericMapping(from, to, named, bounds, genericsMap));
				tempMap.clear();
			}
		} else if (paramType.type == CodeConstants.TYPE_GENVAR && !paramType.equals(argtype) &&
		           argtype.arrayDim >= paramType.arrayDim) {
			VarType adjustedArg = paramType.arrayDim > 0 ? argtype.resizeArrayDim(argtype.arrayDim - paramType.arrayDim) : argtype;
			VarType adjustedParam = paramType.arrayDim > 0 ? paramType.resizeArrayDim(0) : paramType;
			InvocationGenericHelper.processGenericMapping(adjustedParam, adjustedArg, named, bounds, genericsMap);
		}
	}

	private void applyRemainingUpperBounds(List<String> fparams, Map<VarType, List<VarType>> named,
	                                       Map<VarType, List<VarType>> bounds, Map<VarType, VarType> upperBoundsMap) {
		upperBoundsMap.forEach((k, v) -> {
			if (fparams.contains(k.value) && !GenericType.DUMMY_VAR.equals(v)) {
				InvocationGenericHelper.processGenericMapping(k, v, named, bounds, genericsMap);
			}
		});
	}

	private VarType computeFinalReturnType(VarType ret, Map<VarType, VarType> hierarchyMap, List<String> fparams,
	                                       boolean isNew, boolean isGenNew, Map<VarType, List<VarType>> named,
	                                       VarType upperBound, Map<VarType, List<VarType>> bounds) {
		VarType newRet = ret.remap(hierarchyMap);
		boolean skipArgs = true;
		if (!fparams.isEmpty() && newRet.isGeneric()) {
			for (VarType genVar : ((GenericType) newRet).getAllGenericVars()) {
				if (fparams.contains(genVar.value)) {
					skipArgs = false;
					break;
				}
			}
		}

		newRet = newRet.remap(genericsMap);
		if (newRet == null) {
			newRet = bounds.get(ret).get(0).remap(genericsMap);
		}

		if (!skipArgs && (!isNew || isGenNew)) {
			Set<VarType> paramGenerics = collectParamGenerics(fparams);
			boolean missing = paramGenerics.isEmpty() ||
			                  fparams.stream().anyMatch(p -> !paramGenerics.contains(GenericType.parse("T" + p + ";")));

			boolean suppress = (!missing || !isInvocationInstance) &&
			                   (upperBound == null || !newRet.isGeneric() ||
			                    DecompilerContext.getStructContext().instanceOf(newRet.value, upperBound.value));

			if (!suppress || DecompilerContext.getOption(IFernflowerPreferences.EXPLICIT_GENERIC_ARGUMENTS)) {
				getGenericArgs(fparams, genericsMap, genericArgs);
			} else if (isGenNew) {
				genericArgs.add(GenericType.DUMMY_VAR);
			}
		}

		if (newRet != ret && !(newRet.isGeneric() && ((GenericType) newRet).hasUnknownGenericType(named.keySet()))) {
			return newRet;
		}
		return getExprType();
	}

	private Set<VarType> collectParamGenerics(List<String> fparams) {
		Set<VarType> paramGenerics = new HashSet<>();
		if (desc.getSignature() != null) {
			for (VarType paramType : desc.getSignature().parameterTypes) {
				if (paramType.isGeneric()) {
					((GenericType) paramType).getAllGenericVars().stream()
					    .filter(v -> fparams.contains(v.value) && genericsMap.containsKey(v))
					    .forEach(paramGenerics::add);
				} else if (paramType.type == CodeConstants.TYPE_GENVAR &&
				           fparams.contains(paramType.value) && genericsMap.containsKey(paramType)) {
					paramGenerics.add(paramType);
				}
			}
		}
		return paramGenerics;
	}

	@Override
	public CheckTypesResult checkExprTypeBounds() {
		CheckTypesResult result = new CheckTypesResult();
		if (instance != null) {
			result.addMinTypeExprent(instance, VarType.getMinTypeInFamily(instance.getExprType().typeFamily));
			result.addMaxTypeExprent(instance, instance.getExprType());
		}
		for (int i = 0; i < lstParameters.size(); i++) {
			Exprent parameter = lstParameters.get(i);
			VarType leftType = descriptor.params[i];
			result.addMinTypeExprent(parameter, VarType.getMinTypeInFamily(leftType.typeFamily));
			result.addMaxTypeExprent(parameter, leftType);
		}
		return result;
	}

	@Override
	public List<Exprent> getAllExprents(List<Exprent> lst) {
		if (instance != null) lst.add(instance);
		lst.addAll(lstParameters);
		return lst;
	}

	@Override
	public Exprent copy() {
		return new InvocationExprent(this);
	}

	@Override
	public TextBuffer toJava(int indent) {
		TextBuffer buf = new TextBuffer();
		if (instance instanceof InvocationExprent) {
			((InvocationExprent) instance).markUsingBoxingResult();
		}

		boolean pushedCallChainGroup = false;
		String superQualifier = null;
		boolean isInstanceThis = false;

		if (isStatic || invocationTyp == INVOKE_DYNAMIC || invocationTyp == CONSTANT_DYNAMIC) {
			if (renderStaticOrDynamic(buf, indent)) return buf;
		} else {
			InvocationRenderer.RenderInstanceResult result = InvocationRenderer.renderInstance(this, indent, buf);
			superQualifier = result.superQualifier;
			isInstanceThis = result.isInstanceThis;

			if (CodeConstants.isReturnPolymorphic(classname, name) && !descriptor.ret.equals(VarType.VARTYPE_VOID)) {
				buf.append('(').append(ExprProcessor.getCastTypeName(descriptor.ret)).append(')');
			}

			if (functype == TYP_GENERAL && (superQualifier != null || instance != null)) {
				if (!isQualifier) {
					buf.pushNewlineGroup(indent, 1);
					pushedCallChainGroup = true;
				}
				InvocationRenderer.renderFullInstance(this, indent, buf, superQualifier, isQualifier);
			}
		}

		renderMethodCall(buf, indent, superQualifier, isInstanceThis);
		buf.append(appendParamList(indent)).append(')');
		if (pushedCallChainGroup) buf.popNewlineGroup();
		return buf;
	}

	private boolean renderStaticOrDynamic(TextBuffer buf, int indent) {
		if (isBoxingCall() && canIgnoreBoxing && !forceBoxing) {
			ExprProcessor.getCastedExprent(lstParameters.get(0), descriptor.params[0], buf, indent,
			                               false, false, true, false);
			buf.addBytecodeMapping(bytecode);
			return true;
		}
		if (invocationTyp == CONSTANT_DYNAMIC) {
			buf.append('(').append(ExprProcessor.getCastTypeName(descriptor.ret)).append(')');
		}
		ClassNode node = (ClassNode) DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
		if (node == null || !classname.equals(node.classStruct.qualifiedName)) {
			buf.append(DecompilerContext.getImportCollector()
			                            .getShortNameInClassContext(ExprProcessor.buildJavaClassName(classname)));
		}
		return false;
	}

	private void renderMethodCall(TextBuffer buf, int indent, String superQualifier, boolean isInstanceThis) {
		switch (functype) {
			case TYP_GENERAL:
				if (buf.contentEquals(VarExprent.VAR_NAMELESS_ENCLOSURE)) buf.setLength(0);
				if (buf.length() > 0) {
					buf.append(".");
					appendParameters(buf, genericArgs);
				}
				buf.addBytecodeMapping(bytecode);
				if (invocationTyp == INVOKE_DYNAMIC || invocationTyp == CONSTANT_DYNAMIC) {
					DynamicInvocationHelper.renderDynamicInvocation(buf, name, invocationTyp,
					                                                bootstrapMethod, bootstrapArguments);
				} else {
					buf.append(name);
				}
				buf.append("(");
				break;
			case TYP_CLINIT:
				throw new RuntimeException("Explicit invocation of " + CodeConstants.CLINIT_NAME);
			case TYP_INIT:
				buf.addBytecodeMapping(bytecode);
				if (superQualifier != null) buf.append("super(");
				else if (isInstanceThis) buf.append("this(");
				else if (instance != null) buf.append(instance.toJava(indent)).append(".<init>(");
				else throw new RuntimeException("Unrecognized invocation of " + CodeConstants.INIT_NAME);
				break;
		}
	}

	public TextBuffer appendParamList(int indent) {
		return InvocationRenderer.renderParamList(this, indent, desc, genericsMap);
	}

	public boolean isBoxingCall() {
		return InvocationBoxingHelper.isBoxingCall(this);
	}

	public void markUsingBoxingResult() {
		canIgnoreBoxing = false;
	}

	@Override
	public void setIsQualifier() {
		isQualifier = true;
	}

	public boolean isUnboxingCall() {
		return InvocationBoxingHelper.isUnboxingCall(this);
	}

	public boolean shouldForceBoxing() { return forceBoxing; }
	public void setForceBoxing(boolean value) { this.forceBoxing = value; }
	public void forceUnboxing(boolean value) { this.forceUnboxing = value; }
	public boolean shouldForceUnboxing() { return forceUnboxing; }

	@Override
	public void replaceExprent(Exprent oldExpr, Exprent newExpr) {
		if (oldExpr == instance) instance = newExpr;
		for (int i = 0; i < lstParameters.size(); i++) {
			if (oldExpr == lstParameters.get(i)) lstParameters.set(i, newExpr);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof InvocationExprent)) return false;
		InvocationExprent it = (InvocationExprent) o;
		return InterpreterUtil.equalObjects(name, it.getName()) &&
		       InterpreterUtil.equalObjects(classname, it.getClassname()) &&
		       isStatic == it.isStatic() &&
		       InterpreterUtil.equalObjects(instance, it.getInstance()) &&
		       InterpreterUtil.equalObjects(descriptor, it.getDescriptor()) &&
		       functype == it.getFunctype() &&
		       InterpreterUtil.equalLists(lstParameters, it.getLstParameters());
	}

	// Getters and setters
	public List<Exprent> getLstParameters() { return lstParameters; }
	public void setLstParameters(List<Exprent> lstParameters) { this.lstParameters = lstParameters; }
	public MethodDescriptor getDescriptor() { return descriptor; }
	public void setDescriptor(MethodDescriptor descriptor) { this.descriptor = descriptor; }
	public String getClassname() { return classname; }
	public void setClassname(String classname) { this.classname = classname; }
	public int getFunctype() { return functype; }
	public void setFunctype(int functype) { this.functype = functype; }
	public Exprent getInstance() { return instance; }
	public void setInstance(Exprent instance) { this.instance = instance; }
	public boolean isStatic() { return isStatic; }
	public void setStatic(boolean isStatic) { this.isStatic = isStatic; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getStringDescriptor() { return stringDescriptor; }
	public void setStringDescriptor(String stringDescriptor) { this.stringDescriptor = stringDescriptor; }
	public int getInvocationTyp() { return invocationTyp; }
	public String getInvokeDynamicClassSuffix() { return invokeDynamicClassSuffix; }
	public LinkConstant getBootstrapMethod() { return bootstrapMethod; }
	public List<PooledConstant> getBootstrapArguments() { return bootstrapArguments; }
	public void setSyntheticNullCheck() { isSyntheticNullCheck = true; }
	public boolean isSyntheticNullCheck() { return isSyntheticNullCheck; }
	public List<VarType> getGenericArgs() { return genericArgs; }
	public Map<VarType, VarType> getGenericsMap() { return genericsMap; }
	public void setInvocationInstance() { isInvocationInstance = true; }

	@Override
	public void getBytecodeRange(BitSet values) {
		measureBytecode(values, lstParameters);
		measureBytecode(values, instance);
		measureBytecode(values);
	}

	@Override
	public boolean match(MatchNode matchNode, MatchEngine engine) {
		if (!super.match(matchNode, engine)) return false;
		for (Entry<MatchProperties, RuleValue> rule : matchNode.getRules().entrySet()) {
			RuleValue value = rule.getValue();
			MatchProperties key = rule.getKey();
			if (key == MatchProperties.EXPRENT_INVOCATION_PARAMETER) {
				if (value.isVariable() && (value.parameter >= lstParameters.size() ||
				    !engine.checkAndSetVariableValue(value.value.toString(), lstParameters.get(value.parameter)))) {
					return false;
				}
			} else if (key == MatchProperties.EXPRENT_INVOCATION_CLASS && !value.value.equals(classname)) {
				return false;
			} else if (key == MatchProperties.EXPRENT_INVOCATION_SIGNATURE && !value.value.equals(name + stringDescriptor)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public <T> T accept(ExprentVisitor<T> visitor) {
		return visitor.visitInvocation(this);
	}
}
