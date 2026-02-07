// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructBootstrapMethodsAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper class for writing error information and bytecode dumps to decompiled output.
 */
public final class ErrorWriter {

	private static final Set<String> ERROR_DUMP_STOP_POINTS = new HashSet<>(Arrays.asList(
		"Fernflower.decompileContext",
		"MethodProcessorRunnable.codeToJava",
		"ClassWriter.methodToJava",
		"ClassWriter.methodLambdaToJava",
		"ClassWriter.classLambdaToJava"
	));

	private ErrorWriter() {
		// Utility class
	}

	/**
	 * Dumps error information for a method that couldn't be decompiled.
	 *
	 * @param buffer  the text buffer to append to
	 * @param wrapper the method wrapper containing the error
	 * @param indent  the indentation level
	 */
	public static void dumpError(TextBuffer buffer, MethodWrapper wrapper, int indent) {
		List<String> lines = new ArrayList<>();
		lines.add("$FF: Couldn't be decompiled");
		boolean exceptions = DecompilerContext.getOption(IFernflowerPreferences.DUMP_EXCEPTION_ON_ERROR);
		boolean bytecode = DecompilerContext.getOption(IFernflowerPreferences.DUMP_BYTECODE_ON_ERROR);
		if (exceptions) {
			lines.addAll(getErrorComment());
			collectErrorLines(wrapper.decompileError, lines);
			if (bytecode) {
				lines.add("");
			}
		}
		if (bytecode) {
			try {
				lines.add("Bytecode:");
				collectBytecode(wrapper, lines);
			} catch (Exception e) {
				lines.add("Error collecting bytecode:");
				collectErrorLines(e, lines);
			} finally {
				wrapper.methodStruct.releaseResources();
			}
		}
		for (String line : lines) {
			buffer.appendIndent(indent);
			buffer.append("//");
			if (!line.isEmpty()) {
				buffer.append(' ').append(line);
			}
			buffer.appendLineSeparator();
		}
	}

	/**
	 * Collects stack trace lines from an error.
	 *
	 * @param error the throwable to collect lines from
	 * @param lines the list to add lines to
	 */
	public static void collectErrorLines(Throwable error, List<String> lines) {
		StackTraceElement[] stack = error.getStackTrace();
		List<StackTraceElement> filteredStack = new ArrayList<>();
		boolean hasSeenOwnClass = false;
		for (StackTraceElement e : stack) {
			String className = e.getClassName();
			boolean isOwnClass = className.startsWith("org.jetbrains.java.decompiler");
			if (isOwnClass) {
				hasSeenOwnClass = true;
			} else if (hasSeenOwnClass) {
				break;
			}
			filteredStack.add(e);
			if (isOwnClass) {
				String simpleName = className.substring(className.lastIndexOf('.') + 1);
				if (ERROR_DUMP_STOP_POINTS.contains(simpleName + "." + e.getMethodName())) {
					break;
				}
			}
		}
		if (filteredStack.isEmpty()) {
			return;
		}
		lines.add(error.toString());
		for (StackTraceElement e : filteredStack) {
			lines.add("  at " + e);
		}
		Throwable cause = error.getCause();
		if (cause != null) {
			List<String> causeLines = new ArrayList<>();
			collectErrorLines(cause, causeLines);
			if (!causeLines.isEmpty()) {
				lines.add("Caused by: " + causeLines.get(0));
				lines.addAll(causeLines.subList(1, causeLines.size()));
			}
		}
	}

