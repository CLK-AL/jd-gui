// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.*;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.code.cfg.ControlFlowGraph;
import org.jetbrains.java.decompiler.code.cfg.ExceptionRangeCFG;
import org.jetbrains.java.decompiler.modules.code.DeadCodeHelper;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchAllStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;

import java.util.*;
import java.util.Map.Entry;

/**
 * Handles transformation of control flow graph for finally block processing.
 */
public final class FinallyTransformer {

	/**
	 * Inserts a semaphore variable to preserve control flow when finally blocks
	 * cannot be verified.
	 */
	public static void insertSemaphore(ControlFlowGraph graph,
	                                   Set<BasicBlock> setTry,
	                                   BasicBlock head,
	                                   BasicBlock handler,
	                                   int var,
	                                   FinallyDetector.FinallyInfo information,
	                                   BytecodeVersion bytecode_version) {
		Set<BasicBlock> setCopy = new HashSet<>(setTry);

		int finallytype = information.getFirstCode();
		Map<BasicBlock, Boolean> mapLast = information.getMapLast();

		// first and last statements
		removeExceptionInstructionsEx(handler, 1, finallytype);
		for (Entry<BasicBlock, Boolean> entry : mapLast.entrySet()) {
			BasicBlock last = entry.getKey();

			if (entry.getValue()) {
				removeExceptionInstructionsEx(last, 2, finallytype);
				graph.getFinallyExits().add(last);
			}
		}

		final int store_length = var <= 3 ? 1 : var <= 128 ? 2 : 4;

		// disable semaphore at statement exit points
		for (BasicBlock block : setTry) {
			List<BasicBlock> lstSucc = block.getSuccs();

			for (BasicBlock dest : lstSucc) {
				// break out
				if (dest != graph.getLast() && !setCopy.contains(dest)) {
					// disable semaphore
					SimpleInstructionSequence seq = new SimpleInstructionSequence();
					seq.addInstruction(Instruction.create(CodeConstants.opc_bipush, false,
					                                      CodeConstants.GROUP_GENERAL, bytecode_version,
					                                      new int[]{0}, 1), -1);
					seq.addInstruction(Instruction.create(CodeConstants.opc_istore, false,
					                                      CodeConstants.GROUP_GENERAL, bytecode_version,
					                                      new int[]{var}, store_length), -1);

					// build a separate block
					BasicBlock newblock = new BasicBlock(++graph.last_id);
					newblock.setSeq(seq);

					// insert between block and dest
					block.replaceSuccessor(dest, newblock);
					newblock.addSuccessor(dest);
					setCopy.add(newblock);
					graph.getBlocks().addWithKey(newblock, newblock.id);

					// exception ranges
					// FIXME: special case synchronized

					// copy exception edges and extend protected ranges
					for (int j = 0; j < block.getSuccExceptions().size(); j++) {
						BasicBlock hd = block.getSuccExceptions().get(j);
						newblock.addSuccessorException(hd);

						ExceptionRangeCFG range = graph.getExceptionRange(hd, block);
						range.getProtectedRange().add(newblock);
					}
				}
			}
		}

		// enable semaphore at the statement entrance
		SimpleInstructionSequence seq = new SimpleInstructionSequence();
		seq.addInstruction(Instruction.create(CodeConstants.opc_bipush, false,
		                                      CodeConstants.GROUP_GENERAL, bytecode_version,
		                                      new int[]{1}, 1), -1);
		seq.addInstruction(Instruction.create(CodeConstants.opc_istore, false,
		                                      CodeConstants.GROUP_GENERAL, bytecode_version,
		                                      new int[]{var}, store_length), -1);

		BasicBlock newhead = new BasicBlock(++graph.last_id);
		newhead.setSeq(seq);

		insertBlockBefore(graph, head, newhead);

		// initialize semaphore with false
		seq = new SimpleInstructionSequence();
		seq.addInstruction(Instruction.create(CodeConstants.opc_bipush, false,
		                                      CodeConstants.GROUP_GENERAL, bytecode_version,
		                                      new int[]{0}, 1), -1);
		seq.addInstruction(Instruction.create(CodeConstants.opc_istore, false,
		                                      CodeConstants.GROUP_GENERAL, bytecode_version,
		                                      new int[]{var}, store_length), -1);

		BasicBlock newheadinit = new BasicBlock(++graph.last_id);
		newheadinit.setSeq(seq);

		insertBlockBefore(graph, newhead, newheadinit);

		setCopy.add(newhead);
		setCopy.add(newheadinit);

		for (BasicBlock hd : new HashSet<>(newheadinit.getSuccExceptions())) {
			ExceptionRangeCFG range = graph.getExceptionRange(hd, newheadinit);

			if (setCopy.containsAll(range.getProtectedRange())) {
				newheadinit.removeSuccessorException(hd);
				range.getProtectedRange().remove(newheadinit);
			}
		}
	}

