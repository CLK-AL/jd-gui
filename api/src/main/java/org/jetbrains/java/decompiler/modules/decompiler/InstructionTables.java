// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.IfExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.MonitorExprent;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.HashMap;
import java.util.Map;

/**
 * Static lookup tables for bytecode instruction processing.
 * Contains mappings between opcodes, function types, and type arrays
 * used during decompilation.
 */
public final class InstructionTables implements CodeConstants {

	/** Maps specific opcodes to their corresponding function constants */
	private static final Map<Integer, Integer> MAP_CONSTS = new HashMap<>();

	/** Constant types for LDC instructions */
	public static final VarType[] CONSTS = {
		VarType.VARTYPE_INT,
		VarType.VARTYPE_FLOAT,
		VarType.VARTYPE_LONG,
		VarType.VARTYPE_DOUBLE,
		VarType.VARTYPE_CLASS,
		VarType.VARTYPE_STRING
	};

	/** Variable types for load/store instructions */
	public static final VarType[] VAR_TYPES = {
		VarType.VARTYPE_INT,
		VarType.VARTYPE_LONG,
		VarType.VARTYPE_FLOAT,
		VarType.VARTYPE_DOUBLE,
		VarType.VARTYPE_OBJECT
	};

	/** Array element types */
	public static final VarType[] ARR_TYPES = {
		VarType.VARTYPE_INT,
		VarType.VARTYPE_LONG,
		VarType.VARTYPE_FLOAT,
		VarType.VARTYPE_DOUBLE,
		VarType.VARTYPE_OBJECT,
		VarType.VARTYPE_BOOLEAN,
		VarType.VARTYPE_CHAR,
		VarType.VARTYPE_SHORT
	};

	/** Arithmetic operation functions (add, sub, mul, div, rem) */
	public static final int[] FUNC1 = {
		FunctionExprent.FUNCTION_ADD,
		FunctionExprent.FUNCTION_SUB,
		FunctionExprent.FUNCTION_MUL,
		FunctionExprent.FUNCTION_DIV,
		FunctionExprent.FUNCTION_REM
	};

	/** Bitwise/shift operation functions (shl, shr, ushr, and, or, xor) */
	public static final int[] FUNC2 = {
		FunctionExprent.FUNCTION_SHL,
		FunctionExprent.FUNCTION_SHR,
		FunctionExprent.FUNCTION_USHR,
		FunctionExprent.FUNCTION_AND,
		FunctionExprent.FUNCTION_OR,
		FunctionExprent.FUNCTION_XOR
	};

	/** Type conversion functions (i2l, i2f, i2d, etc.) */
	public static final int[] FUNC3 = {
		FunctionExprent.FUNCTION_I2L,
		FunctionExprent.FUNCTION_I2F,
		FunctionExprent.FUNCTION_I2D,
		FunctionExprent.FUNCTION_L2I,
		FunctionExprent.FUNCTION_L2F,
		FunctionExprent.FUNCTION_L2D,
		FunctionExprent.FUNCTION_F2I,
		FunctionExprent.FUNCTION_F2L,
		FunctionExprent.FUNCTION_F2D,
		FunctionExprent.FUNCTION_D2I,
		FunctionExprent.FUNCTION_D2L,
		FunctionExprent.FUNCTION_D2F,
		FunctionExprent.FUNCTION_I2B,
		FunctionExprent.FUNCTION_I2C,
		FunctionExprent.FUNCTION_I2S
	};

	/** Comparison functions (lcmp, fcmpl, fcmpg, dcmpl, dcmpg) */
	public static final int[] FUNC4 = {
		FunctionExprent.FUNCTION_LCMP,
		FunctionExprent.FUNCTION_FCMPL,
		FunctionExprent.FUNCTION_FCMPG,
		FunctionExprent.FUNCTION_DCMPL,
		FunctionExprent.FUNCTION_DCMPG
	};

	/** Single-operand comparison types (ifeq, ifne, iflt, ifge, ifgt, ifle) */
	public static final int[] FUNC5 = {
		IfExprent.IF_EQ,
		IfExprent.IF_NE,
		IfExprent.IF_LT,
		IfExprent.IF_GE,
		IfExprent.IF_GT,
		IfExprent.IF_LE
	};

	/** Two-operand comparison types (if_icmpeq, etc., if_acmpeq, etc.) */
	public static final int[] FUNC6 = {
		IfExprent.IF_ICMPEQ,
		IfExprent.IF_ICMPNE,
		IfExprent.IF_ICMPLT,
		IfExprent.IF_ICMPGE,
		IfExprent.IF_ICMPGT,
		IfExprent.IF_ICMPLE,
		IfExprent.IF_ACMPEQ,
		IfExprent.IF_ACMPNE
	};

	/** Null comparison types (ifnull, ifnonnull) */
	public static final int[] FUNC7 = {
		IfExprent.IF_NULL,
		IfExprent.IF_NONNULL
	};

	/** Monitor operation types (monitorenter, monitorexit) */
	public static final int[] FUNC8 = {
		MonitorExprent.MONITOR_ENTER,
		MonitorExprent.MONITOR_EXIT
	};

	/** Array type IDs for newarray instruction */
	public static final int[] ARR_TYPE_IDS = {
		CodeConstants.TYPE_BOOLEAN,
		CodeConstants.TYPE_CHAR,
		CodeConstants.TYPE_FLOAT,
		CodeConstants.TYPE_DOUBLE,
		CodeConstants.TYPE_BYTE,
		CodeConstants.TYPE_SHORT,
		CodeConstants.TYPE_INT,
		CodeConstants.TYPE_LONG
	};

	/** Negated if conditions - used for inverting branch conditions */
	public static final int[] NEG_IFS = {
		IfExprent.IF_NE,
		IfExprent.IF_EQ,
		IfExprent.IF_GE,
		IfExprent.IF_LT,
		IfExprent.IF_LE,
		IfExprent.IF_GT,
		IfExprent.IF_NONNULL,
		IfExprent.IF_NULL,
		IfExprent.IF_ICMPNE,
		IfExprent.IF_ICMPEQ,
		IfExprent.IF_ICMPGE,
		IfExprent.IF_ICMPLT,
		IfExprent.IF_ICMPLE,
		IfExprent.IF_ICMPGT,
		IfExprent.IF_ACMPNE,
		IfExprent.IF_ACMPEQ
	};

	/** Primitive type names indexed by type code */
	public static final String[] TYPE_NAMES = {
		"byte",
		"char",
		"double",
		"float",
		"int",
		"long",
		"short",
		"boolean"
	};

	static {
		MAP_CONSTS.put(opc_arraylength, FunctionExprent.FUNCTION_ARRAY_LENGTH);
		MAP_CONSTS.put(opc_checkcast, FunctionExprent.FUNCTION_CAST);
		MAP_CONSTS.put(opc_instanceof, FunctionExprent.FUNCTION_INSTANCEOF);
	}

	private InstructionTables() {
		// Utility class - prevent instantiation
	}

	/**
	 * Gets the function constant for a given opcode.
	 *
	 * @param opcode the bytecode opcode
	 * @return the function constant, or null if not mapped
	 */
	public static Integer getMapConst(int opcode) {
		return MAP_CONSTS.get(opcode);
	}
}
