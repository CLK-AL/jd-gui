// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.BytecodeVersion;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.code.cfg.ControlFlowGraph;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.code.DeadCodeHelper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchAllStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Processes finally blocks in decompiled Java bytecode.
 *
 * <p>This class coordinates the detection, extraction, and transformation of
 * finally blocks during decompilation. The actual work is delegated to:
 * <ul>
 *   <li>{@link FinallyDetector} - Detects and analyzes finally block structure</li>
 *   <li>{@link FinallyExtractor} - Extracts and compares finally block copies</li>
 *   <li>{@link FinallyTransformer} - Transforms the control flow graph</li>
 * </ul>
 */
public class FinallyProcessor {
	private final Map<Integer, Integer> finallyBlockIDs = new HashMap<>();
	private final Map<Integer, Integer> catchallBlockIDs = new HashMap<>();

	private final MethodDescriptor methodDescriptor;
	private final VarProcessor varProcessor;

	private final FinallyDetector detector;
	private final FinallyExtractor extractor;

	public FinallyProcessor(MethodDescriptor md, VarProcessor varProc) {
		this.methodDescriptor = md;
		this.varProcessor = varProc;
		this.detector = new FinallyDetector(md, varProc);
		this.extractor = new FinallyExtractor();
	}

	/**
	 * Iterates through the statement graph, processing finally blocks.
	 *
	 * @param cl    the class containing the method
	 * @param mt    the method being decompiled
	 * @param root  the root statement of the method
	 * @param graph the control flow graph
	 * @return true if any changes were made, false otherwise
	 */
	public boolean iterateGraph(StructClass cl,
	                            StructMethod mt,
	                            RootStatement root,
	                            ControlFlowGraph graph) {
		BytecodeVersion bytecodeVersion = mt.getBytecodeVersion();

		LinkedList<Statement> stack = new LinkedList<>();
		stack.add(root);

		while (!stack.isEmpty()) {
			Statement stat = stack.removeLast();

			Statement parent = stat.getParent();
			if (parent != null
			    && parent.type == Statement.TYPE_CATCHALL
			    && stat == parent.getFirst()
			    && !parent.isCopied()) {

				CatchAllStatement fin = (CatchAllStatement) parent;
				BasicBlock head = fin.getBasichead().getBlock();
				BasicBlock handler = fin.getHandler().getBasichead().getBlock();

				if (catchallBlockIDs.containsKey(handler.id)) {
					// do nothing - already identified as catch-all
				} else if (finallyBlockIDs.containsKey(handler.id)) {
					// already identified as finally block
					fin.setFinally(true);

					Integer var = finallyBlockIDs.get(handler.id);
					fin.setMonitor(var == null
					               ? null
					               : new VarExprent(var, VarType.VARTYPE_INT, varProcessor));
				} else {
					// analyze the finally block
					FinallyDetector.FinallyInfo info = detector.getFinallyInformation(cl, mt, root, fin);

					if (info == null) {
						// inconsistent finally
						catchallBlockIDs.put(handler.id, null);
						root.addComment("$FF: Could not inline inconsistent finally blocks");
						root.addErrorComment = true;
					} else {
						if (DecompilerContext.getOption(IFernflowerPreferences.FINALLY_DEINLINE)
						    && verifyFinally(graph, fin, info)) {
							finallyBlockIDs.put(handler.id, null);
						} else {
							// insert semaphore to preserve control flow
							int varIndex = DecompilerContext.getCounterContainer()
							                                .getCounterAndIncrement(CounterContainer.VAR_COUNTER);
							FinallyTransformer.insertSemaphore(
								graph,
								FinallyTransformer.getAllBasicBlocks(fin.getFirst()),
								head,
								handler,
								varIndex,
								info,
								bytecodeVersion
							);

							finallyBlockIDs.put(handler.id, varIndex);

							root.addComment("$FF: Could not verify finally blocks. A semaphore variable has been "
							                + "added to preserve control flow.");
							root.addErrorComment = true;
						}

						// clean up the graph
						DeadCodeHelper.removeDeadBlocks(graph);
						DeadCodeHelper.removeEmptyBlocks(graph);
						DeadCodeHelper.mergeBasicBlocks(graph);
					}

					return true;
				}
			}

			stack.addAll(stat.getStats());
		}

		return false;
	}

	/**
	 * Verifies finally block structure and removes duplicated code.
	 *
	 * @param graph the control flow graph
	 * @param fstat the catch-all statement
	 * @param info  the finally block information
	 * @return true if verification succeeded
	 */
	private boolean verifyFinally(ControlFlowGraph graph,
	                              CatchAllStatement fstat,
	                              FinallyDetector.FinallyInfo info) {
		return FinallyTransformer.verifyFinallyEx(graph, fstat, info, extractor);
	}
}
