// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.List;

/**
 * Utility class for building and manipulating expressions during decompilation.
 * Contains methods for expression casting, default value creation, and
 * converting expression lists to Java source code.
 */
public final class ExpressionBuilder {

	private ExpressionBuilder() {
		// Utility class - prevent instantiation
	}

	/**
	 * Creates expression data for a variable expression.
	 *
	 * @param var the variable expression
	 * @return primitive expression list containing the variable assignment
	 */
	public static PrimitiveExprsList getExpressionData(VarExprent var) {
		PrimitiveExprsList prlst = new PrimitiveExprsList();
		VarExprent vartmp = new VarExprent(VarExprent.STACK_BASE,
		                                   var.getExprType(),
		                                   var.getProcessor());
		vartmp.setStack(true);

		prlst.getLstExprents()
		     .add(new AssignmentExprent(vartmp,
		                                var.copy(),
		                                null));
		prlst.getStack()
		     .push(vartmp.copy());
		return prlst;
	}

	/**
	 * Checks if an expression should end with a semicolon.
	 *
	 * @param expr the expression to check
	 * @return true if the expression should end with a semicolon
	 */
	public static boolean endsWithSemicolon(Exprent expr) {
		int type = expr.type;
		return !(type == Exprent.EXPRENT_SWITCH_HEAD
		         || type == Exprent.EXPRENT_MONITOR
		         || type == Exprent.EXPRENT_IF
		         || (type == Exprent.EXPRENT_VAR && ((VarExprent) expr).isClassDef()));
	}

	/**
	 * Gets a casted expression with default parameters.
	 */
	public static boolean getCastedExprent(Exprent exprent,
	                                       VarType leftType,
	                                       TextBuffer buffer,
	                                       int indent,
	                                       boolean castNull) {
		return getCastedExprent(exprent, leftType, buffer, indent,
		                        castNull, false, false, false);
	}

	/**
	 * Gets a casted expression with full control over casting behavior.
	 *
	 * @param exprent       the expression to potentially cast
	 * @param leftType      the target type for casting
	 * @param buffer        the text buffer to append to
	 * @param indent        the indentation level
	 * @param castNull      whether to cast null values
	 * @param castAlways    whether to always cast regardless of type compatibility
	 * @param castNarrowing whether to cast narrowing integer conversions
	 * @param unbox         whether to unbox wrapper types
	 * @return true if a cast was applied
	 */
	public static boolean getCastedExprent(Exprent exprent,
	                                       VarType leftType,
	                                       TextBuffer buffer,
	                                       int indent,
	                                       boolean castNull,
	                                       boolean castAlways,
	                                       boolean castNarrowing,
	                                       boolean unbox) {

		if (unbox) {
			// "unbox" invocation parameters, e.g. 'byteSet.add((byte)123)' or 'new ShortContainer((short)813)'
			if (exprent.type == Exprent.EXPRENT_INVOCATION) {
				InvocationExprent invocationExprent = (InvocationExprent) exprent;
				if (invocationExprent.isBoxingCall() && !invocationExprent.shouldForceBoxing()) {
					exprent = invocationExprent.getLstParameters().get(0);
					int paramType = invocationExprent.getDescriptor().params[0].type;
					if (exprent.type == Exprent.EXPRENT_CONST
					    && ((ConstExprent) exprent).getConstType().type != paramType) {
						leftType = new VarType(paramType);
					}
				}
			}
		}

		VarType rightType = exprent.getInferredExprType(leftType);

		boolean cast = castAlways
		               || (!leftType.isSuperset(rightType)
		                   && (rightType.equals(VarType.VARTYPE_OBJECT)
		                       || leftType.type != CodeConstants.TYPE_OBJECT))
		               || (castNull
		                   && rightType.type == CodeConstants.TYPE_NULL
		                   && !TypeConverter.UNDEFINED_TYPE_STRING.equals(TypeConverter.getTypeName(leftType)))
		               || (castNarrowing && isIntConstant(exprent) && isNarrowedIntType(leftType));

		boolean castLambda = !cast
		                     && exprent.type == Exprent.EXPRENT_NEW
		                     && !leftType.equals(rightType)
		                     && lambdaNeedsCast(leftType, (NewExprent) exprent);

		boolean quote = cast && exprent.getPrecedence() >= FunctionExprent.getPrecedence(FunctionExprent.FUNCTION_CAST);

		// cast instead to 'byte' / 'short' when int constant is used as a value for 'Byte' / 'Short'
		if (castNarrowing && exprent.type == Exprent.EXPRENT_CONST && !((ConstExprent) exprent).isNull()) {
			if (leftType.equals(VarType.VARTYPE_BYTE_OBJ)) {
				leftType = VarType.VARTYPE_BYTE;
			} else if (leftType.equals(VarType.VARTYPE_SHORT_OBJ)) {
				leftType = VarType.VARTYPE_SHORT;
			}
		}

		if (cast) {
			buffer.append('(')
			      .append(TypeConverter.getCastTypeName(leftType))
			      .append(')');
		}

		if (castLambda) {
			buffer.append('(')
			      .append(TypeConverter.getCastTypeName(rightType))
			      .append(')');
		}

		if (quote) {
			buffer.append('(');
		}

		if (exprent.type == Exprent.EXPRENT_CONST) {
			((ConstExprent) exprent).adjustConstType(leftType);
		}

		buffer.append(exprent.toJava(indent));

		if (quote) {
			buffer.append(')');
		}

		return cast;
	}

