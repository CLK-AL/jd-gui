// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.attr.StructBootstrapMethodsAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.ListStack;

import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Processes bytecode instructions and converts them to expression trees.
 * This is the core of the bytecode-to-expression conversion process.
 */
public class InstructionProcessor implements CodeConstants {

	private final MethodDescriptor methodDescriptor;
	private final VarProcessor varProcessor;

	public InstructionProcessor(MethodDescriptor md, VarProcessor varProc) {
		this.methodDescriptor = md;
		this.varProcessor = varProc;
	}

	/**
	 * Processes a basic block and converts its bytecode instructions to expressions.
	 *
	 * @param stat the basic block statement
	 * @param data the primitive expression list for stack simulation
	 * @param cl   the class structure
	 */
	public void processBlock(BasicBlockStatement stat,
	                         PrimitiveExprsList data,
	                         StructClass cl) {

		ConstantPool pool = cl.getPool();
		StructBootstrapMethodsAttribute bootstrap = cl.getAttribute(StructGeneralAttribute.ATTRIBUTE_BOOTSTRAP_METHODS);

		BasicBlock block = stat.getBlock();

		ListStack<Exprent> stack = data.getStack();
		List<Exprent> exprlist = data.getLstExprents();

		InstructionSequence seq = block.getSeq();

		for (int i = 0; i < seq.length(); i++) {
			Instruction instr = seq.getInstr(i);
			Integer bytecode_offset = block.getOldOffset(i);
			BitSet bytecode_offsets = null;
			if (bytecode_offset >= 0) {
				bytecode_offsets = new BitSet();
				bytecode_offsets.set(bytecode_offset);
				int end_offset = block.getOldOffset(i + 1);
				if (end_offset > bytecode_offset) {
					bytecode_offsets.set(bytecode_offset, end_offset);
				}
			}

			processInstruction(instr, bytecode_offset, bytecode_offsets, stack, exprlist,
			                   pool, bootstrap, seq, i);
		}
	}

