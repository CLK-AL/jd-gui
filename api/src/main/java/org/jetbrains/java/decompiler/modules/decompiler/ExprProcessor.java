// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectNode;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper.FinallyPathWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.ListStack;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.*;

/**
 * Main expression processor for bytecode-to-expression conversion.
 * Coordinates the decompilation process by delegating to specialized helper classes:
 * <ul>
 *   <li>{@link InstructionTables} - Static lookup tables for bytecode processing</li>
 *   <li>{@link TypeConverter} - Type name conversion utilities</li>
 *   <li>{@link ExpressionBuilder} - Expression building and casting utilities</li>
 *   <li>{@link InstructionProcessor} - Bytecode instruction processing</li>
 * </ul>
 */
public class ExprProcessor {

	// Type string constants - delegated to TypeConverter for backward compatibility
	public static final String UNDEFINED_TYPE_STRING = TypeConverter.UNDEFINED_TYPE_STRING;
	public static final String UNKNOWN_TYPE_STRING   = TypeConverter.UNKNOWN_TYPE_STRING;
	public static final String NULL_TYPE_STRING      = TypeConverter.NULL_TYPE_STRING;

	private final MethodDescriptor methodDescriptor;
	private final VarProcessor varProcessor;
	private final InstructionProcessor instructionProcessor;

	public ExprProcessor(MethodDescriptor md, VarProcessor varProc) {
		this.methodDescriptor = md;
		this.varProcessor = varProc;
		this.instructionProcessor = new InstructionProcessor(md, varProc);
	}

	// ========== Delegation methods for backward compatibility ==========

	/**
	 * Gets the type name for a VarType.
	 * @see TypeConverter#getTypeName(VarType)
	 */
	public static String getTypeName(VarType type) {
		return TypeConverter.getTypeName(type);
	}

	/**
	 * Gets the type name for a VarType with optional short name.
	 * @see TypeConverter#getTypeName(VarType, boolean)
	 */
	public static String getTypeName(VarType type, boolean getShort) {
		return TypeConverter.getTypeName(type, getShort);
	}

	/**
	 * Gets the cast type name with array dimensions.
	 * @see TypeConverter#getCastTypeName(VarType)
	 */
	public static String getCastTypeName(VarType type) {
		return TypeConverter.getCastTypeName(type);
	}

	/**
	 * Gets the cast type name with array dimensions and optional short name.
	 * @see TypeConverter#getCastTypeName(VarType, boolean)
	 */
	public static String getCastTypeName(VarType type, boolean getShort) {
		return TypeConverter.getCastTypeName(type, getShort);
	}

	/**
	 * Builds a Java class name from an internal name.
	 * @see TypeConverter#buildJavaClassName(String)
	 */
	public static String buildJavaClassName(String name) {
		return TypeConverter.buildJavaClassName(name);
	}

	/**
	 * Creates expression data for a variable expression.
	 * @see ExpressionBuilder#getExpressionData(VarExprent)
	 */
	public static PrimitiveExprsList getExpressionData(VarExprent var) {
		return ExpressionBuilder.getExpressionData(var);
	}

	/**
	 * Checks if an expression should end with a semicolon.
	 * @see ExpressionBuilder#endsWithSemicolon(Exprent)
	 */
	public static boolean endsWithSemicolon(Exprent expr) {
		return ExpressionBuilder.endsWithSemicolon(expr);
	}

	/**
	 * Gets a casted expression.
	 * @see ExpressionBuilder#getCastedExprent(Exprent, VarType, TextBuffer, int, boolean)
	 */
	public static boolean getCastedExprent(Exprent exprent,
	                                       VarType leftType,
	                                       TextBuffer buffer,
	                                       int indent,
	                                       boolean castNull) {
		return ExpressionBuilder.getCastedExprent(exprent, leftType, buffer, indent, castNull);
	}

