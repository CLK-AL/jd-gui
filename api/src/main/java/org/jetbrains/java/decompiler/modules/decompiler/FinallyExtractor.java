// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.code.SimpleInstructionSequence;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.code.cfg.ControlFlowGraph;
import org.jetbrains.java.decompiler.code.cfg.ExceptionRangeCFG;
import org.jetbrains.java.decompiler.modules.code.DeadCodeHelper;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.*;

/**
 * Handles extraction and comparison of finally block code.
 */
public final class FinallyExtractor {

	/**
	 * Represents an extracted area of code (a finally block copy).
	 */
	public static final class Area {
		private final BasicBlock start;
		private final Set<BasicBlock> sample;
		private final BasicBlock next;

		public Area(BasicBlock start, Set<BasicBlock> sample, BasicBlock next) {
			this.start = start;
			this.sample = sample;
			this.next = next;
		}

		public BasicBlock getStart() {
			return start;
		}

		public Set<BasicBlock> getSample() {
			return sample;
		}

		public BasicBlock getNext() {
			return next;
		}
	}

	/**
	 * Compares subgraphs to identify finally block copies.
	 *
	 * @param graph        the control flow graph
	 * @param startSample  the starting block of the sample
	 * @param catchBlocks  the set of blocks in the catch/finally handler
	 * @param startCatch   the starting block of the catch handler
	 * @param finallytype  the type of finally block
	 * @param mapLast      map of last blocks to their exit status
	 * @param skippedFirst whether the first block was skipped
	 * @return an Area representing the matched region, or null if no match
	 */
	public Area compareSubgraphsEx(ControlFlowGraph graph,
	                               BasicBlock startSample,
	                               Set<BasicBlock> catchBlocks,
	                               BasicBlock startCatch,
	                               int finallytype,
	                               Map<BasicBlock, Boolean> mapLast,
	                               boolean skippedFirst) {

		List<BlockStackEntry> stack = new LinkedList<>();
		Set<BasicBlock> setSample = new HashSet<>();
		Map<String, BasicBlock[]> mapNext = new HashMap<>();

		stack.add(new BlockStackEntry(startCatch, startSample, new ArrayList<>()));

		while (!stack.isEmpty()) {
			BlockStackEntry entry = stack.remove(0);
			BasicBlock blockCatch = entry.blockCatch;
			BasicBlock blockSample = entry.blockSample;

			boolean isFirstBlock = !skippedFirst && blockCatch == startCatch;
			boolean isLastBlock = mapLast.containsKey(blockCatch);
			boolean isTrueLastBlock = isLastBlock && mapLast.get(blockCatch);

			if (!compareBasicBlocksEx(graph, blockCatch, blockSample,
			                          (isFirstBlock ? 1 : 0) | (isTrueLastBlock ? 2 : 0),
			                          finallytype, entry.lstStoreVars)) {
				return null;
			}

			if (blockSample.getSuccs().size() != blockCatch.getSuccs().size()) {
				return null;
			}

			setSample.add(blockSample);

			// direct successors
			for (int i = 0; i < blockCatch.getSuccs().size(); i++) {
				BasicBlock sucCatch = blockCatch.getSuccs().get(i);
				BasicBlock sucSample = blockSample.getSuccs().get(i);

				if (catchBlocks.contains(sucCatch) && !setSample.contains(sucSample)) {
					stack.add(new BlockStackEntry(sucCatch, sucSample, entry.lstStoreVars));
				}
			}

			// exception successors
			if (isLastBlock && blockSample.getSeq().isEmpty()) {
				// do nothing, blockSample will be removed anyway
			} else {
				if (blockCatch.getSuccExceptions().size() == blockSample.getSuccExceptions().size()) {
					for (int i = 0; i < blockCatch.getSuccExceptions().size(); i++) {
						BasicBlock sucCatch = blockCatch.getSuccExceptions().get(i);
						BasicBlock sucSample = blockSample.getSuccExceptions().get(i);

						String excCatch = graph.getExceptionRange(sucCatch, blockCatch).getUniqueExceptionsString();
						String excSample = graph.getExceptionRange(sucSample, blockSample).getUniqueExceptionsString();

						// FIXME: compare handlers if possible
						boolean equalexc = excCatch == null ? excSample == null : excCatch.equals(excSample);

						if (equalexc) {
							if (catchBlocks.contains(sucCatch) && !setSample.contains(sucSample)) {
								List<int[]> lst = entry.lstStoreVars;

								if (sucCatch.getSeq().length() > 0 && sucSample.getSeq().length() > 0) {
									Instruction instrCatch = sucCatch.getSeq().getInstr(0);
									Instruction instrSample = sucSample.getSeq().getInstr(0);

									if (instrCatch.opcode == CodeConstants.opc_astore
									    && instrSample.opcode == CodeConstants.opc_astore) {
										lst = new ArrayList<>(lst);
										lst.add(new int[]{instrCatch.operand(0), instrSample.operand(0)});
									}
								}

								stack.add(new BlockStackEntry(sucCatch, sucSample, lst));
							}
						} else {
							return null;
						}
					}
				} else {
					return null;
				}
			}

			if (isLastBlock) {
				Set<BasicBlock> setSuccs = new HashSet<>(blockSample.getSuccs());
				setSuccs.removeAll(setSample);

				for (BlockStackEntry stackent : stack) {
					setSuccs.remove(stackent.blockSample);
				}

				for (BasicBlock succ : setSuccs) {
					if (graph.getLast() != succ) { // FIXME: why?
						mapNext.put(blockSample.id + "#" + succ.id,
						            new BasicBlock[]{blockSample, succ, isTrueLastBlock ? succ : null});
					}
				}
			}
		}

		return new Area(startSample, setSample,
		                getUniqueNext(graph, new HashSet<>(mapNext.values())));
	}