	/**
	 * Processes a single bytecode instruction.
	 */
	private void processInstruction(Instruction instr,
	                                Integer bytecode_offset,
	                                BitSet bytecode_offsets,
	                                ListStack<Exprent> stack,
	                                List<Exprent> exprlist,
	                                ConstantPool pool,
	                                StructBootstrapMethodsAttribute bootstrap,
	                                InstructionSequence seq,
	                                int instrIndex) {

		switch (instr.opcode) {
			case opc_aconst_null:
				pushEx(stack, exprlist, new ConstExprent(VarType.VARTYPE_NULL, null, bytecode_offsets));
				break;

			case opc_bipush:
			case opc_sipush:
				pushEx(stack, exprlist, new ConstExprent(instr.operand(0), true, bytecode_offsets));
				break;

			case opc_lconst_0:
			case opc_lconst_1:
				pushEx(stack, exprlist,
				       new ConstExprent(VarType.VARTYPE_LONG, (long) (instr.opcode - opc_lconst_0), bytecode_offsets));
				break;

			case opc_fconst_0:
			case opc_fconst_1:
			case opc_fconst_2:
				pushEx(stack, exprlist,
				       new ConstExprent(VarType.VARTYPE_FLOAT, (float) (instr.opcode - opc_fconst_0), bytecode_offsets));
				break;

			case opc_dconst_0:
			case opc_dconst_1:
				pushEx(stack, exprlist,
				       new ConstExprent(VarType.VARTYPE_DOUBLE, (double) (instr.opcode - opc_dconst_0), bytecode_offsets));
				break;

			case opc_ldc:
			case opc_ldc_w:
			case opc_ldc2_w:
				processLdc(instr, bytecode_offsets, stack, exprlist, pool, bootstrap);
				break;

			case opc_iload:
			case opc_lload:
			case opc_fload:
			case opc_dload:
			case opc_aload:
				processLoad(instr, bytecode_offset, bytecode_offsets, stack, exprlist);
				break;

			case opc_iaload:
			case opc_laload:
			case opc_faload:
			case opc_daload:
			case opc_aaload:
			case opc_baload:
			case opc_caload:
			case opc_saload:
				processArrayLoad(instr, bytecode_offsets, stack, exprlist);
				break;

			case opc_istore:
			case opc_lstore:
			case opc_fstore:
			case opc_dstore:
			case opc_astore:
				processStore(instr, bytecode_offset, bytecode_offsets, stack, exprlist);
				break;

			case opc_iastore:
			case opc_lastore:
			case opc_fastore:
			case opc_dastore:
			case opc_aastore:
			case opc_bastore:
			case opc_castore:
			case opc_sastore:
				processArrayStore(instr, bytecode_offsets, stack, exprlist);
				break;

			case opc_iadd:
			case opc_ladd:
			case opc_fadd:
			case opc_dadd:
			case opc_isub:
			case opc_lsub:
			case opc_fsub:
			case opc_dsub:
			case opc_imul:
			case opc_lmul:
			case opc_fmul:
			case opc_dmul:
			case opc_idiv:
			case opc_ldiv:
			case opc_fdiv:
			case opc_ddiv:
			case opc_irem:
			case opc_lrem:
			case opc_frem:
			case opc_drem:
				pushEx(stack, exprlist,
				       new FunctionExprent(InstructionTables.FUNC1[(instr.opcode - opc_iadd) / 4], stack, bytecode_offsets));
				break;

			case opc_ishl:
			case opc_lshl:
			case opc_ishr:
			case opc_lshr:
			case opc_iushr:
			case opc_lushr:
			case opc_iand:
			case opc_land:
			case opc_ior:
			case opc_lor:
			case opc_ixor:
			case opc_lxor:
				pushEx(stack, exprlist,
				       new FunctionExprent(InstructionTables.FUNC2[(instr.opcode - opc_ishl) / 2], stack, bytecode_offsets));
				break;

			case opc_ineg:
			case opc_lneg:
			case opc_fneg:
			case opc_dneg:
				pushEx(stack, exprlist,
				       new FunctionExprent(FunctionExprent.FUNCTION_NEG, stack, bytecode_offsets));
				break;

			case opc_iinc:
				processIinc(instr, bytecode_offset, bytecode_offsets, exprlist);
				break;

			case opc_i2l:
			case opc_i2f:
			case opc_i2d:
			case opc_l2i:
			case opc_l2f:
			case opc_l2d:
			case opc_f2i:
			case opc_f2l:
			case opc_f2d:
			case opc_d2i:
			case opc_d2l:
			case opc_d2f:
			case opc_i2b:
			case opc_i2c:
			case opc_i2s:
				pushEx(stack, exprlist,
				       new FunctionExprent(InstructionTables.FUNC3[instr.opcode - opc_i2l], stack, bytecode_offsets));
				break;

			case opc_lcmp:
			case opc_fcmpl:
			case opc_fcmpg:
			case opc_dcmpl:
			case opc_dcmpg:
				pushEx(stack, exprlist,
				       new FunctionExprent(InstructionTables.FUNC4[instr.opcode - opc_lcmp], stack, bytecode_offsets));
				break;

			case opc_ifeq:
			case opc_ifne:
			case opc_iflt:
			case opc_ifge:
			case opc_ifgt:
			case opc_ifle:
				exprlist.add(new IfExprent(
					InstructionTables.NEG_IFS[InstructionTables.FUNC5[instr.opcode - opc_ifeq]],
					stack, bytecode_offsets));
				break;

			case opc_if_icmpeq:
			case opc_if_icmpne:
			case opc_if_icmplt:
			case opc_if_icmpge:
			case opc_if_icmpgt:
			case opc_if_icmple:
			case opc_if_acmpeq:
			case opc_if_acmpne:
				exprlist.add(new IfExprent(
					InstructionTables.NEG_IFS[InstructionTables.FUNC6[instr.opcode - opc_if_icmpeq]],
					stack, bytecode_offsets));
				break;

			case opc_ifnull:
			case opc_ifnonnull:
				exprlist.add(new IfExprent(
					InstructionTables.NEG_IFS[InstructionTables.FUNC7[instr.opcode - opc_ifnull]],
					stack, bytecode_offsets));
				break;

			case opc_tableswitch:
			case opc_lookupswitch:
				exprlist.add(new SwitchHeadExprent(stack.pop(), bytecode_offsets));
				break;

			case opc_ireturn:
			case opc_lreturn:
			case opc_freturn:
			case opc_dreturn:
			case opc_areturn:
			case opc_return:
			case opc_athrow:
				processReturn(instr, bytecode_offsets, stack, exprlist);
				break;

			case opc_monitorenter:
			case opc_monitorexit:
				exprlist.add(new MonitorExprent(
					InstructionTables.FUNC8[instr.opcode - opc_monitorenter],
					stack.pop(), bytecode_offsets));
				break;

			case opc_checkcast:
			case opc_instanceof:
				stack.push(new ConstExprent(
					new VarType(pool.getPrimitiveConstant(instr.operand(0)).getString(), true),
					null, null));
				// Fall through to opc_arraylength
			case opc_arraylength:
				pushEx(stack, exprlist,
				       new FunctionExprent(InstructionTables.getMapConst(instr.opcode), stack, bytecode_offsets));
				break;

			case opc_getstatic:
			case opc_getfield:
				processGetField(instr, bytecode_offsets, stack, exprlist, pool);
				break;

			case opc_putstatic:
			case opc_putfield:
				processPutField(instr, bytecode_offsets, stack, exprlist, pool);
				break;

			case opc_invokevirtual:
			case opc_invokespecial:
			case opc_invokestatic:
			case opc_invokeinterface:
			case opc_invokedynamic:
				processInvoke(instr, bytecode_offsets, stack, exprlist, pool, bootstrap);
				break;

			case opc_new:
			case opc_anewarray:
			case opc_multianewarray:
				processNew(instr, bytecode_offsets, stack, exprlist, pool);
				break;

			case opc_newarray:
				pushEx(stack, exprlist,
				       new NewExprent(new VarType(InstructionTables.ARR_TYPE_IDS[instr.operand(0) - 4], 1),
				                      stack, 1, bytecode_offsets));
				break;

			case opc_dup:
				pushEx(stack, exprlist, stack.getByOffset(-1).copy());
				break;

			case opc_dup_x1:
				insertByOffsetEx(-2, stack, exprlist, -1);
				break;

			case opc_dup_x2:
				if (stack.getByOffset(-2).getExprType().stackSize == 2) {
					insertByOffsetEx(-2, stack, exprlist, -1);
				} else {
					insertByOffsetEx(-3, stack, exprlist, -1);
				}
				break;

			case opc_dup2:
				processDup2(stack, exprlist);
				break;

			case opc_dup2_x1:
				processDup2X1(stack, exprlist);
				break;

			case opc_dup2_x2:
				processDup2X2(stack, exprlist);
				break;

			case opc_swap:
				insertByOffsetEx(-2, stack, exprlist, -1);
				stack.pop();
				break;

			case opc_pop:
				processPop(stack, exprlist, seq, instrIndex);
				break;

			case opc_pop2:
				if (stack.getByOffset(-1).getExprType().stackSize == 1) {
					// Since value at the top of the stack is a value of category 1 (JVMS9 2.11.1)
					// we should remove one more item from the stack.
					// See JVMS9 pop2 chapter.
					stack.pop();
				}
				stack.pop();
				break;
		}
	}

