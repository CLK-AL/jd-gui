// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.IfExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.SequenceStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;

import java.util.*;

public final class IfHelper {
	public static boolean mergeAllIfs(RootStatement root) {
		boolean res = mergeAllIfsRec(root,
		                             new HashSet<>());
		if (res) {
			SequenceHelper.condenseSequences(root);
		}
		return res;
	}

	private static boolean mergeAllIfsRec(Statement stat,
	                                      Set<? super Integer> setReorderedIfs) {
		boolean res = false;

		if (stat.getExprents() == null) {
			while (true) {
				boolean changed = false;

				for (Statement st : stat.getStats()) {
					res |= mergeAllIfsRec(st,
					                      setReorderedIfs);

					// collapse composed if's
					if (mergeIfs(st,
					             setReorderedIfs)) {
						changed = true;
						break;
					}
				}

				res |= changed;

				if (!changed) {
					break;
				}
			}
		}

		return res;
	}

	public static boolean mergeIfs(Statement statement,
	                               Set<? super Integer> setReorderedIfs) {
		if (statement.type != Statement.TYPE_IF && statement.type != Statement.TYPE_SEQUENCE) {
			return false;
		}

		boolean res = false;

		while (true) {
			boolean updated = false;

			List<Statement> lst = new ArrayList<>();
			if (statement.type == Statement.TYPE_IF) {
				lst.add(statement);
			} else {
				lst.addAll(statement.getStats());
			}

			boolean stsingle = (lst.size() == 1);

			for (Statement stat : lst) {
				if (stat.type == Statement.TYPE_IF) {
					IfNode rtnode = buildGraph((IfStatement) stat,
					                           stsingle);

					if (rtnode == null) {
						continue;
					}

					if (collapseIfIf(rtnode)) {
						updated = true;
						break;
					}

					if (!setReorderedIfs.contains(stat.id)) {
						if (collapseIfElse(rtnode)) {
							updated = true;
							break;
						}

						if (collapseElse(rtnode)) {
							updated = true;
							break;
						}
					}

					if (reorderIf((IfStatement) stat)) {
						updated = true;
						setReorderedIfs.add(stat.id);
						break;
					}
				}
			}

			if (!updated) {
				break;
			}

			res = true;
		}

		return res;
	}

	private static boolean collapseIfIf(IfNode rtnode) {
		if (rtnode.edgetypes.get(0) == 0) {
			IfNode ifbranch = rtnode.succs.get(0);
			if (ifbranch.succs.size() == 2 && rtnode.succs.size() >= 2) {

				// if-if branch
				if (ifbranch.succs.get(1).value == rtnode.succs.get(1).value) {

					IfStatement ifparent = (IfStatement) rtnode.value;
					IfStatement ifchild  = (IfStatement) ifbranch.value;
					Statement   ifinner  = ifbranch.succs.get(0).value;

					if (ifchild.getFirst()
					           .getExprents()
					           .isEmpty()) {

						ifparent.getFirst()
						        .removeSuccessor(ifparent.getIfEdge());
						ifchild.removeSuccessor(ifchild.getAllSuccessorEdges()
						                               .get(0));
						ifparent.getStats()
						        .removeWithKey(ifchild.id);

						if (ifbranch.edgetypes.get(0) == 1) { // target null

							ifparent.setIfstat(null);

							StatEdge ifedge = ifchild.getIfEdge();

							ifchild.getFirst()
							       .removeSuccessor(ifedge);
							ifedge.setSource(ifparent.getFirst());

							if (ifedge.closure == ifchild) {
								ifedge.closure = null;
							}
							ifparent.getFirst()
							        .addSuccessor(ifedge);

							ifparent.setIfEdge(ifedge);
						} else {
							ifchild.getFirst()
							       .removeSuccessor(ifchild.getIfEdge());

							StatEdge ifedge = new StatEdge(StatEdge.TYPE_REGULAR,
							                               ifparent.getFirst(),
							                               ifinner);
							ifparent.getFirst()
							        .addSuccessor(ifedge);
							ifparent.setIfEdge(ifedge);
							ifparent.setIfstat(ifinner);

							ifparent.getStats()
							        .addWithKey(ifinner,
							                    ifinner.id);
							ifinner.setParent(ifparent);

							if (!ifinner.getAllSuccessorEdges()
							            .isEmpty()) {
								StatEdge edge = ifinner.getAllSuccessorEdges()
								                       .get(0);
								if (edge.closure == ifchild) {
									edge.closure = null;
								}
							}
						}

						// merge if conditions
						IfExprent statexpr = ifparent.getHeadexprent();

						List<Exprent> lstOperands = new ArrayList<>();
						lstOperands.add(statexpr.getCondition());
						lstOperands.add(ifchild.getHeadexprent()
						                       .getCondition());

						statexpr.setCondition(new FunctionExprent(FunctionExprent.FUNCTION_CADD,
						                                          lstOperands,
						                                          null));
						statexpr.addBytecodeOffsets(ifchild.getHeadexprent().bytecode);

						return true;
					}
				}
			}
		}

		return false;
	}

