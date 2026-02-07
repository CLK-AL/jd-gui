// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.ClasspathHelper;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericMethodDescriptor;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Helper class for method resolution and matching in invocations.
 */
public final class InvocationMethodResolver {

	private static final BitSet EMPTY_BIT_SET = new BitSet(0);

	private InvocationMethodResolver() {
		// Utility class
	}

	/**
	 * Checks if the method being invoked is a varargs method.
	 */
	public static boolean isVarArgCall(String classname, String name, String stringDescriptor, MethodDescriptor descriptor) {
		StructClass cl = DecompilerContext.getStructContext().getClass(classname);
		if (cl != null) {
			StructMethod mt = cl.getMethod(InterpreterUtil.makeUniqueKey(name, stringDescriptor));
			if (mt != null) {
				return mt.hasModifier(CodeConstants.ACC_VARARGS);
			}
		} else {
			// try to check the class on the classpath
			Method mtd = ClasspathHelper.findMethod(classname, name, descriptor);
			return mtd != null && mtd.isVarArgs();
		}
		return false;
	}

	/**
	 * Gets all methods that match the invocation descriptor.
	 */
	public static List<StructMethod> getMatchedDescriptors(String classname,
	                                                       String name,
	                                                       String stringDescriptor,
	                                                       MethodDescriptor descriptor) {
		List<StructMethod> matches = new ArrayList<>();
		ClassNode currCls = ((ClassNode) DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE));
		StructClass cl = DecompilerContext.getStructContext().getClass(classname);
		if (cl == null) {
			return matches;
		}

		Set<String> visited = new HashSet<>();
		Queue<StructClass> que = new ArrayDeque<>();
		que.add(cl);

		while (!que.isEmpty()) {
			StructClass cls = que.poll();
			if (cls == null) {
				continue;
			}

			for (StructMethod mt : cls.getMethods()) {
				if (name.equals(mt.getName())) {
					MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());
					if (matches(md.params, descriptor.params) &&
					    (currCls == null || canAccess(currCls.classStruct, mt))) {
						matches.add(mt);
					}
				}
			}

			if (cls == cl && !matches.isEmpty()) {
				return matches;
			}

			visited.add(cls.qualifiedName);
			if (cls.superClass != null && !visited.contains(cls.superClass.value)) {
				StructClass tmp = DecompilerContext.getStructContext()
				                                   .getClass((String) cls.superClass.value);
				if (tmp != null) {
					que.add(tmp);
				}
			}