	/**
	 * Gets a casted expression with full control over casting behavior.
	 * @see ExpressionBuilder#getCastedExprent(Exprent, VarType, TextBuffer, int, boolean, boolean, boolean, boolean)
	 */
	public static boolean getCastedExprent(Exprent exprent,
	                                       VarType leftType,
	                                       TextBuffer buffer,
	                                       int indent,
	                                       boolean castNull,
	                                       boolean castAlways,
	                                       boolean castNarrowing,
	                                       boolean unbox) {
		return ExpressionBuilder.getCastedExprent(exprent, leftType, buffer, indent,
		                                          castNull, castAlways, castNarrowing, unbox);
	}

	/**
	 * Gets a default array value expression.
	 * @see ExpressionBuilder#getDefaultArrayValue(VarType)
	 */
	public static ConstExprent getDefaultArrayValue(VarType arrType) {
		return ExpressionBuilder.getDefaultArrayValue(arrType);
	}

	/**
	 * Converts a list of expressions to Java source code.
	 * @see ExpressionBuilder#listToJava(List, int)
	 */
	public static TextBuffer listToJava(List<? extends Exprent> lst, int indent) {
		return ExpressionBuilder.listToJava(lst, indent);
	}

	/**
	 * Wraps a statement with jump handling.
	 * @see ExpressionBuilder#jmpWrapper(Statement, int, boolean)
	 */
	public static TextBuffer jmpWrapper(Statement stat, int indent, boolean semicolon) {
		return ExpressionBuilder.jmpWrapper(stat, indent, semicolon);
	}

	/**
	 * Copies exprent entries in a list.
	 */
	public static void copyEntries(List<Exprent> stack) {
		for (int i = 0; i < stack.size(); i++) {
			stack.set(i, stack.get(i).copy());
		}
	}

	// ========== Core processing methods ==========

	/**
	 * Processes a root statement and converts bytecode to expression trees.
	 *
	 * @param root the root statement
	 * @param cl   the class structure
	 */
	public void processStatement(RootStatement root, StructClass cl) {
		FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
		DirectGraph dgraph = flatthelper.buildDirectGraph(root);

		// collect finally entry points
		Set<String> setFinallyShortRangeEntryPoints = new HashSet<>();
		for (List<FinallyPathWrapper> lst : dgraph.mapShortRangeFinallyPaths.values()) {
			for (FinallyPathWrapper finwrap : lst) {
				setFinallyShortRangeEntryPoints.add(finwrap.entry);
			}
		}

		Set<String> setFinallyLongRangeEntryPaths = new HashSet<>();
		for (List<FinallyPathWrapper> lst : dgraph.mapLongRangeFinallyPaths.values()) {
			for (FinallyPathWrapper finwrap : lst) {
				setFinallyLongRangeEntryPaths.add(finwrap.source + "##" + finwrap.entry);
			}
		}

		Map<String, VarExprent> mapCatch = new HashMap<>();
		collectCatchVars(root, flatthelper, mapCatch);

		Map<DirectNode, Map<String, PrimitiveExprsList>> mapData = new HashMap<>();

		LinkedList<DirectNode> stack = new LinkedList<>();
		LinkedList<LinkedList<String>> stackEntryPoint = new LinkedList<>();

		stack.add(dgraph.first);
		stackEntryPoint.add(new LinkedList<>());

		Map<String, PrimitiveExprsList> map = new HashMap<>();
		map.put(null, new PrimitiveExprsList());
		mapData.put(dgraph.first, map);

		while (!stack.isEmpty()) {
			DirectNode node = stack.removeFirst();
			LinkedList<String> entrypoints = stackEntryPoint.removeFirst();

			PrimitiveExprsList data;
			if (mapCatch.containsKey(node.id)) {
				data = ExpressionBuilder.getExpressionData(mapCatch.get(node.id));
			} else {
				data = mapData.get(node).get(buildEntryPointKey(entrypoints));
			}

			BasicBlockStatement block = node.block;
			if (block != null) {
				processBlock(block, data, cl);
				block.setExprents(data.getLstExprents());
			}

			String currentEntrypoint = entrypoints.isEmpty() ? null : entrypoints.getLast();

			for (DirectNode nd : node.succs) {
				boolean isSuccessor = true;
				if (currentEntrypoint != null && dgraph.mapLongRangeFinallyPaths.containsKey(node.id)) {
					isSuccessor = false;
					for (FinallyPathWrapper finwraplong : dgraph.mapLongRangeFinallyPaths.get(node.id)) {
						if (finwraplong.source.equals(currentEntrypoint) && finwraplong.destination.equals(nd.id)) {
							isSuccessor = true;
							break;
						}
					}
				}

				if (isSuccessor) {
					Map<String, PrimitiveExprsList> mapSucc = mapData.computeIfAbsent(nd, k -> new HashMap<>());
					LinkedList<String> ndentrypoints = new LinkedList<>(entrypoints);

					if (setFinallyLongRangeEntryPaths.contains(node.id + "##" + nd.id)) {
						ndentrypoints.addLast(node.id);
					} else if (!setFinallyShortRangeEntryPoints.contains(nd.id)
					           && dgraph.mapLongRangeFinallyPaths.containsKey(node.id)) {
						ndentrypoints.removeLast(); // currentEntrypoint should not be null at this point
					}

					// handling of entry point loops
					int succ_entry_index = ndentrypoints.indexOf(nd.id);
					if (succ_entry_index >= 0) {
						// we are in a loop (e.g. continue in a finally block), drop all entry points
						// in the list beginning with succ_entry_index
						for (int elements_to_remove = ndentrypoints.size() - succ_entry_index;
						     elements_to_remove > 0;
						     elements_to_remove--) {
							ndentrypoints.removeLast();
						}
					}

					String ndentrykey = buildEntryPointKey(ndentrypoints);
					if (!mapSucc.containsKey(ndentrykey)) {
						mapSucc.put(ndentrykey, copyVarExprents(data.copyStack()));
						stack.add(nd);
						stackEntryPoint.add(ndentrypoints);
					}
				}
			}
		}

		initStatementExprents(root);
	}