	/**
	 * Processes LDC instructions (load constant).
	 */
	private void processLdc(Instruction instr, BitSet bytecode_offsets,
	                        ListStack<Exprent> stack, List<Exprent> exprlist,
	                        ConstantPool pool, StructBootstrapMethodsAttribute bootstrap) {
		PooledConstant cn = pool.getConstant(instr.operand(0));
		if (cn instanceof PrimitiveConstant) {
			pushEx(stack, exprlist,
			       new ConstExprent(InstructionTables.CONSTS[cn.type - CONSTANT_Integer],
			                        ((PrimitiveConstant) cn).value, bytecode_offsets));
		} else if (cn instanceof LinkConstant && cn.type == CodeConstants.CONSTANT_Dynamic) {
			LinkConstant invoke_constant = (LinkConstant) cn;

			LinkConstant bootstrapMethod = null;
			List<PooledConstant> bootstrap_arguments = null;
			if (bootstrap != null) {
				bootstrapMethod = bootstrap.getMethodReference(invoke_constant.index1);
				bootstrap_arguments = bootstrap.getMethodArguments(invoke_constant.index1);
			}

			InvocationExprent exprinv = new InvocationExprent(instr.opcode,
			                                                  invoke_constant,
			                                                  bootstrapMethod,
			                                                  bootstrap_arguments,
			                                                  stack,
			                                                  bytecode_offsets);
			if (exprinv.getDescriptor().ret.type == CodeConstants.TYPE_VOID) {
				exprlist.add(exprinv);
			} else {
				pushEx(stack, exprlist, exprinv);
			}
		} else if (cn instanceof LinkConstant) {
			// TODO: for now treat Links as Strings
			pushEx(stack, exprlist,
			       new ConstExprent(VarType.VARTYPE_STRING,
			                        ((LinkConstant) cn).elementname,
			                        bytecode_offsets));
		}
	}