	private static boolean collapseIfElse(IfNode rtnode) {
		if (rtnode.edgetypes.get(0) == 0) {
			IfNode ifbranch = rtnode.succs.get(0);
			if (ifbranch.succs.size() == 2 && rtnode.succs.size() >= 2) {
				// if-else branch
				if (ifbranch.succs.get(0).value == rtnode.succs.get(1).value) {

					IfStatement ifparent = (IfStatement) rtnode.value;
					IfStatement ifchild  = (IfStatement) ifbranch.value;

					if (ifchild.getFirst()
					           .getExprents()
					           .isEmpty()) {

						ifparent.getFirst()
						        .removeSuccessor(ifparent.getIfEdge());
						ifchild.getFirst()
						       .removeSuccessor(ifchild.getIfEdge());
						ifparent.getStats()
						        .removeWithKey(ifchild.id);

						if (ifbranch.edgetypes.get(1) == 1 && ifbranch.edgetypes.get(0) == 1) { // target null

							ifparent.setIfstat(null);

							StatEdge ifedge = ifchild.getAllSuccessorEdges()
							                         .get(0);

							ifchild.removeSuccessor(ifedge);
							ifedge.setSource(ifparent.getFirst());
							ifparent.getFirst()
							        .addSuccessor(ifedge);

							ifparent.setIfEdge(ifedge);
						} else {
							throw new IllegalStateException("inconsistent if structure in statement " + ifbranch.value + "!");
						}

						// merge if conditions
						IfExprent statexpr = ifparent.getHeadexprent();

						List<Exprent> lstOperands = new ArrayList<>();
						lstOperands.add(statexpr.getCondition());
						lstOperands.add(new FunctionExprent(FunctionExprent.FUNCTION_BOOL_NOT,
						                                    ifchild.getHeadexprent()
						                                           .getCondition(),
						                                    null));
						statexpr.setCondition(new FunctionExprent(FunctionExprent.FUNCTION_CADD,
						                                          lstOperands,
						                                          null));
						statexpr.addBytecodeOffsets(ifchild.getHeadexprent().bytecode);

						return true;
					}
				}
			}
		}

		return false;
	}