	/**
	 * Collects bytecode instructions for a method.
	 *
	 * @param wrapper the method wrapper
	 * @param lines   the list to add bytecode lines to
	 * @throws IOException if bytecode cannot be read
	 */
	public static void collectBytecode(MethodWrapper wrapper, List<String> lines) throws IOException {
		ClassNode classNode = (ClassNode) DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
		StructMethod method = wrapper.methodStruct;
		InstructionSequence instructions = method.getInstructionSequence();
		if (instructions == null) {
			method.expandData(classNode.classStruct);
			instructions = method.getInstructionSequence();
		}
		int lastOffset = instructions.getOffset(instructions.length() - 1);
		int digits = 8 - Integer.numberOfLeadingZeros(lastOffset) / 4;
		ConstantPool pool = classNode.classStruct.getPool();
		StructBootstrapMethodsAttribute bootstrap = classNode.classStruct.getAttribute(
			StructGeneralAttribute.ATTRIBUTE_BOOTSTRAP_METHODS);

		for (int idx = 0; idx < instructions.length(); idx++) {
			int offset = instructions.getOffset(idx);
			Instruction instr = instructions.getInstr(idx);
			StringBuilder sb = new StringBuilder();
			String offHex = Integer.toHexString(offset);
			for (int i = offHex.length(); i < digits; i++) {
				sb.append('0');
			}
			sb.append(offHex).append(": ");
			if (instr.wide) {
				sb.append("wide ");
			}
			sb.append(TextUtil.getInstructionName(instr.opcode));
			switch (instr.group) {
				case CodeConstants.GROUP_INVOCATION: {
					sb.append(' ');
					if (instr.opcode == CodeConstants.opc_invokedynamic && bootstrap != null) {
						appendBootstrapCall(sb, pool.getLinkConstant(instr.operand(0)), bootstrap);
					} else {
						appendConstant(sb, pool.getConstant(instr.operand(0)));
					}
					for (int i = 1; i < instr.operandsCount(); i++) {
						sb.append(' ').append(instr.operand(i));
					}
					break;
				}
				case CodeConstants.GROUP_FIELDACCESS: {
					sb.append(' ');
					appendConstant(sb, pool.getConstant(instr.operand(0)));
					break;
				}
				case CodeConstants.GROUP_JUMP: {
					sb.append(' ');
					int dest = offset + instr.operand(0);
					String destHex = Integer.toHexString(dest);
					for (int i = destHex.length(); i < digits; i++) {
						sb.append('0');
					}
					sb.append(destHex);
					break;
				}
				default: {
					switch (instr.opcode) {
						case CodeConstants.opc_new:
						case CodeConstants.opc_checkcast:
						case CodeConstants.opc_instanceof:
						case CodeConstants.opc_ldc:
						case CodeConstants.opc_ldc_w:
						case CodeConstants.opc_ldc2_w: {
							sb.append(' ');
							PooledConstant constant = pool.getConstant(instr.operand(0));
							if (constant.type == CodeConstants.CONSTANT_Dynamic && bootstrap != null) {
								appendBootstrapCall(sb, (LinkConstant) constant, bootstrap);
							} else {
								appendConstant(sb, constant);
							}
							break;
						}
						default: {
							for (int i = 0; i < instr.operandsCount(); i++) {
								sb.append(' ').append(instr.operand(i));
							}
						}
					}
				}
			}
			lines.add(sb.toString());
		}
	}

	/**
	 * Appends a bootstrap method call to the string builder.
	 */
	private static void appendBootstrapCall(StringBuilder sb, LinkConstant target,
	                                         StructBootstrapMethodsAttribute bootstrap) {
		sb.append(target.elementname).append(' ').append(target.descriptor);

		LinkConstant bsm = bootstrap.getMethodReference(target.index1);
		List<PooledConstant> bsmArgs = bootstrap.getMethodArguments(target.index1);

		sb.append(" bsm=");
		appendConstant(sb, bsm);
		sb.append(" args=[ ");
		boolean first = true;
		for (PooledConstant arg : bsmArgs) {
			if (!first) {
				sb.append(", ");
			}
			first = false;
			appendConstant(sb, arg);
		}
		sb.append(" ]");
	}

	/**
	 * Appends a constant value to the string builder.
	 */
	private static void appendConstant(StringBuilder sb, PooledConstant constant) {
		if (constant == null) {
			sb.append("<null constant>");
			return;
		}
		if (constant instanceof PrimitiveConstant) {
			PrimitiveConstant prim = ((PrimitiveConstant) constant);
			Object value = prim.value;
			String stringValue = String.valueOf(value);
			if (prim.type == CodeConstants.CONSTANT_Class) {
				sb.append(stringValue);
			} else if (prim.type == CodeConstants.CONSTANT_String) {
				sb.append('"').append(ConstExprent.convertStringToJava(stringValue, false)).append('"');
			} else {
				sb.append(stringValue);
			}
		} else if (constant instanceof LinkConstant) {
			LinkConstant linkConstant = (LinkConstant) constant;
			sb.append(linkConstant.classname).append('.').append(linkConstant.elementname)
			  .append(' ').append(linkConstant.descriptor);
		}
	}

	/**
	 * Gets the error comment lines from preferences.
	 *
	 * @return list of error comment lines
	 */
	public static List<String> getErrorComment() {
		return Arrays.stream(((String) DecompilerContext.getProperty(IFernflowerPreferences.ERROR_MESSAGE)).split("\n"))
		             .filter(s -> !s.isEmpty())
		             .collect(Collectors.toList());
	}
}