	/**
	 * Compares two basic blocks for equivalence in finally block matching.
	 */
	public boolean compareBasicBlocksEx(ControlFlowGraph graph,
	                                    BasicBlock pattern,
	                                    BasicBlock sample,
	                                    int type,
	                                    int finallytype,
	                                    List<int[]> lstStoreVars) {
		InstructionSequence seqPattern = pattern.getSeq();
		InstructionSequence seqSample = sample.getSeq();

		if (type != 0) {
			seqPattern = seqPattern.clone();

			if ((type & 1) > 0) { // first
				if (finallytype > 0) {
					seqPattern.removeInstruction(0);
				}
			}

			if ((type & 2) > 0) { // last
				if (finallytype == 0 || finallytype == 2) {
					seqPattern.removeLast();
				}

				if (finallytype == 2) {
					seqPattern.removeLast();
				}
			}
		}

		if (seqPattern.length() > seqSample.length()) {
			return false;
		}

		for (int i = 0; i < seqPattern.length(); i++) {
			Instruction instrPattern = seqPattern.getInstr(i);
			Instruction instrSample = seqSample.getInstr(i);

			// compare instructions with respect to jumps
			if (!equalInstructions(instrPattern, instrSample, lstStoreVars)) {
				return false;
			}
		}

		if (seqPattern.length() < seqSample.length()) { // split in two blocks
			SimpleInstructionSequence seq = new SimpleInstructionSequence();
			LinkedList<Integer> oldOffsets = new LinkedList<>();
			for (int i = seqSample.length() - 1; i >= seqPattern.length(); i--) {
				seq.addInstruction(0, seqSample.getInstr(i), -1);
				oldOffsets.addFirst(sample.getOldOffset(i));
				seqSample.removeInstruction(i);
			}

			BasicBlock newblock = new BasicBlock(++graph.last_id);
			newblock.setSeq(seq);
			newblock.getInstrOldOffsets().addAll(oldOffsets);

			List<BasicBlock> lstTemp = new ArrayList<>(sample.getSuccs());

			// move successors
			for (BasicBlock suc : lstTemp) {
				sample.removeSuccessor(suc);
				newblock.addSuccessor(suc);
			}

			sample.addSuccessor(newblock);

			graph.getBlocks().addWithKey(newblock, newblock.id);

			Set<BasicBlock> setFinallyExits = graph.getFinallyExits();
			if (setFinallyExits.contains(sample)) {
				setFinallyExits.remove(sample);
				setFinallyExits.add(newblock);
			}

			// copy exception edges and extend protected ranges
			for (int j = 0; j < sample.getSuccExceptions().size(); j++) {
				BasicBlock hd = sample.getSuccExceptions().get(j);
				newblock.addSuccessorException(hd);

				ExceptionRangeCFG range = graph.getExceptionRange(hd, sample);
				range.getProtectedRange().add(newblock);
			}
		}

		return true;
	}