	private static boolean collapseElse(IfNode rtnode) {
		if (rtnode.edgetypes.size() >= 2 && rtnode.edgetypes.get(1) == 0) {
			IfNode elsebranch = rtnode.succs.get(1);
			if (elsebranch.succs.size() == 2) {

				// else-if or else-else branch
				int path = elsebranch.succs.get(1).value == rtnode.succs.get(0).value
				           ? 2
				           : (elsebranch.succs.get(0).value == rtnode.succs.get(0).value
				              ? 1
				              : 0);

				if (path > 0) {

					IfStatement firstif  = (IfStatement) rtnode.value;
					IfStatement secondif = (IfStatement) elsebranch.value;
					Statement   parent   = firstif.getParent();

					if (secondif.getFirst()
					            .getExprents()
					            .isEmpty()) {

						firstif.getFirst()
						       .removeSuccessor(firstif.getIfEdge());

						// remove first if
						firstif.removeAllSuccessors(secondif);

						for (StatEdge edge : firstif.getAllPredecessorEdges()) {
							if (!firstif.containsStatementStrict(edge.getSource())) {
								firstif.removePredecessor(edge);
								edge.getSource()
								    .changeEdgeNode(Statement.DIRECTION_FORWARD,
								                    edge,
								                    secondif);
								secondif.addPredecessor(edge);
							}
						}

						parent.getStats()
						      .removeWithKey(firstif.id);
						if (parent.getFirst() == firstif) {
							parent.setFirst(secondif);
						}

						// merge if conditions
						IfExprent statexpr = secondif.getHeadexprent();

						List<Exprent> lstOperands = new ArrayList<>();
						lstOperands.add(firstif.getHeadexprent()
						                       .getCondition());

						if (path == 2) {
							lstOperands.set(0,
							                new FunctionExprent(FunctionExprent.FUNCTION_BOOL_NOT,
							                                    lstOperands.get(0),
							                                    null));
						}

						lstOperands.add(statexpr.getCondition());

						statexpr.setCondition(new FunctionExprent(path == 1
						                                          ? FunctionExprent.FUNCTION_COR
						                                          : FunctionExprent.FUNCTION_CADD,
						                                          lstOperands,
						                                          null));

						if (secondif.getFirst()
						            .getExprents()
						            .isEmpty() && !firstif.getFirst()
						                                  .getExprents()
						                                  .isEmpty()) {

							secondif.replaceStatement(secondif.getFirst(),
							                          firstif.getFirst());
						}

						return true;
					}
				}
			} else if (elsebranch.succs.size() == 1) {
				if (elsebranch.succs.get(0).value == rtnode.succs.get(0).value) {
					IfStatement firstif = (IfStatement) rtnode.value;
					Statement   second  = elsebranch.value;

					firstif.removeAllSuccessors(second);

					for (StatEdge edge : second.getAllSuccessorEdges()) {
						second.removeSuccessor(edge);
						edge.setSource(firstif);
						firstif.addSuccessor(edge);
					}

					StatEdge ifedge = firstif.getIfEdge();
					firstif.getFirst()
					       .removeSuccessor(ifedge);

					second.addSuccessor(new StatEdge(ifedge.getType(),
					                                 second,
					                                 ifedge.getDestination(),
					                                 ifedge.closure));

					StatEdge newifedge = new StatEdge(StatEdge.TYPE_REGULAR,
					                                  firstif.getFirst(),
					                                  second);
					firstif.getFirst()
					       .addSuccessor(newifedge);
					firstif.setIfstat(second);

					firstif.getStats()
					       .addWithKey(second,
					                   second.id);
					second.setParent(firstif);

					firstif.getParent()
					       .getStats()
					       .removeWithKey(second.id);

					// negate the if condition
					IfExprent statexpr = firstif.getHeadexprent();
					statexpr.setCondition(new FunctionExprent(FunctionExprent.FUNCTION_BOOL_NOT,
					                                          statexpr.getCondition(),
					                                          null));

					return true;
				}
			}
		}

		return false;
	}