	/**
	 * Inserts a block before another block in the control flow graph.
	 */
	public static void insertBlockBefore(ControlFlowGraph graph,
	                                     BasicBlock oldblock,
	                                     BasicBlock newblock) {
		List<BasicBlock> lstTemp = new ArrayList<>();
		lstTemp.addAll(oldblock.getPreds());
		lstTemp.addAll(oldblock.getPredExceptions());

		// replace predecessors
		for (BasicBlock pred : lstTemp) {
			pred.replaceSuccessor(oldblock, newblock);
		}

		// copy exception edges and extend protected ranges
		for (BasicBlock hd : oldblock.getSuccExceptions()) {
			newblock.addSuccessorException(hd);

			ExceptionRangeCFG range = graph.getExceptionRange(hd, oldblock);
			range.getProtectedRange().add(newblock);
		}

		// replace handler
		for (ExceptionRangeCFG range : graph.getExceptions()) {
			if (range.getHandler() == oldblock) {
				range.setHandler(newblock);
			}
		}

		newblock.addSuccessor(oldblock);
		graph.getBlocks().addWithKey(newblock, newblock.id);
		if (graph.getFirst() == oldblock) {
			graph.setFirst(newblock);
		}
	}

	/**
	 * Deletes an area (finally block copy) from the control flow graph.
	 */
	public static void deleteArea(ControlFlowGraph graph, FinallyExtractor.Area area) {
		BasicBlock start = area.getStart();
		BasicBlock next = area.getNext();

		if (start == next) {
			return;
		}

		if (next == null) {
			// dummy exit block
			next = graph.getLast();
		}

		// collect common exception ranges of predecessors and successors
		Set<BasicBlock> setCommonExceptionHandlers = new HashSet<>(next.getSuccExceptions());
		for (BasicBlock pred : start.getPreds()) {
			setCommonExceptionHandlers.retainAll(pred.getSuccExceptions());
		}

		boolean is_outside_range = false;

		Set<BasicBlock> setPredecessors = new HashSet<>(start.getPreds());

		// replace start with next
		for (BasicBlock pred : setPredecessors) {
			pred.replaceSuccessor(start, next);
		}

		Set<BasicBlock> setBlocks = area.getSample();

		Set<ExceptionRangeCFG> setCommonRemovedExceptionRanges = null;

		// remove all the blocks in between
		for (BasicBlock block : setBlocks) {
			// artificial basic blocks (those resulted from splitting)
			// can belong to more than one area
			if (graph.getBlocks().containsKey(block.id)) {
				if (!block.getSuccExceptions().containsAll(setCommonExceptionHandlers)) {
					is_outside_range = true;
				}

				Set<ExceptionRangeCFG> setRemovedExceptionRanges = new HashSet<>();
				for (BasicBlock handler : block.getSuccExceptions()) {
					setRemovedExceptionRanges.add(graph.getExceptionRange(handler, block));
				}

				if (setCommonRemovedExceptionRanges == null) {
					setCommonRemovedExceptionRanges = setRemovedExceptionRanges;
				} else {
					setCommonRemovedExceptionRanges.retainAll(setRemovedExceptionRanges);
				}

				// shift extern edges on splitted blocks
				if (block.getSeq().isEmpty() && block.getSuccs().size() == 1) {
					BasicBlock succs = block.getSuccs().get(0);
					for (BasicBlock pred : new ArrayList<>(block.getPreds())) {
						if (!setBlocks.contains(pred)) {
							pred.replaceSuccessor(block, succs);
						}
					}

					if (graph.getFirst() == block) {
						graph.setFirst(succs);
					}
				}

				graph.removeBlock(block);
			}
		}

		if (is_outside_range) {
			// new empty block
			BasicBlock emptyblock = new BasicBlock(++graph.last_id);

			graph.getBlocks().addWithKey(emptyblock, emptyblock.id);

			// add to ranges if necessary
			for (ExceptionRangeCFG range : setCommonRemovedExceptionRanges) {
				emptyblock.addSuccessorException(range.getHandler());
				range.getProtectedRange().add(emptyblock);
			}

			// insert between predecessors and next
			emptyblock.addSuccessor(next);
			for (BasicBlock pred : setPredecessors) {
				pred.replaceSuccessor(next, emptyblock);
			}
		}
	}