	/**
	 * Processes a basic block.
	 *
	 * @param stat the basic block statement
	 * @param data the primitive expression list
	 * @param cl   the class structure
	 */
	public void processBlock(BasicBlockStatement stat, PrimitiveExprsList data, StructClass cl) {
		instructionProcessor.processBlock(stat, data, cl);
	}

	// ========== Private helper methods ==========

	/**
	 * Builds a key for entry points.
	 */
	private static String buildEntryPointKey(LinkedList<String> entrypoints) {
		if (entrypoints.isEmpty()) {
			return null;
		} else {
			StringBuilder buffer = new StringBuilder();
			for (String point : entrypoints) {
				buffer.append(point);
				buffer.append(":");
			}
			return buffer.toString();
		}
	}

	/**
	 * Copies variable exprents for stack preservation.
	 */
	private static PrimitiveExprsList copyVarExprents(PrimitiveExprsList data) {
		ListStack<Exprent> stack = data.getStack();
		copyEntries(stack);
		return data;
	}

	/**
	 * Collects catch variable mappings for exception handling.
	 */
	private static void collectCatchVars(Statement stat,
	                                     FlattenStatementsHelper flatthelper,
	                                     Map<String, VarExprent> map) {

		List<VarExprent> lst = null;

		if (stat.type == Statement.TYPE_CATCHALL) {
			CatchAllStatement catchall = (CatchAllStatement) stat;
			if (!catchall.isFinally()) {
				lst = catchall.getVars();
			}
		} else if (stat.type == Statement.TYPE_TRYCATCH) {
			lst = ((CatchStatement) stat).getVars();
		}

		if (lst != null) {
			for (int i = 1; i < stat.getStats().size(); i++) {
				map.put(flatthelper.getMapDestinationNodes().get(stat.getStats().get(i).id)[0],
				        lst.get(i - 1));
			}
		}

		for (Statement st : stat.getStats()) {
			collectCatchVars(st, flatthelper, map);
		}
	}

	/**
	 * Initializes exprents for all statements recursively.
	 */
	private static void initStatementExprents(Statement stat) {
		stat.initExprents();

		for (Statement st : stat.getStats()) {
			initStatementExprents(st);
		}
	}
}
