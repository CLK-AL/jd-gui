/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
 * found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.exps;

/**
 * Abstract base implementation of {@link ExprentVisitor} that provides default
 * implementations for all visit methods.
 * <p>
 * Each visit method delegates to {@link #visitDefault(Exprent)} by default,
 * allowing subclasses to override only the methods they need while providing
 * a common fallback behavior for unhandled types.
 * <p>
 * Usage example:
 * <pre>{@code
 * // Count all method invocations in an expression tree
 * ExprentVisitor<Integer> invocationCounter = new AbstractExprentVisitor<Integer>() {
 *     @Override
 *     public Integer visitInvocation(InvocationExprent exprent) {
 *         return 1;
 *     }
 *
 *     @Override
 *     public Integer visitDefault(Exprent exprent) {
 *         return 0;
 *     }
 * };
 *
 * int count = someExprent.accept(invocationCounter);
 * }</pre>
 *
 * @param <T> the return type of the visit operations
 * @see ExprentVisitor
 */
public abstract class AbstractExprentVisitor<T> implements ExprentVisitor<T> {

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitInvocation(InvocationExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitVar(VarExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitConst(ConstExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitFunction(FunctionExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitNew(NewExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitField(FieldExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitAssignment(AssignmentExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitArray(ArrayExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitSwitch(SwitchExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitSwitchHead(SwitchHeadExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitMonitor(MonitorExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitExit(ExitExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitAnnotation(AnnotationExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitAssert(AssertExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitIf(IfExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Exprent)}.
     */
    @Override
    public T visitYield(YieldExprent exprent) {
        return visitDefault(exprent);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation returns null. Override this method to provide
     * a common fallback behavior for all unhandled Exprent types.
     */
    @Override
    public T visitDefault(Exprent exprent) {
        return null;
    }
}