	/**
	 * Processes load instructions (iload, aload, etc.).
	 */
	private void processLoad(Instruction instr, Integer bytecode_offset, BitSet bytecode_offsets,
	                         ListStack<Exprent> stack, List<Exprent> exprlist) {
		VarExprent varExprent = new VarExprent(instr.operand(0),
		                                       InstructionTables.VAR_TYPES[instr.opcode - opc_iload],
		                                       varProcessor,
		                                       bytecode_offsets);
		varProcessor.findLVT(varExprent, bytecode_offset + instr.length);
		pushEx(stack, exprlist, varExprent);
	}

	/**
	 * Processes array load instructions (iaload, aaload, etc.).
	 */
	private void processArrayLoad(Instruction instr, BitSet bytecode_offsets,
	                              ListStack<Exprent> stack, List<Exprent> exprlist) {
		Exprent index = stack.pop();
		Exprent arr = stack.pop();

		VarType vartype = null;
		switch (instr.opcode) {
			case opc_laload:
				vartype = VarType.VARTYPE_LONG;
				break;
			case opc_daload:
				vartype = VarType.VARTYPE_DOUBLE;
		}
		pushEx(stack, exprlist,
		       new ArrayExprent(arr, index, InstructionTables.ARR_TYPES[instr.opcode - opc_iaload], bytecode_offsets),
		       vartype);
	}

	/**
	 * Processes store instructions (istore, astore, etc.).
	 */
	private void processStore(Instruction instr, Integer bytecode_offset, BitSet bytecode_offsets,
	                          ListStack<Exprent> stack, List<Exprent> exprlist) {
		Exprent expr = stack.pop();
		int varindex = instr.operand(0);
		if (bytecode_offsets != null) { // TODO: Figure out why this nulls in some cases
			bytecode_offsets.set(bytecode_offset, bytecode_offset + instr.length);
		}
		VarExprent varExprent = new VarExprent(varindex,
		                                       InstructionTables.VAR_TYPES[instr.opcode - opc_istore],
		                                       varProcessor,
		                                       bytecode_offsets);
		varProcessor.findLVT(varExprent, bytecode_offset + instr.length);
		AssignmentExprent assign = new AssignmentExprent(varExprent, expr, bytecode_offsets);
		exprlist.add(assign);
	}

	/**
	 * Processes array store instructions (iastore, aastore, etc.).
	 */
	private void processArrayStore(Instruction instr, BitSet bytecode_offsets,
	                               ListStack<Exprent> stack, List<Exprent> exprlist) {
		Exprent value = stack.pop();
		Exprent index_store = stack.pop();
		Exprent arr_store = stack.pop();
		AssignmentExprent arrassign = new AssignmentExprent(
			new ArrayExprent(arr_store, index_store,
			                 InstructionTables.ARR_TYPES[instr.opcode - opc_iastore], bytecode_offsets),
			value, bytecode_offsets);
		exprlist.add(arrassign);
	}

	/**
	 * Processes iinc instruction (increment local variable).
	 */
	private void processIinc(Instruction instr, Integer bytecode_offset, BitSet bytecode_offsets,
	                         List<Exprent> exprlist) {
		VarExprent vevar = new VarExprent(instr.operand(0), VarType.VARTYPE_INT, varProcessor, bytecode_offsets);
		varProcessor.findLVT(vevar, bytecode_offset + instr.length);
		exprlist.add(new AssignmentExprent(vevar,
		                                   new FunctionExprent(instr.operand(1) < 0
		                                                       ? FunctionExprent.FUNCTION_SUB
		                                                       : FunctionExprent.FUNCTION_ADD,
		                                                       Arrays.asList(vevar.copy(),
		                                                                     new ConstExprent(VarType.VARTYPE_INT,
		                                                                                      Math.abs(instr.operand(1)),
		                                                                                      null)),
		                                                       bytecode_offsets),
		                                   bytecode_offsets));
	}

	/**
	 * Processes return and throw instructions.
	 */
	private void processReturn(Instruction instr, BitSet bytecode_offsets,
	                           ListStack<Exprent> stack, List<Exprent> exprlist) {
		exprlist.add(new ExitExprent(instr.opcode == opc_athrow
		                             ? ExitExprent.EXIT_THROW
		                             : ExitExprent.EXIT_RETURN,
		                             instr.opcode == opc_return
		                             ? null
		                             : stack.pop(),
		                             instr.opcode == opc_athrow
		                             ? null
		                             : methodDescriptor.ret,
		                             bytecode_offsets,
		                             methodDescriptor));
	}

