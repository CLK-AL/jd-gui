// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectNode;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.SSAConstructorSparseEx;
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchAllStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;

import java.util.*;

/**
 * Handles detection and analysis of finally blocks in bytecode.
 */
public final class FinallyDetector {
	private final MethodDescriptor methodDescriptor;
	private final VarProcessor varProcessor;

	public FinallyDetector(MethodDescriptor md, VarProcessor varProc) {
		this.methodDescriptor = md;
		this.varProcessor = varProc;
	}

	/**
	 * Gathers information about a finally block structure.
	 *
	 * @param cl    the class containing the method
	 * @param mt    the method being analyzed
	 * @param root  the root statement
	 * @param fstat the catch-all statement representing the finally
	 * @return a FinallyInfo record, or null if the finally is inconsistent
	 */
	public FinallyInfo getFinallyInformation(StructClass cl,
	                                         StructMethod mt,
	                                         RootStatement root,
	                                         CatchAllStatement fstat) {
		Map<BasicBlock, Boolean> mapLast = new HashMap<>();

		BasicBlockStatement firstBlockStatement = fstat.getHandler().getBasichead();
		BasicBlock firstBasicBlock = firstBlockStatement.getBlock();
		Instruction instrFirst = firstBasicBlock.getInstruction(0);

		int firstcode = 0;

		switch (instrFirst.opcode) {
			case CodeConstants.opc_pop:
				firstcode = 1;
				break;
			case CodeConstants.opc_astore:
				firstcode = 2;
		}

		ExprProcessor proc = new ExprProcessor(methodDescriptor, varProcessor);
		proc.processStatement(root, cl);

		SSAConstructorSparseEx ssa = new SSAConstructorSparseEx();
		ssa.splitVariables(root, mt);

		List<Exprent> lstExprents = firstBlockStatement.getExprents();

		VarVersionPair varpaar = new VarVersionPair((VarExprent) ((AssignmentExprent) lstExprents.get(firstcode == 2 ? 1 : 0)).getLeft());

		FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
		DirectGraph dgraph = flatthelper.buildDirectGraph(root);

		LinkedList<DirectNode> stack = new LinkedList<>();
		stack.add(dgraph.first);

		Set<DirectNode> setVisited = new HashSet<>();

		while (!stack.isEmpty()) {
			DirectNode node = stack.removeFirst();

			if (setVisited.contains(node)) {
				continue;
			}
			setVisited.add(node);

			BasicBlockStatement blockStatement = null;
			if (node.block != null) {
				blockStatement = node.block;
			} else if (node.preds.size() == 1) {
				blockStatement = node.preds.get(0).block;
			}

			boolean isTrueExit = true;

			if (firstcode != 1) {
				isTrueExit = false;

				for (int i = 0; i < node.exprents.size(); i++) {
					Exprent exprent = node.exprents.get(i);

					if (firstcode == 0) {
						List<Exprent> lst = exprent.getAllExprents();
						lst.add(exprent);

						boolean found = false;
						for (Exprent expr : lst) {
							if (expr.type == Exprent.EXPRENT_VAR && new VarVersionPair((VarExprent) expr).equals(varpaar)) {
								found = true;
								break;
							}
						}

						if (found) {
							found = false;
							if (exprent.type == Exprent.EXPRENT_EXIT) {
								ExitExprent exexpr = (ExitExprent) exprent;
								if (exexpr.getExitType() == ExitExprent.EXIT_THROW && exexpr.getValue().type == Exprent.EXPRENT_VAR) {
									found = true;
								}
							}

							if (!found) {
								return null;
							} else {
								isTrueExit = true;
							}
						}
					} else if (firstcode == 2) {
						// search for a load instruction
						if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
							AssignmentExprent assexpr = (AssignmentExprent) exprent;
							if (assexpr.getRight().type == Exprent.EXPRENT_VAR
							    && new VarVersionPair((VarExprent) assexpr.getRight()).equals(varpaar)) {

								Exprent next = null;
								if (i == node.exprents.size() - 1) {
									if (node.succs.size() == 1) {
										DirectNode nd = node.succs.get(0);
										if (!nd.exprents.isEmpty()) {
											next = nd.exprents.get(0);
										}
									}
								} else {
									next = node.exprents.get(i + 1);
								}

								boolean found = false;
								if (next != null && next.type == Exprent.EXPRENT_EXIT) {
									ExitExprent exexpr = (ExitExprent) next;
									if (exexpr.getExitType() == ExitExprent.EXIT_THROW
									    && exexpr.getValue().type == Exprent.EXPRENT_VAR
									    && assexpr.getLeft().equals(exexpr.getValue())) {
										found = true;
									}
								}

								if (!found) {
									return null;
								} else {
									isTrueExit = true;
								}
							}
						}
					}
				}
			}

			// find finally exits
			if (blockStatement != null && blockStatement.getBlock() != null) {
				Statement handler = fstat.getHandler();
				for (StatEdge edge : blockStatement.getSuccessorEdges(Statement.STATEDGE_DIRECT_ALL)) {
					if (edge.getType() != StatEdge.TYPE_REGULAR
					    && handler.containsStatement(blockStatement)
					    && !handler.containsStatement(edge.getDestination())) {
						Boolean existingFlag = mapLast.get(blockStatement.getBlock());
						// note: the dummy node is also processed!
						if (existingFlag == null || !existingFlag) {
							mapLast.put(blockStatement.getBlock(), isTrueExit);
							break;
						}
					}
				}
			}

			stack.addAll(node.succs);
		}

		// empty finally block?
		if (fstat.getHandler().type == Statement.TYPE_BASICBLOCK) {
			boolean isEmpty = false;
			boolean isFirstLast = mapLast.containsKey(firstBasicBlock);
			var seq = firstBasicBlock.getSeq();

			switch (firstcode) {
				case 0:
					isEmpty = isFirstLast && seq.length() == 1;
					break;
				case 1:
					isEmpty = seq.length() == 1;
					break;
				case 2:
					isEmpty = isFirstLast ? seq.length() == 3 : seq.length() == 1;
			}

			if (isEmpty) {
				firstcode = 3;
			}
		}

		return new FinallyInfo(firstcode, mapLast);
	}

	/**
	 * Holds information about a finally block structure.
	 */
	public static final class FinallyInfo {
		private final int firstCode;
		private final Map<BasicBlock, Boolean> mapLast;

		public FinallyInfo(int firstCode, Map<BasicBlock, Boolean> mapLast) {
			this.firstCode = firstCode;
			this.mapLast = mapLast;
		}

		public int getFirstCode() {
			return firstCode;
		}

		public Map<BasicBlock, Boolean> getMapLast() {
			return mapLast;
		}
	}
}
