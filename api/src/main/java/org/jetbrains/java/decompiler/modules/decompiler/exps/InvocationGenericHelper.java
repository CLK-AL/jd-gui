// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericType;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Helper class for generic type inference in invocations.
 */
public final class InvocationGenericHelper {

	private InvocationGenericHelper() {
		// Utility class
	}

	/**
	 * Gets the generic bounds for a method and its containing class.
	 */
	public static Map<VarType, List<VarType>> getGenericBounds(StructMethod desc, StructClass mthCls) {
		Map<VarType, List<VarType>> bounds = new HashMap<>();

		if (desc.getSignature() != null) {
			for (int x = 0; x < desc.getSignature().typeParameters.size(); x++) {
				bounds.putIfAbsent(
					GenericType.parse("T" + desc.getSignature().typeParameters.get(x) + ";"),
					desc.getSignature().typeParameterBounds.get(x)
				);
			}
		}

		if (mthCls.getSignature() != null) {
			for (int x = 0; x < mthCls.getSignature().fparameters.size(); x++) {
				bounds.putIfAbsent(
					GenericType.parse("T" + mthCls.getSignature().fparameters.get(x) + ";"),
					mthCls.getSignature().fbounds.get(x)
				);
			}
		}

		ClassNode cn = DecompilerContext.getClassProcessor()
		                                .getMapRootClasses()
		                                .get(mthCls.qualifiedName);
		cn = cn != null ? cn.parent : null;

		while (cn != null) {
			if (cn.classStruct.getSignature() != null) {
				for (int x = 0; x < cn.classStruct.getSignature().fparameters.size(); x++) {
					bounds.putIfAbsent(
						GenericType.parse("T" + cn.classStruct.getSignature().fparameters.get(x) + ";"),
						cn.classStruct.getSignature().fbounds.get(x)
					);
				}
			}
			cn = cn.parent;
		}

		return bounds;
	}

	/**
	 * Processes a generic type mapping, adding it to the generics map if valid.
	 */
	public static void processGenericMapping(VarType from,
	                                         VarType to,
	                                         Map<VarType, List<VarType>> named,
	                                         Map<VarType, List<VarType>> bounds,
	                                         Map<VarType, VarType> genericsMap) {
		if (VarType.VARTYPE_NULL.equals(to) ||
		    (to != null && to.type == CodeConstants.TYPE_GENVAR && !named.containsKey(to))) {
			return;
		}

		VarType current = genericsMap.get(from);
		if (!genericsMap.containsKey(from)) {
			putGenericMapping(from, to, named, bounds, genericsMap);
		} else if (to != null && current != null && !to.equals(current)) {
			if (named.containsKey(current)) {
				return;
			}

			if (current.type != CodeConstants.TYPE_GENVAR && to.type == CodeConstants.TYPE_GENVAR) {
				if (named.containsKey(to)) {
					VarType bound = named.get(to).get(0);
					if (!bound.equals(VarType.VARTYPE_OBJECT) &&
					    DecompilerContext.getStructContext().instanceOf(bound.value, current.value)) {
						return;
					}
				}
			}

			if (to.isGeneric() && current.isGeneric() &&
			    GenericType.isAssignable(to, current, named)) {
				putGenericMapping(from, to, named, bounds, genericsMap);
			}
		}
	}

	/**
	 * Puts a generic mapping if it's within bounds.
	 */
	public static void putGenericMapping(VarType from,
	                                     VarType to,
	                                     Map<VarType, List<VarType>> named,
	                                     Map<VarType, List<VarType>> bounds,
	                                     Map<VarType, VarType> genericsMap) {
		if (isMappingInBounds(from, to, named, bounds, genericsMap)) {
			genericsMap.put(from, to);
		}
	}

	/**
	 * Checks if a type mapping is within the declared bounds.
	 */
	public static boolean isMappingInBounds(VarType from,
	                                        VarType to,
	                                        Map<VarType, List<VarType>> named,
	                                        Map<VarType, List<VarType>> bounds,
	                                        Map<VarType, VarType> genericsMap) {
		if (!bounds.containsKey(from)) {
			return false;
		}

		if (to == null || (to.type == CodeConstants.TYPE_GENVAR && !named.containsKey(to))) {
			return true;
		}

		BiFunction<VarType, VarType, Boolean> verifier = (newTo, bound) -> {
			if (bound.type == CodeConstants.TYPE_GENVAR) {
				Function<VarType, VarType> map = e -> {
					VarType mapped = genericsMap.get(e);
					if (mapped == null) {
						mapped = named.containsKey(e) ? named.get(e).get(0) : null;
					}
					return mapped;
				};
				VarType mapped = map.apply(bound);

				if (mapped != null && !mapped.equals(bound)) {
					VarType last = bound;
					while (bound != null) {
						last = bound;
						bound = map.apply(bound);
					}
					bound = last;

					if (bound.type != CodeConstants.TYPE_GENVAR) {
						return DecompilerContext.getStructContext()
						                        .instanceOf(newTo.value, bound.value);
					}
				}

				return isMappingInBounds(bound, newTo, named, bounds, genericsMap);
			}

			if (newTo.type < CodeConstants.TYPE_OBJECT) {
				return bound.equals(VarType.VARTYPE_OBJECT) || bound.equals(newTo);
			}

			if (!DecompilerContext.getStructContext().instanceOf(newTo.value, bound.value)) {
				return false;
			}

			if (bound.isGeneric() && !((GenericType) bound).getArguments().isEmpty()) {
				GenericType genbound = (GenericType) bound;
				VarType _new = newTo;

				if (!newTo.value.equals(bound.value)) {
					_new = GenericType.getGenericSuperType(newTo, bound);
				}

				if (!_new.isGeneric() ||
				    ((GenericType) _new).getArguments().size() != genbound.getArguments().size()) {
					return false;
				}

				Map<VarType, VarType> toAdd = new HashMap<>();
				GenericType genNew = (GenericType) _new;
				for (int i = 0; i < genbound.getArguments().size(); ++i) {
					VarType boundArg = genbound.getArguments().get(i);
					VarType newArg = genNew.getArguments().get(i);

					if (boundArg == null) {
						continue;
					}

					if (!boundArg.equals(newArg)) {
						// T extends Comparable<T>
						if (from.equals(boundArg) && to.equals(newArg)) {
							continue;
						}

						// T extends Comparable<S>, S extends Object
						if (bounds.containsKey(boundArg) &&
						    isMappingInBounds(boundArg, newArg, named, bounds, genericsMap)) {
							toAdd.put(boundArg, newArg);
							continue;
						}
						return false;
					}
				}
				toAdd.forEach((k, v) -> processGenericMapping(k, v, named, bounds, genericsMap));
			}
			return true;
		};

		List<VarType> toVerify = (to.type == CodeConstants.TYPE_GENVAR)
		                         ? named.get(to)
		                         : Collections.singletonList(to);

		// We need to satisfy all the bounds for the type we are mapping to
		// The bounds can be satisfied by any of the bounds for the named type
		return bounds.get(from)
		             .stream()
		             .allMatch(bound -> toVerify.stream()
		                                        .anyMatch(v -> verifier.apply(v, bound)));
	}
}