	/**
	 * Processes getfield/getstatic instructions.
	 */
	private void processGetField(Instruction instr, BitSet bytecode_offsets,
	                             ListStack<Exprent> stack, List<Exprent> exprlist,
	                             ConstantPool pool) {
		pushEx(stack, exprlist,
		       new FieldExprent(pool.getLinkConstant(instr.operand(0)),
		                        instr.opcode == opc_getstatic ? null : stack.pop(),
		                        bytecode_offsets));
	}

	/**
	 * Processes putfield/putstatic instructions.
	 */
	private void processPutField(Instruction instr, BitSet bytecode_offsets,
	                             ListStack<Exprent> stack, List<Exprent> exprlist,
	                             ConstantPool pool) {
		Exprent valfield = stack.pop();
		Exprent exprfield = new FieldExprent(pool.getLinkConstant(instr.operand(0)),
		                                     instr.opcode == opc_putstatic ? null : stack.pop(),
		                                     bytecode_offsets);
		exprlist.add(new AssignmentExprent(exprfield, valfield, bytecode_offsets));
	}

	/**
	 * Processes invoke instructions (invokevirtual, invokestatic, etc.).
	 */
	private void processInvoke(Instruction instr, BitSet bytecode_offsets,
	                           ListStack<Exprent> stack, List<Exprent> exprlist,
	                           ConstantPool pool, StructBootstrapMethodsAttribute bootstrap) {
		if (instr.opcode != opc_invokedynamic || instr.bytecodeVersion.hasInvokeDynamic()) {
			LinkConstant invoke_constant = pool.getLinkConstant(instr.operand(0));

			LinkConstant bootstrapMethod = null;
			List<PooledConstant> bootstrap_arguments = null;
			if (instr.opcode == opc_invokedynamic && bootstrap != null) {
				bootstrapMethod = bootstrap.getMethodReference(invoke_constant.index1);
				bootstrap_arguments = bootstrap.getMethodArguments(invoke_constant.index1);
			}

			InvocationExprent exprinv = new InvocationExprent(instr.opcode,
			                                                  invoke_constant,
			                                                  bootstrapMethod,
			                                                  bootstrap_arguments,
			                                                  stack,
			                                                  bytecode_offsets);
			if (exprinv.getDescriptor().ret.type == CodeConstants.TYPE_VOID) {
				exprlist.add(exprinv);
			} else {
				pushEx(stack, exprlist, exprinv);
			}
		}
	}

	/**
	 * Processes new/anewarray/multianewarray instructions.
	 */
	private void processNew(Instruction instr, BitSet bytecode_offsets,
	                        ListStack<Exprent> stack, List<Exprent> exprlist,
	                        ConstantPool pool) {
		int dimensions = (instr.opcode == opc_new)
		                 ? 0
		                 : (instr.opcode == opc_anewarray)
		                   ? 1
		                   : instr.operand(1);
		VarType arrType = new VarType(pool.getPrimitiveConstant(instr.operand(0)).getString(), true);
		if (instr.opcode != opc_multianewarray) {
			arrType = arrType.resizeArrayDim(arrType.arrayDim + dimensions);
		}
		pushEx(stack, exprlist, new NewExprent(arrType, stack, dimensions, bytecode_offsets));
	}

	/**
	 * Processes dup2 instruction.
	 */
	private void processDup2(ListStack<Exprent> stack, List<Exprent> exprlist) {
		if (stack.getByOffset(-1).getExprType().stackSize == 2) {
			pushEx(stack, exprlist, stack.getByOffset(-1).copy());
		} else {
			pushEx(stack, exprlist, stack.getByOffset(-2).copy());
			pushEx(stack, exprlist, stack.getByOffset(-2).copy());
		}
	}

	/**
	 * Processes dup2_x1 instruction.
	 */
	private void processDup2X1(ListStack<Exprent> stack, List<Exprent> exprlist) {
		if (stack.getByOffset(-1).getExprType().stackSize == 2) {
			insertByOffsetEx(-2, stack, exprlist, -1);
		} else {
			insertByOffsetEx(-3, stack, exprlist, -2);
			insertByOffsetEx(-3, stack, exprlist, -1);
		}
	}