	private static IfNode buildGraph(IfStatement stat,
	                                 boolean stsingle) {
		if (stat.iftype == IfStatement.IFTYPE_IFELSE) {
			return null;
		}

		IfNode res = new IfNode(stat);

		// if branch
		Statement ifchild = stat.getIfstat();
		if (ifchild == null) {
			StatEdge edge = stat.getIfEdge();
			res.addChild(new IfNode(edge.getDestination()),
			             1);
		} else {
			IfNode ifnode = new IfNode(ifchild);
			res.addChild(ifnode,
			             0);
			if (ifchild.type == Statement.TYPE_IF && ((IfStatement) ifchild).iftype == IfStatement.IFTYPE_IF) {
				IfStatement stat2    = (IfStatement) ifchild;
				Statement   ifchild2 = stat2.getIfstat();
				if (ifchild2 == null) {
					StatEdge edge = stat2.getIfEdge();
					ifnode.addChild(new IfNode(edge.getDestination()),
					                1);
				} else {
					ifnode.addChild(new IfNode(ifchild2),
					                0);
				}
			}

			if (!ifchild.getAllSuccessorEdges()
			            .isEmpty()) {
				ifnode.addChild(new IfNode(ifchild.getAllSuccessorEdges()
				                                  .get(0)
				                                  .getDestination()),
				                1);
			}
		}

		// else branch
		List<StatEdge> successorEdges = stat.getAllSuccessorEdges();
		if (successorEdges.isEmpty()) {
			return res;
		}
		StatEdge  edge      = successorEdges.get(0);
		Statement elsechild = edge.getDestination();
		IfNode    elsenode  = new IfNode(elsechild);

		if (stsingle || edge.getType() != StatEdge.TYPE_REGULAR) {
			res.addChild(elsenode,
			             1);
		} else {
			res.addChild(elsenode,
			             0);
			if (elsechild.type == Statement.TYPE_IF && ((IfStatement) elsechild).iftype == IfStatement.IFTYPE_IF) {
				IfStatement stat2    = (IfStatement) elsechild;
				Statement   ifchild2 = stat2.getIfstat();
				if (ifchild2 == null) {
					elsenode.addChild(new IfNode(stat2.getIfEdge()
					                                  .getDestination()),
					                  1);
				} else {
					elsenode.addChild(new IfNode(ifchild2),
					                  0);
				}
			}

			if (!elsechild.getAllSuccessorEdges()
			              .isEmpty()) {
				elsenode.addChild(new IfNode(elsechild.getAllSuccessorEdges()
				                                      .get(0)
				                                      .getDestination()),
				                  1);
			}
		}

		return res;
	}

	// ===========================================================================================
	// If Statement Reordering
	// ===========================================================================================
	//
	// This section handles reordering of if statements to improve decompiled code structure.
	// The goal is to transform patterns like:
	//   if (cond) { ... }
	//   stmt1; stmt2; ...
	// Into proper if-else structures when the control flow allows it.
	//
	// IMPORTANT: Finally exits require special handling because they represent control flow
	// that exits through a finally block, which should be treated as a direct path.
	// ===========================================================================================

	/**
	 * Context class to hold all state information for the reorderIf operation.
	 * This encapsulates the various flags and computed values needed during reordering.
	 */
	private static class ReorderContext {
		final IfStatement ifstat;
		final Statement parent;
		final Statement from;
		final Statement next;
		final Statement last;

		// Flags indicating whether if/else branches exist
		boolean noIfStat;
		boolean noElseStat;

		// Flags indicating direct control flow paths
		boolean ifDirect;
		boolean elseDirect;

		// Flags indicating indirect paths through the statement graph
		boolean ifDirectPath;
		boolean elseDirectPath;

		ReorderContext(IfStatement ifstat) {
			this.ifstat = ifstat;
			this.parent = ifstat.getParent();
			this.from = (parent.type == Statement.TYPE_SEQUENCE) ? parent : ifstat;
			this.next = getNextStatement(from);
			this.last = (parent.type == Statement.TYPE_SEQUENCE)
			            ? parent.getStats().getLast()
			            : ifstat;
			this.noElseStat = (last == ifstat);
		}

		/**
		 * Checks if both branches can form a complete if-then-else structure.
		 */
		boolean canFormIfElse() {
			return (ifDirect || ifDirectPath)
			       && (elseDirect || elseDirectPath)
			       && !noIfStat
			       && !noElseStat;
		}

		/**
		 * Checks if the structure should be reordered as if-then (with negated condition).
		 */
		boolean shouldReorderAsIfThen() {
			return ifDirect
			       && (!elseDirect || (noIfStat && !noElseStat))
			       && !ifstat.getAllSuccessorEdges().isEmpty();
		}
	}

