/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.parser.antlr;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.jd.gui.api.model.Container;

import java.util.HashMap;
import java.util.List;

/**
 * Base ANTLR listener for parsing Java source files.
 * Supports Java 8 through Java 21 features including:
 * - Lambdas and method references (Java 8)
 * - Modules (Java 9)
 * - Local variable type inference - var (Java 10)
 * - Switch expressions (Java 14)
 * - Text blocks (Java 15)
 * - Records (Java 16)
 * - Sealed classes (Java 17)
 * - Pattern matching (Java 16-21)
 * - Record patterns (Java 21)
 * - Unnamed patterns and variables (Java 21)
 */
public abstract class AbstractJavaListener
				extends JavaBaseListener {
	protected Container.Entry         entry;
	protected String                  packageName            = "";
	protected HashMap<String, String> nameToInternalTypeName = new HashMap<>();
	protected StringBuilder           sb                     = new StringBuilder();
	protected HashMap<String, String> typeNameCache          = new HashMap<>();

	public AbstractJavaListener(Container.Entry entry) {
		this.entry = entry;
	}

	public void enterPackageDeclaration(JavaParser.PackageDeclarationContext ctx) {
		packageName = concatIdentifiers(ctx.qualifiedName()
		                                   .Identifier());
	}

	public void enterImportDeclaration(JavaParser.ImportDeclarationContext ctx) {
		List<TerminalNode> identifiers = ctx.qualifiedName()
		                                    .Identifier();
		int size = identifiers.size();

		if (size > 1) {
			nameToInternalTypeName.put(identifiers.get(size - 1)
			                                      .getText(),
			                           concatIdentifiers(identifiers));
		}
	}

	protected String concatIdentifiers(List<TerminalNode> identifiers) {
		switch (identifiers.size()) {
			case 0:
				return "";
			case 1:
				return identifiers.get(0)
				                  .getText();
			default:
				sb.setLength(0);

				for (TerminalNode identifier : identifiers) {
					sb.append(identifier.getText())
					  .append('/');
				}

				// Remove last separator
				sb.setLength(sb.length() - 1);

				return sb.toString();
		}
	}

	protected String resolveInternalTypeName(List<TerminalNode> identifiers) {
		switch (identifiers.size()) {
			case 0:
				return null;

			case 1:
				// Search in cache
				String name = identifiers.get(0)
				                         .getText();
				String qualifiedName = typeNameCache.get(name);

				if (qualifiedName != null) {
					return qualifiedName;
				}

				// Search in imports
				String imp = nameToInternalTypeName.get(name);

				if (imp != null) {
					// Import found
					return imp;
				}

				// Search type in same package
				String prefix = name + '.';

				if (entry.getPath()
				         .indexOf('/') != -1) {
					// Not in root package
					Container.Entry parent = entry.getParent();
					int packageLength = parent.getPath()
					                          .length() + 1;

					for (Container.Entry child : parent.getChildren()) {
						if (!child.isDirectory() && child.getPath()
						                                 .substring(packageLength)
						                                 .startsWith(prefix)) {
							qualifiedName = packageName + '/' + name;
							typeNameCache.put(name,
							                  qualifiedName);
							return qualifiedName;
						}
					}
				}

				// Search type in root package
				for (Container.Entry child : entry.getContainer()
				                                  .getRoot()
				                                  .getChildren()) {
					if (!child.isDirectory() && child.getPath()
					                                 .startsWith(prefix)) {
						typeNameCache.put(name,
						                  name);
						return name;
					}
				}

				// Search type in 'java.lang'
				try {
					if (Class.forName("java.lang." + name) != null) {
						qualifiedName = "java/lang/" + name;
						typeNameCache.put(name,
						                  qualifiedName);
						return qualifiedName;
					}
				} catch (ClassNotFoundException ignore) {
					// Ignore class loading error
				}

				// Type not found
				qualifiedName = "*/" + name;
				typeNameCache.put(name,
				                  qualifiedName);
				return qualifiedName;

			default:
				// Qualified type name -> Nothing to do
				return concatIdentifiers(identifiers);
		}
	}

	/**
	 * Creates a type descriptor from a type context.
	 * Handles all Java types including:
	 * - Primitive types
	 * - Reference types
	 * - Array types
	 * - Generic types
	 * - var (local variable type inference - Java 10+)
	 */
	protected String createDescriptor(JavaParser.TypeContext typeContext,
	                                  int dimension) {
		if (typeContext == null) {
			return "V";
		} else {
			dimension += countDimension(typeContext.children);
			JavaParser.PrimitiveTypeContext primitive = typeContext.primitiveType();
			String                          name;

			if (primitive == null) {
				JavaParser.ClassOrInterfaceTypeContext type = typeContext.classOrInterfaceType();
				if (type == null) {
					// Could be an annotation or other type
					return "Ljava/lang/Object;";
				}
				List<JavaParser.TypeArgumentsContext>  typeArgumentsContexts = type.typeArguments();

				if (typeArgumentsContexts != null && typeArgumentsContexts.size() == 1) {
					JavaParser.TypeArgumentsContext      typeArgumentsContext = typeArgumentsContexts.get(0);
					List<JavaParser.TypeArgumentContext> typeArguments        = typeArgumentsContext.typeArgument();
				} else if (typeArgumentsContexts != null && typeArgumentsContexts.size() > 1) {
					// Multiple type argument contexts (nested generics)
					// Just continue with the base type
				}

				name = "L" + resolveInternalTypeName(type.Identifier()) + ";";
			} else {
				// Search primitive
				name = getPrimitiveDescriptor(primitive.getText());
			}

			return prependArrayDimension(name, dimension);
		}
	}

	/**
	 * Creates a descriptor for a type that could be 'var' (Java 10+).
	 * When 'var' is used, we can't determine the actual type statically,
	 * so we return Object as a placeholder.
	 */
	protected String createDescriptorWithVar(JavaParser.TypeContext typeContext,
	                                         boolean isVar,
	                                         int dimension) {
		if (isVar) {
			// 'var' keyword - type inference, return Object as placeholder
			return prependArrayDimension("Ljava/lang/Object;", dimension);
		}
		return createDescriptor(typeContext, dimension);
	}

	/**
	 * Gets the JVM descriptor for a primitive type.
	 */
	protected String getPrimitiveDescriptor(String primitiveType) {
		switch (primitiveType) {
			case "boolean":
				return "Z";
			case "byte":
				return "B";
			case "char":
				return "C";
			case "double":
				return "D";
			case "float":
				return "F";
			case "int":
				return "I";
			case "long":
				return "J";
			case "short":
				return "S";
			case "void":
				return "V";
			default:
				throw new RuntimeException("UNEXPECTED PRIMITIVE: " + primitiveType);
		}
	}

	/**
	 * Prepends array dimension markers to a type descriptor.
	 */
	protected String prependArrayDimension(String name, int dimension) {
		switch (dimension) {
			case 0:
				return name;
			case 1:
				return "[" + name;
			case 2:
				return "[[" + name;
			default:
				return new String(new char[dimension]).replace('\0', '[') + name;
		}
	}

	protected int countDimension(List<ParseTree> children) {
		int dimension = 0;

		if (children != null) {
			for (ParseTree child : children) {
				if (child instanceof TerminalNodeImpl) {
					if (((TerminalNodeImpl) child).getSymbol()
					                              .getType() == JavaParser.LBRACK) {
						dimension++;
					}
				}
			}
		}

		return dimension;
	}

	/**
	 * Checks if a variable declarator ID represents an unnamed variable (Java 21+).
	 * Unnamed variables use the underscore '_' identifier.
	 */
	protected boolean isUnnamedVariable(JavaParser.VariableDeclaratorIdContext ctx) {
		if (ctx == null) return false;
		// Check if it's the UNDERSCORE token
		return ctx.UNDERSCORE() != null;
	}

	/**
	 * Gets the identifier name from a variable declarator ID.
	 * Returns "_" for unnamed variables (Java 21+).
	 */
	protected String getVariableName(JavaParser.VariableDeclaratorIdContext ctx) {
		if (ctx == null) return "";
		if (ctx.UNDERSCORE() != null) {
			return "_";
		}
		TerminalNode identifier = ctx.Identifier();
		return identifier != null ? identifier.getText() : "";
	}
}