	/**
	 * Processes dup2_x2 instruction.
	 */
	private void processDup2X2(ListStack<Exprent> stack, List<Exprent> exprlist) {
		if (stack.getByOffset(-1).getExprType().stackSize == 2) {
			if (stack.getByOffset(-2).getExprType().stackSize == 2) {
				insertByOffsetEx(-2, stack, exprlist, -1);
			} else {
				insertByOffsetEx(-3, stack, exprlist, -1);
			}
		} else {
			if (stack.getByOffset(-3).getExprType().stackSize == 2) {
				insertByOffsetEx(-3, stack, exprlist, -2);
				insertByOffsetEx(-3, stack, exprlist, -1);
			} else {
				insertByOffsetEx(-4, stack, exprlist, -2);
				insertByOffsetEx(-4, stack, exprlist, -1);
			}
		}
	}

	/**
	 * Processes pop instruction with synthetic null check detection.
	 */
	private void processPop(ListStack<Exprent> stack, List<Exprent> exprlist,
	                        InstructionSequence seq, int instrIndex) {
		stack.pop();
		// check for synthetic getClass and requireNonNull calls added by the compiler
		// see https://stackoverflow.com/a/20130641
		if (instrIndex > 0) {
			Exprent last = exprlist.get(exprlist.size() - 1);
			// Our heuristic is checking for an assignment and the type of the assignment is an invocation.
			// This roughly corresponds to a pattern of DUP [nullcheck] POP.
			if (last.type == Exprent.EXPRENT_ASSIGNMENT
			    && ((AssignmentExprent) last).getRight().type == Exprent.EXPRENT_INVOCATION) {
				InvocationExprent invocation = (InvocationExprent) ((AssignmentExprent) last).getRight();

				// Check to make sure there's still more opcodes after this one
				if (instrIndex + 1 < seq.length()) {
					// Match either this.getClass() or Objects.requireNonNull([value]);
					if ((!invocation.isStatic()
					     && invocation.getName().equals("getClass")
					     && invocation.getStringDescriptor().equals("()Ljava/lang/Class;"))
					    // J8
					    || (invocation.isStatic()
					        && invocation.getClassname().equals("java/util/Objects")
					        && invocation.getName().equals("requireNonNull")
					        && invocation.getStringDescriptor().equals("(Ljava/lang/Object;)Ljava/lang/Object;"))) { // J9+

						// Ensure that these null checks are constant loads, LDC opcodes, null loads, or bi/sipushes.
						int nextOpc = seq.getInstr(instrIndex + 1).opcode;
						if (nextOpc >= opc_aconst_null && nextOpc <= opc_ldc2_w) {
							invocation.setSyntheticNullCheck();
						}
					}
				}
			}
		}
	}

	/**
	 * Pushes an expression onto the stack and adds the assignment to the expression list.
	 */
	private void pushEx(ListStack<Exprent> stack, List<Exprent> exprlist, Exprent exprent) {
		pushEx(stack, exprlist, exprent, null);
	}

	/**
	 * Pushes an expression onto the stack with optional type override.
	 */
	private void pushEx(ListStack<Exprent> stack, List<Exprent> exprlist, Exprent exprent, VarType vartype) {
		int varindex = VarExprent.STACK_BASE + stack.size();
		VarExprent var = new VarExprent(varindex,
		                                vartype == null ? exprent.getExprType() : vartype,
		                                varProcessor);
		var.setStack(true);

		exprlist.add(new AssignmentExprent(var, exprent, null));
		stack.push(var.copy());
	}

	/**
	 * Inserts a copy of an element at the specified offset.
	 */
	private void insertByOffsetEx(int offset, ListStack<Exprent> stack,
	                              List<Exprent> exprlist, int copyoffset) {
		int base = VarExprent.STACK_BASE + stack.size();

		LinkedList<VarExprent> lst = new LinkedList<>();

		for (int i = -1; i >= offset; i--) {
			Exprent varex = stack.pop();
			VarExprent varnew = new VarExprent(base + i + 1, varex.getExprType(), varProcessor);
			varnew.setStack(true);
			exprlist.add(new AssignmentExprent(varnew, varex, null));
			lst.add(0, (VarExprent) varnew.copy());
		}

		Exprent exprent = lst.get(lst.size() + copyoffset).copy();
		VarExprent var = new VarExprent(base + offset, exprent.getExprType(), varProcessor);
		var.setStack(true);
		exprlist.add(new AssignmentExprent(var, exprent, null));
		lst.add(0, (VarExprent) var.copy());

		for (VarExprent expr : lst) {
			stack.push(expr);
		}
	}
}