	/**
	 * Reorders an if statement to improve the decompiled code structure.
	 *
	 * This method attempts to transform simple if statements into proper if-else
	 * structures when the control flow allows it. It handles two main cases:
	 * 1. If-then-else: Both branches have direct paths to continuation
	 * 2. If-then: Only the if branch has a direct path, requiring condition negation
	 *
	 * @param ifstat the if statement to potentially reorder
	 * @return true if any reordering was performed, false otherwise
	 */
	private static boolean reorderIf(IfStatement ifstat) {
		// Only process simple if statements (not if-else)
		if (ifstat.iftype == IfStatement.IFTYPE_IFELSE) {
			return false;
		}

		// Initialize the context with all necessary state
		ReorderContext ctx = new ReorderContext(ifstat);

		// Compute direct path flags for if and else branches
		computeDirectPathFlags(ctx);

		// Validate that we have successors
		List<StatEdge> successors = ifstat.getAllSuccessorEdges();
		if (successors.isEmpty()) {
			throw new IllegalStateException("If statement " + ifstat + " has no successors!");
		}

		// Check for circular path that would prevent reordering
		if (!ctx.noElseStat && existsPath(ifstat, successors.get(0).getDestination())) {
			return false;
		}

		// Compute indirect path flags
		computeIndirectPathFlags(ctx);

		// Attempt reordering based on the computed flags
		if (ctx.canFormIfElse()) {
			return reorderAsIfElse(ctx);
		} else if (ctx.shouldReorderAsIfThen()) {
			return reorderAsIfThen(ctx);
		}

		return false;
	}

	/**
	 * Computes whether the if and else branches have direct control flow paths.
	 *
	 * A direct path exists when:
	 * - The edge type is FINALLYEXIT (exits through finally block)
	 * - Or there's a direct path from the source to the destination
	 */
	private static void computeDirectPathFlags(ReorderContext ctx) {
		// Compute if-branch direct flag
		if (ctx.ifstat.getIfstat() == null) {
			ctx.noIfStat = true;
			StatEdge ifEdge = ctx.ifstat.getIfEdge();
			// Finally exits are always considered direct paths
			ctx.ifDirect = isFinallyExit(ifEdge)
			               || MergeHelper.isDirectPath(ctx.from, ifEdge.getDestination());
		} else {
			List<StatEdge> ifSuccessors = ctx.ifstat.getIfstat().getAllSuccessorEdges();
			// Check if the if-branch's successor is a finally exit or has a direct end edge
			ctx.ifDirect = (!ifSuccessors.isEmpty() && isFinallyExit(ifSuccessors.get(0)))
			               || hasDirectEndEdge(ctx.ifstat.getIfstat(), ctx.from);
		}

		// Compute else-branch direct flag
		List<StatEdge> lastSuccessors = ctx.last.getAllSuccessorEdges();
		ctx.elseDirect = (!lastSuccessors.isEmpty() && isFinallyExit(lastSuccessors.get(0)))
		                 || hasDirectEndEdge(ctx.last, ctx.from);
	}

	/**
	 * Checks if an edge represents a finally exit.
	 */
	private static boolean isFinallyExit(StatEdge edge) {
		return edge != null && edge.getType() == StatEdge.TYPE_FINALLYEXIT;
	}

	/**
	 * Computes indirect path flags by checking if paths exist through the statement graph.
	 */
	private static void computeIndirectPathFlags(ReorderContext ctx) {
		// Check for indirect if-branch path
		if (!ctx.ifDirect && !ctx.noIfStat) {
			ctx.ifDirectPath = existsPath(ctx.ifstat, ctx.next);
		}

		// Check for indirect else-branch path by scanning sequence statements
		if (!ctx.elseDirect && !ctx.noElseStat) {
			SequenceStatement sequence = (SequenceStatement) ctx.parent;
			ctx.elseDirectPath = hasIndirectElsePath(sequence, ctx.ifstat, ctx.next);
		}
	}