	/**
	 * Removes exception-related instructions from a basic block.
	 *
	 * @param block       the block to modify
	 * @param blocktype   1=first, 2=last, 3=both
	 * @param finallytype the type of finally block (0-3)
	 */
	public static void removeExceptionInstructionsEx(BasicBlock block, int blocktype, int finallytype) {
		InstructionSequence seq = block.getSeq();

		if (finallytype == 3) { // empty finally handler
			for (int i = seq.length() - 1; i >= 0; i--) {
				seq.removeInstruction(i);
			}
		} else {
			if ((blocktype & 1) > 0) { // first
				if (finallytype == 2 || finallytype == 1) { // astore or pop
					seq.removeInstruction(0);
				}
			}

			if ((blocktype & 2) > 0) { // last
				if (finallytype == 2 || finallytype == 0) {
					seq.removeLast();
				}

				if (finallytype == 2) { // astore
					seq.removeLast();
				}
			}
		}
	}

	/**
	 * Verifies and processes finally blocks, removing duplicated code.
	 *
	 * @param graph       the control flow graph
	 * @param fstat       the catch-all statement
	 * @param information the finally block information
	 * @param extractor   the extractor to use for comparison
	 * @return true if verification succeeded
	 */
	public static boolean verifyFinallyEx(ControlFlowGraph graph,
	                                      CatchAllStatement fstat,
	                                      FinallyDetector.FinallyInfo information,
	                                      FinallyExtractor extractor) {
		Set<BasicBlock> tryBlocks = getAllBasicBlocks(fstat.getFirst());
		Set<BasicBlock> catchBlocks = getAllBasicBlocks(fstat.getHandler());

		int finallytype = information.getFirstCode();
		Map<BasicBlock, Boolean> mapLast = information.getMapLast();

		BasicBlock first = fstat.getHandler().getBasichead().getBlock();
		boolean skippedFirst = false;

		if (finallytype == 3) {
			// empty finally
			removeExceptionInstructionsEx(first, 3, finallytype);

			if (mapLast.containsKey(first)) {
				graph.getFinallyExits().add(first);
			}

			return true;
		} else {
			if (first.getSeq().length() == 1 && finallytype > 0) {
				BasicBlock firstsuc = first.getSuccs().get(0);
				if (catchBlocks.contains(firstsuc)) {
					first = firstsuc;
					skippedFirst = true;
				}
			}
		}

		// identify start blocks
		Set<BasicBlock> startBlocks = new HashSet<>();
		for (BasicBlock block : tryBlocks) {
			startBlocks.addAll(block.getSuccs());
		}
		// throw in the try body will point directly to the dummy exit
		// so remove dummy exit
		startBlocks.remove(graph.getLast());
		startBlocks.removeAll(tryBlocks);
		List<BasicBlock> starts = new ArrayList<>(startBlocks);
		Collections.sort(starts, (o1, o2) -> o2.id - o1.id);

		List<FinallyExtractor.Area> lstAreas = new ArrayList<>();

		for (BasicBlock start : starts) {
			FinallyExtractor.Area arr = extractor.compareSubgraphsEx(graph, start, catchBlocks,
			                                                         first, finallytype, mapLast, skippedFirst);
			if (arr == null) {
				return false;
			}

			lstAreas.add(arr);
		}

		// delete areas
		for (FinallyExtractor.Area area : lstAreas) {
			deleteArea(graph, area);
		}

		List<Entry<BasicBlock, Boolean>> lasts = new ArrayList<>(mapLast.entrySet());
		// We must sort here to prevent decompile differences deriving from hash maps.
		Collections.sort(lasts, (o1, o2) -> o1.getKey().id - o2.getKey().id);

		// INFO: empty basic blocks may remain in the graph!
		for (Entry<BasicBlock, Boolean> entry : lasts) {
			BasicBlock last = entry.getKey();

			if (entry.getValue()) {
				removeExceptionInstructionsEx(last, 2, finallytype);
				graph.getFinallyExits().add(last);
			}
		}

		removeExceptionInstructionsEx(fstat.getHandler().getBasichead().getBlock(), 1, finallytype);

		return true;
	}

	/**
	 * Collects all basic blocks from a statement and its children.
	 */
	public static Set<BasicBlock> getAllBasicBlocks(Statement stat) {
		List<Statement> lst = new LinkedList<>();
		lst.add(stat);

		int index = 0;
		do {
			Statement st = lst.get(index);

			if (st.type == Statement.TYPE_BASICBLOCK) {
				index++;
			} else {
				lst.addAll(st.getStats());
				lst.remove(index);
			}
		} while (index < lst.size());

		Set<BasicBlock> res = new HashSet<>();

		for (Statement st : lst) {
			res.add(((org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement) st).getBlock());
		}

		return res;
	}
}
