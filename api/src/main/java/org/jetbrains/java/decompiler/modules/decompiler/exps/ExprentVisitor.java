/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
 * found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.exps;

/**
 * Visitor interface for the Exprent hierarchy.
 * <p>
 * This interface follows the Visitor design pattern to allow operations on Exprent
 * objects without modifying their classes. It eliminates the need for instanceof
 * checks and type casting when processing different Exprent types.
 * <p>
 * Usage example:
 * <pre>{@code
 * ExprentVisitor<String> printer = new AbstractExprentVisitor<String>() {
 *     @Override
 *     public String visitInvocation(InvocationExprent exprent) {
 *         return "Method call: " + exprent.getName();
 *     }
 *
 *     @Override
 *     public String visitVar(VarExprent exprent) {
 *         return "Variable: " + exprent.getIndex();
 *     }
 * };
 *
 * String result = someExprent.accept(printer);
 * }</pre>
 *
 * @param <T> the return type of the visit operations
 * @see AbstractExprentVisitor
 * @see Exprent#accept(ExprentVisitor)
 */
public interface ExprentVisitor<T> {

    /**
     * Visits an InvocationExprent (method call).
     *
     * @param exprent the invocation expression to visit
     * @return the result of visiting
     */
    T visitInvocation(InvocationExprent exprent);

    /**
     * Visits a VarExprent (variable reference).
     *
     * @param exprent the variable expression to visit
     * @return the result of visiting
     */
    T visitVar(VarExprent exprent);

    /**
     * Visits a ConstExprent (constant value).
     *
     * @param exprent the constant expression to visit
     * @return the result of visiting
     */
    T visitConst(ConstExprent exprent);

    /**
     * Visits a FunctionExprent (operators and built-in functions).
     *
     * @param exprent the function expression to visit
     * @return the result of visiting
     */
    T visitFunction(FunctionExprent exprent);

    /**
     * Visits a NewExprent (object/array creation).
     *
     * @param exprent the new expression to visit
     * @return the result of visiting
     */
    T visitNew(NewExprent exprent);

    /**
     * Visits a FieldExprent (field access).
     *
     * @param exprent the field expression to visit
     * @return the result of visiting
     */
    T visitField(FieldExprent exprent);

    /**
     * Visits an AssignmentExprent (assignment operation).
     *
     * @param exprent the assignment expression to visit
     * @return the result of visiting
     */
    T visitAssignment(AssignmentExprent exprent);

    /**
     * Visits an ArrayExprent (array access).
     *
     * @param exprent the array expression to visit
     * @return the result of visiting
     */
    T visitArray(ArrayExprent exprent);

    /**
     * Visits a SwitchExprent (switch expression - Java 14+).
     *
     * @param exprent the switch expression to visit
     * @return the result of visiting
     */
    T visitSwitch(SwitchExprent exprent);

    /**
     * Visits a SwitchHeadExprent (switch statement header).
     *
     * @param exprent the switch head expression to visit
     * @return the result of visiting
     */
    T visitSwitchHead(SwitchHeadExprent exprent);

    /**
     * Visits a MonitorExprent (synchronized block enter/exit).
     *
     * @param exprent the monitor expression to visit
     * @return the result of visiting
     */
    T visitMonitor(MonitorExprent exprent);

    /**
     * Visits an ExitExprent (return/throw statement).
     *
     * @param exprent the exit expression to visit
     * @return the result of visiting
     */
    T visitExit(ExitExprent exprent);

    /**
     * Visits an AnnotationExprent (annotation).
     *
     * @param exprent the annotation expression to visit
     * @return the result of visiting
     */
    T visitAnnotation(AnnotationExprent exprent);

    /**
     * Visits an AssertExprent (assert statement).
     *
     * @param exprent the assert expression to visit
     * @return the result of visiting
     */
    T visitAssert(AssertExprent exprent);

    /**
     * Visits an IfExprent (conditional expression in control flow).
     *
     * @param exprent the if expression to visit
     * @return the result of visiting
     */
    T visitIf(IfExprent exprent);

    /**
     * Visits a YieldExprent (yield statement in switch expressions).
     *
     * @param exprent the yield expression to visit
     * @return the result of visiting
     */
    T visitYield(YieldExprent exprent);

    /**
     * Default handler for unknown or unhandled Exprent types.
     * <p>
     * This method is called by {@link AbstractExprentVisitor} for any
     * visit method that is not overridden. It can also be used as a
     * fallback for future Exprent types.
     *
     * @param exprent the expression to visit
     * @return the default result (typically null)
     */
    default T visitDefault(Exprent exprent) {
        return null;
    }
}