	/**
	 * Checks if there's an indirect path from any statement after the if to the next statement.
	 */
	private static boolean hasIndirectElsePath(SequenceStatement sequence, IfStatement ifstat, Statement next) {
		List<Statement> stats = sequence.getStats();
		for (int i = stats.size() - 1; i >= 0; i--) {
			Statement current = stats.get(i);
			if (current == ifstat) {
				break;
			}
			if (existsPath(current, next)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Reorders the if statement into a proper if-then-else structure.
	 *
	 * This is used when both branches have direct (or indirect) paths to continuation,
	 * allowing us to form a complete if-else structure.
	 */
	private static boolean reorderAsIfElse(ReorderContext ctx) {
		SequenceStatement sequence = (SequenceStatement) ctx.parent;

		// Extract statements that will form the else branch
		List<Statement> elseStatements = extractStatementsAfterIf(sequence, ctx.ifstat);

		// Build the else statement (single statement or sequence)
		Statement elseStatement = buildElseStatement(elseStatements);

		// Disconnect the if statement from its current successor
		ctx.ifstat.removeSuccessor(ctx.ifstat.getAllSuccessorEdges().get(0));

		// Remove the else statements from the sequence
		removeStatementsFromSequence(sequence, elseStatements);

		// Connect the else branch to the if statement
		attachElseBranch(ctx.ifstat, elseStatement);

		// Mark as if-else type
		ctx.ifstat.iftype = IfStatement.IFTYPE_IFELSE;

		return true;
	}

	/**
	 * Reorders the if statement by negating the condition (if-then case).
	 *
	 * This is used when the if branch has a direct path but the else doesn't,
	 * requiring us to negate the condition and swap the branches.
	 */
	private static boolean reorderAsIfThen(ReorderContext ctx) {
		// Negate the if condition
		negateIfCondition(ctx.ifstat);

		if (ctx.noElseStat) {
			return reorderWithNoElseStat(ctx);
		} else {
			return reorderWithElseStat(ctx);
		}
	}

	/**
	 * Negates the condition of an if statement.
	 */
	private static void negateIfCondition(IfStatement ifstat) {
		IfExprent headExpr = ifstat.getHeadexprent();
		headExpr.setCondition(new FunctionExprent(
			FunctionExprent.FUNCTION_BOOL_NOT,
			headExpr.getCondition(),
			null
		));
	}

	/**
	 * Handles reordering when there's no else statement (if is last in sequence).
	 */
	private static boolean reorderWithNoElseStat(ReorderContext ctx) {
		StatEdge ifEdge = ctx.ifstat.getIfEdge();
		StatEdge elseEdge = ctx.ifstat.getAllSuccessorEdges().get(0);

		if (ctx.noIfStat) {
			// No if body: just swap the edges
			swapIfAndElseEdges(ctx.ifstat, ifEdge, elseEdge);
		} else {
			// Has if body: extract it and create sequence
			extractIfBodyToSequence(ctx.ifstat, ifEdge, elseEdge);
		}
		return true;
	}

	/**
	 * Swaps the if and else edges when there's no if body.
	 */
	private static void swapIfAndElseEdges(IfStatement ifstat, StatEdge ifEdge, StatEdge elseEdge) {
		ifstat.getFirst().removeSuccessor(ifEdge);
		ifstat.removeSuccessor(elseEdge);

		ifEdge.setSource(ifstat);
		elseEdge.setSource(ifstat.getFirst());

		ifstat.addSuccessor(ifEdge);
		ifstat.getFirst().addSuccessor(elseEdge);

		ifstat.setIfEdge(elseEdge);
	}

	/**
	 * Extracts the if body and creates a sequence statement.
	 */
	private static void extractIfBodyToSequence(IfStatement ifstat, StatEdge ifEdge, StatEdge elseEdge) {
		Statement ifBranch = ifstat.getIfstat();
		SequenceStatement newSequence = new SequenceStatement(Arrays.asList(ifstat, ifBranch));

		// Disconnect the if body
		ifstat.getFirst().removeSuccessor(ifEdge);
		ifstat.getStats().removeWithKey(ifBranch.id);
		ifstat.setIfstat(null);

		// Redirect the else edge to become the if edge
		ifstat.removeSuccessor(elseEdge);
		elseEdge.setSource(ifstat.getFirst());
		ifstat.getFirst().addSuccessor(elseEdge);
		ifstat.setIfEdge(elseEdge);

		// Replace in parent and set up the sequence
		ifstat.getParent().replaceStatement(ifstat, newSequence);
		newSequence.setAllParent();

		// Connect if statement to the extracted branch
		ifstat.addSuccessor(new StatEdge(StatEdge.TYPE_REGULAR, ifstat, ifBranch));
	}

	/**
	 * Handles reordering when there are statements after the if (else exists).
	 */
	private static boolean reorderWithElseStat(ReorderContext ctx) {
		SequenceStatement sequence = (SequenceStatement) ctx.parent;

		// Extract statements that will form the new if body
		List<Statement> elseStatements = extractStatementsAfterIf(sequence, ctx.ifstat);

		// Build the new if body
		Statement newIfBody = buildElseStatement(elseStatements);

		// Disconnect the if statement from its current successor
		ctx.ifstat.removeSuccessor(ctx.ifstat.getAllSuccessorEdges().get(0));

		// Remove the statements from the sequence
		removeStatementsFromSequence(sequence, elseStatements);

		// Handle the original if body (move it to sequence)
		if (ctx.noIfStat) {
			moveIfEdgeToStatement(ctx.ifstat);
		} else {
			moveIfBodyToSequence(ctx.ifstat, sequence);
		}

		// Attach the new if body
		attachIfBranch(ctx.ifstat, newIfBody);

		return true;
	}

	/**
	 * Moves the if edge to the statement level when there's no if body.
	 */
	private static void moveIfEdgeToStatement(IfStatement ifstat) {
		StatEdge ifEdge = ifstat.getIfEdge();
		ifstat.getFirst().removeSuccessor(ifEdge);
		ifEdge.setSource(ifstat);
		ifstat.addSuccessor(ifEdge);
	}

	/**
	 * Moves the if body to the parent sequence.
	 */
	private static void moveIfBodyToSequence(IfStatement ifstat, SequenceStatement sequence) {
		Statement ifBranch = ifstat.getIfstat();

		ifstat.getFirst().removeSuccessor(ifstat.getIfEdge());
		ifstat.getStats().removeWithKey(ifBranch.id);

		ifstat.addSuccessor(new StatEdge(StatEdge.TYPE_REGULAR, ifstat, ifBranch));

		sequence.getStats().addWithKey(ifBranch, ifBranch.id);
		ifBranch.setParent(sequence);
	}

	/**
	 * Attaches a statement as the if branch.
	 */
	private static void attachIfBranch(IfStatement ifstat, Statement body) {
		StatEdge newIfEdge = new StatEdge(StatEdge.TYPE_REGULAR, ifstat.getFirst(), body);
		ifstat.getFirst().addSuccessor(newIfEdge);
		ifstat.setIfstat(body);
		ifstat.setIfEdge(newIfEdge);

		ifstat.getStats().addWithKey(body, body.id);
		body.setParent(ifstat);
	}

	/**
	 * Extracts all statements from a sequence that come after the specified if statement.
	 */
	private static List<Statement> extractStatementsAfterIf(SequenceStatement sequence, IfStatement ifstat) {
		List<Statement> result = new ArrayList<>();
		List<Statement> stats = sequence.getStats();

		for (int i = stats.size() - 1; i >= 0; i--) {
			Statement current = stats.get(i);
			if (current == ifstat) {
				break;
			}
			result.add(0, current);
		}

		return result;
	}

	/**
	 * Builds an else statement from a list of statements.
	 * Returns the single statement if only one, otherwise wraps in a SequenceStatement.
	 */
	private static Statement buildElseStatement(List<Statement> statements) {
		if (statements.size() == 1) {
			return statements.get(0);
		}

		SequenceStatement sequence = new SequenceStatement(statements);
		sequence.setAllParent();
		return sequence;
	}

	/**
	 * Removes a list of statements from a sequence.
	 */
	private static void removeStatementsFromSequence(SequenceStatement sequence, List<Statement> statements) {
		for (Statement st : statements) {
			sequence.getStats().removeWithKey(st.id);
		}
	}

	/**
	 * Attaches a statement as the else branch of an if statement.
	 */
	private static void attachElseBranch(IfStatement ifstat, Statement elseStatement) {
		StatEdge elseEdge = new StatEdge(StatEdge.TYPE_REGULAR, ifstat.getFirst(), elseStatement);
		ifstat.getFirst().addSuccessor(elseEdge);
		ifstat.setElsestat(elseStatement);
		ifstat.setElseEdge(elseEdge);

		ifstat.getStats().addWithKey(elseStatement, elseStatement.id);
		elseStatement.setParent(ifstat);
	}

	private static boolean hasDirectEndEdge(Statement stat,
	                                        Statement from) {

		for (StatEdge edge : stat.getAllSuccessorEdges()) {
			if (MergeHelper.isDirectPath(from,
			                             edge.getDestination())) {
				return true;
			}
		}

		if (stat.getExprents() == null) {
			switch (stat.type) {
				case Statement.TYPE_SEQUENCE:
					return hasDirectEndEdge(stat.getStats()
					                            .getLast(),
					                        from);
				case Statement.TYPE_CATCHALL:
				case Statement.TYPE_TRYCATCH:
					for (Statement st : stat.getStats()) {
						if (hasDirectEndEdge(st,
						                     from)) {
							return true;
						}
					}
					break;
				case Statement.TYPE_IF:
					IfStatement ifstat = (IfStatement) stat;
					if (ifstat.iftype == IfStatement.IFTYPE_IFELSE) {
						return hasDirectEndEdge(ifstat.getIfstat(),
						                        from) || hasDirectEndEdge(ifstat.getElsestat(),
						                                                  from);
					}
					break;
				case Statement.TYPE_SYNCRONIZED:
					return hasDirectEndEdge(stat.getStats()
					                            .get(1),
					                        from);
				case Statement.TYPE_SWITCH:
					for (Statement st : stat.getStats()) {
						if (hasDirectEndEdge(st,
						                     from)) {
							return true;
						}
					}
			}
		}

		return false;
	}

	private static Statement getNextStatement(Statement stat) {
		Statement parent = stat.getParent();
		switch (parent.type) {
			case Statement.TYPE_ROOT:
				return ((RootStatement) parent).getDummyExit();
			case Statement.TYPE_DO:
				return parent;
			case Statement.TYPE_SEQUENCE:
				SequenceStatement sequence = (SequenceStatement) parent;
				if (sequence.getStats()
				            .getLast() != stat) {
					for (int i = sequence.getStats()
					                     .size() - 1;
					     i >= 0;
					     i--) {
						if (sequence.getStats()
						            .get(i) == stat) {
							return sequence.getStats()
							               .get(i + 1);
						}
					}
				}
		}

		return getNextStatement(parent);
	}

	private static boolean existsPath(Statement from,
	                                  Statement to) {
		for (StatEdge edge : to.getAllPredecessorEdges()) {
			if (from.containsStatementStrict(edge.getSource())) {
				return true;
			}
		}

		return false;
	}

	private static class IfNode {
		public final Statement     value;
		public final List<IfNode>  succs     = new ArrayList<>();
		public final List<Integer> edgetypes = new ArrayList<>();

		IfNode(Statement value) {
			this.value = value;
		}

		public void addChild(IfNode child,
		                     int type) {
			succs.add(child);
			edgetypes.add(type);
		}
	}
}