	/**
	 * Checks if an expression is an integer constant.
	 */
	private static boolean isIntConstant(Exprent exprent) {
		if (exprent.type == Exprent.EXPRENT_CONST) {
			switch (((ConstExprent) exprent).getConstType().type) {
				case CodeConstants.TYPE_BYTE:
				case CodeConstants.TYPE_BYTECHAR:
				case CodeConstants.TYPE_SHORT:
				case CodeConstants.TYPE_SHORTCHAR:
				case CodeConstants.TYPE_INT:
					return true;
			}
		}
		return false;
	}

	/**
	 * Checks if a type is a narrowed integer type (byte, short, or their wrappers).
	 */
	private static boolean isNarrowedIntType(VarType type) {
		return VarType.VARTYPE_INT.isStrictSuperset(type)
		       || type.equals(VarType.VARTYPE_BYTE_OBJ)
		       || type.equals(VarType.VARTYPE_SHORT_OBJ);
	}

	/**
	 * Checks if a lambda expression needs a cast to the target type.
	 */
	private static boolean lambdaNeedsCast(VarType left, NewExprent exprent) {
		if (exprent.isLambda() && !exprent.isMethodReference()) {
			StructClass cls = DecompilerContext.getStructContext().getClass(left.value);
			return cls == null || cls.getMethod(exprent.getLambdaMethodKey()) == null;
		}
		return false;
	}

	/**
	 * Creates a default value expression for an array element type.
	 *
	 * @param arrType the array element type
	 * @return a constant expression with the default value for the type
	 */
	public static ConstExprent getDefaultArrayValue(VarType arrType) {
		ConstExprent defaultVal;
		if (arrType.type == CodeConstants.TYPE_OBJECT || arrType.arrayDim > 0) {
			defaultVal = new ConstExprent(VarType.VARTYPE_NULL, null, null);
		} else if (arrType.type == CodeConstants.TYPE_FLOAT) {
			defaultVal = new ConstExprent(VarType.VARTYPE_FLOAT, 0f, null);
		} else if (arrType.type == CodeConstants.TYPE_LONG) {
			defaultVal = new ConstExprent(VarType.VARTYPE_LONG, 0L, null);
		} else if (arrType.type == CodeConstants.TYPE_DOUBLE) {
			defaultVal = new ConstExprent(VarType.VARTYPE_DOUBLE, 0d, null);
		} else { // integer types
			defaultVal = new ConstExprent(0, true, null);
		}
		return defaultVal;
	}

	/**
	 * Converts a list of expressions to Java source code.
	 *
	 * @param lst    the list of expressions
	 * @param indent the indentation level
	 * @return the Java source code as a TextBuffer
	 */
	public static TextBuffer listToJava(List<? extends Exprent> lst, int indent) {
		if (lst == null || lst.isEmpty()) {
			return new TextBuffer();
		}

		TextBuffer buf = new TextBuffer();
		lst = Exprent.sortIndexed(lst);

		for (Exprent expr : lst) {
			if (buf.length() > 0
			    && expr.type == Exprent.EXPRENT_VAR
			    && ((VarExprent) expr).isClassDef()) {
				// separates local class definition from previous statements
				buf.appendLineSeparator();
			}

			expr.getInferredExprType(null);

			TextBuffer content = expr.toJava(indent);

			if (content.length() > 0) {
				if (expr.type != Exprent.EXPRENT_VAR || !((VarExprent) expr).isClassDef()) {
					buf.appendIndent(indent);
				}
				buf.append(content);
				if (expr.type == Exprent.EXPRENT_MONITOR
				    && ((MonitorExprent) expr).getMonType() == MonitorExprent.MONITOR_ENTER) {
					buf.append("{}"); // empty synchronized block
				}
				if (endsWithSemicolon(expr)) {
					buf.append(";");
				}
				buf.appendLineSeparator();
			}
		}

		return buf;
	}

	/**
	 * Wraps a statement with jump handling (break/continue).
	 *
	 * @param stat      the statement to wrap
	 * @param indent    the indentation level
	 * @param semicolon whether to add a semicolon if the buffer is empty
	 * @return the Java source code with jump handling
	 */
	public static TextBuffer jmpWrapper(Statement stat, int indent, boolean semicolon) {
		TextBuffer buf = stat.toJava(indent);

		List<StatEdge> lstSuccs = stat.getSuccessorEdges(Statement.STATEDGE_DIRECT_ALL);
		if (lstSuccs.size() == 1) {
			StatEdge edge = lstSuccs.get(0);
			if (edge.getType() != StatEdge.TYPE_REGULAR
			    && edge.explicit
			    && edge.getDestination().type != Statement.TYPE_DUMMYEXIT) {
				buf.appendIndent(indent);

				switch (edge.getType()) {
					case StatEdge.TYPE_BREAK:
						addDeletedGotoInstructionMapping(stat, buf);
						buf.append("break");
						break;
					case StatEdge.TYPE_CONTINUE:
						addDeletedGotoInstructionMapping(stat, buf);
						buf.append("continue");
				}

				if (edge.labeled) {
					buf.append(" label").append(edge.closure.id.toString());
				}
				buf.append(";").appendLineSeparator();
			}
		}

		if (buf.length() == 0 && semicolon) {
			buf.appendIndent(indent).append(";").appendLineSeparator();
		}

		return buf;
	}

	/**
	 * Adds bytecode mapping for deleted goto instructions.
	 */
	private static void addDeletedGotoInstructionMapping(Statement stat, TextBuffer buffer) {
		if (stat instanceof BasicBlockStatement) {
			BasicBlock block = ((BasicBlockStatement) stat).getBlock();
			List<Integer> offsets = block.getInstrOldOffsets();
			if (!offsets.isEmpty() && offsets.size() > block.getSeq().length()) {
				// some instructions have been deleted, but we still have offsets
				buffer.addBytecodeMapping(offsets.get(offsets.size() - 1)); // add the last offset
			}
		}
	}
}