			for (String intf : cls.getInterfaceNames()) {
				if (!visited.contains(intf)) {
					StructClass tmp = DecompilerContext.getStructContext().getClass(intf);
					if (tmp != null) {
						que.add(tmp);
					}
				}
			}
		}

		return matches;
	}

	/**
	 * Checks if two parameter arrays match by type family.
	 */
	public static boolean matches(VarType[] left, VarType[] right) {
		if (left.length == right.length) {
			for (int i = 0; i < left.length; i++) {
				if (left[i].typeFamily != right[i].typeFamily) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Checks if a method can be accessed from the given class.
	 */
	public static boolean canAccess(StructClass currCls, StructMethod mt) {
		if (mt.hasModifier(CodeConstants.ACC_PUBLIC)) {
			return true;
		} else if (mt.hasModifier(CodeConstants.ACC_PRIVATE)) {
			return mt.getClassQualifiedName().equals(currCls.qualifiedName);
		} else if (mt.hasModifier(CodeConstants.ACC_PROTECTED)) {
			boolean samePackage = isInSamePackage(currCls.qualifiedName, mt.getClassQualifiedName());
			return samePackage || DecompilerContext.getStructContext()
			                                       .instanceOf(currCls.qualifiedName, mt.getClassQualifiedName());
		} else {
			return isInSamePackage(currCls.qualifiedName, mt.getClassQualifiedName());
		}
	}

	/**
	 * Checks if two classes are in the same package.
	 */
	public static boolean isInSamePackage(String class1, String class2) {
		int pos1 = class1.lastIndexOf('/');
		int pos2 = class2.lastIndexOf('/');
		if (pos1 != pos2) {
			return false;
		}

		if (pos1 == -1) {
			return true;
		}

		String pkg1 = class1.substring(0, pos1);
		String pkg2 = class2.substring(0, pos2);
		return pkg1.equals(pkg2);
	}

	/**
	 * Gets a bit set indicating which parameters are ambiguous.
	 */
	public static BitSet getAmbiguousParameters(String classname,
	                                            String name,
	                                            String stringDescriptor,
	                                            MethodDescriptor descriptor,
	                                            List<Exprent> lstParameters,
	                                            List<StructMethod> matches) {
		StructClass cl = DecompilerContext.getStructContext().getClass(classname);
		if (cl == null || matches.size() == 1) {
			return EMPTY_BIT_SET;
		}

		BitSet missed = new BitSet(lstParameters.size());

		// treat signature polymorphic methods as always ambiguous
		if (CodeConstants.areParametersPolymorphic(classname, name)) {
			missed.set(0, lstParameters.size());
			return missed;
		}

		// check if a call is unambiguous
		StructMethod mt = cl.getMethod(InterpreterUtil.makeUniqueKey(name, stringDescriptor));
		if (mt != null) {
			MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());
			if (md.params.length == lstParameters.size()) {
				boolean exact = true;
				for (int i = 0; i < md.params.length; i++) {
					Exprent exp = lstParameters.get(i);
					if ((!(md.params[i].equals(exp.getExprType()) ||
					       md.params[i].isSuperset(exp.getExprType()))) ||
					    (exp.type == Exprent.EXPRENT_NEW &&
					     ((NewExprent) exp).isLambda() &&
					     !((NewExprent) exp).isMethodReference())) {
						exact = false;
						missed.set(i);
					}
				}
				if (exact) {
					return EMPTY_BIT_SET;
				}
			}
		}

		List<StructMethod> mtds = new ArrayList<>();
		for (StructMethod mtt : matches) {
			boolean failed = false;
			MethodDescriptor md = MethodDescriptor.parseDescriptor(mtt.getDescriptor());
			for (int i = 0; i < lstParameters.size(); i++) {
				Exprent exp = lstParameters.get(i);
				VarType ptype = exp.getExprType();
				if (!missed.get(i)) {
					if (!md.params[i].equals(ptype)) {
						failed = true;
						break;
					}
				} else {
					if (exp.type == Exprent.EXPRENT_NEW) {
						NewExprent newExp = (NewExprent) exp;
						if (newExp.isLambda() && !newExp.isMethodReference() &&
						    !DecompilerContext.getStructContext()
						                      .instanceOf(md.params[i].value, exp.getExprType().value)) {
							StructClass pcls = DecompilerContext.getStructContext()
							                                    .getClass(md.params[i].value);
							if (pcls != null && pcls.getMethod(newExp.getLambdaMethodKey()) == null) {
								failed = true;
								break;
							}
							continue;
						}
					}
					if (md.params[i].type == CodeConstants.TYPE_OBJECT) {
						if (ptype.type != CodeConstants.TYPE_NULL) {
							if (!DecompilerContext.getStructContext()
							                      .instanceOf(ptype.value, md.params[i].value)) {
								failed = true;
								break;
							}
						}
					}
				}
			}
			if (!failed) {
				mtds.add(mtt);
			}
		}

		// mark parameters
		BitSet ambiguous = new BitSet(descriptor.params.length);
		for (int i = 0; i < descriptor.params.length; i++) {
			VarType paramType = descriptor.params[i];
			for (StructMethod mtt : mtds) {
				GenericMethodDescriptor gen = mtt.getSignature();
				if (gen != null && gen.parameterTypes.size() > i &&
				    gen.parameterTypes.get(i).isGeneric()) {
					Exprent exp = lstParameters.get(i);
					if (exp.type != Exprent.EXPRENT_NEW ||
					    !((NewExprent) exp).isLambda() ||
					    ((NewExprent) exp).isMethodReference()) {
						break;
					}
				}

				MethodDescriptor md = MethodDescriptor.parseDescriptor(mtt.getDescriptor());
				if (!paramType.equals(md.params[i])) {
					ambiguous.set(i);
					break;
				}
			}
		}
		return ambiguous;
	}
}