	/**
	 * Compares two instructions for equivalence.
	 */
	public boolean equalInstructions(Instruction first, Instruction second, List<int[]> lstStoreVars) {
		if (!Instruction.equals(first, second)) {
			return false;
		}

		if (first.group != CodeConstants.GROUP_JUMP) { // FIXME: switch comparison
			for (int i = 0; i < first.operandsCount(); i++) {
				int firstOp = first.operand(i);
				int secondOp = second.operand(i);
				if (firstOp != secondOp) {
					// a-load/store instructions
					if (first.opcode == CodeConstants.opc_aload) {
						for (int[] arr : lstStoreVars) {
							if (arr[0] == firstOp && arr[1] == secondOp) {
								return true;
							}
						}
					} else if (first.opcode == CodeConstants.opc_astore) {
						lstStoreVars.add(new int[]{firstOp, secondOp});
						return true;
					}

					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Gets the unique next block from a set of possible exits.
	 */
	public BasicBlock getUniqueNext(ControlFlowGraph graph, Set<BasicBlock[]> setNext) {
		// precondition: there is at most one true exit path in a finally statement

		BasicBlock next = null;
		boolean multiple = false;

		for (BasicBlock[] arr : setNext) {
			if (arr[2] != null) {
				next = arr[1];
				multiple = false;
				break;
			} else {
				if (next == null) {
					next = arr[1];
				} else if (next != arr[1]) {
					multiple = true;
				}

				if (arr[1].getPreds().size() == 1) {
					next = arr[1];
				}
			}
		}

		if (multiple) { // TODO: generic solution
			for (BasicBlock[] arr : setNext) {
				BasicBlock block = arr[1];

				if (block != next) {
					if (InterpreterUtil.equalSets(next.getSuccs(), block.getSuccs())) {
						InstructionSequence seqNext = next.getSeq();
						InstructionSequence seqBlock = block.getSeq();

						if (seqNext.length() == seqBlock.length()) {
							for (int i = 0; i < seqNext.length(); i++) {
								Instruction instrNext = seqNext.getInstr(i);
								Instruction instrBlock = seqBlock.getInstr(i);

								if (!Instruction.equals(instrNext, instrBlock)) {
									return null;
								}
								for (int j = 0; j < instrNext.operandsCount(); j++) {
									if (instrNext.operand(j) != instrBlock.operand(j)) {
										return null;
									}
								}
							}
						} else {
							return null;
						}
					} else {
						return null;
					}
				}
			}

			for (BasicBlock[] arr : setNext) {
				if (arr[1] != next) {
					// FIXME: exception edge possible?
					arr[0].removeSuccessor(arr[1]);
					arr[0].addSuccessor(next);
				}
			}

			DeadCodeHelper.removeDeadBlocks(graph);
		}

		return next;
	}

	/**
	 * Internal class to track block comparison state during traversal.
	 */
	private static final class BlockStackEntry {
		final BasicBlock blockCatch;
		final BasicBlock blockSample;
		final List<int[]> lstStoreVars;

		BlockStackEntry(BasicBlock blockCatch, BasicBlock blockSample, List<int[]> lstStoreVars) {
			this.blockCatch = blockCatch;
			this.blockSample = blockSample;
			this.lstStoreVars = new ArrayList<>(lstStoreVars);
		}
	}
}
