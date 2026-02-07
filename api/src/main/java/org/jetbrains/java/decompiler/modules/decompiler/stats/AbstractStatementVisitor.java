/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
 * found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.stats;

/**
 * Abstract base implementation of {@link StatementVisitor} that provides default
 * implementations for all visit methods.
 * <p>
 * Each visit method delegates to {@link #visitDefault(Statement)} by default,
 * allowing subclasses to override only the methods they need while providing
 * a common fallback behavior for unhandled types.
 * <p>
 * Usage example:
 * <pre>{@code
 * // Count all loop statements in a statement tree
 * StatementVisitor<Integer> loopCounter = new AbstractStatementVisitor<Integer>() {
 *     @Override
 *     public Integer visitDo(DoStatement statement) {
 *         return 1;
 *     }
 *
 *     @Override
 *     public Integer visitDefault(Statement statement) {
 *         return 0;
 *     }
 * };
 *
 * int count = someStatement.accept(loopCounter);
 * }</pre>
 *
 * @param <T> the return type of the visit operations
 * @see StatementVisitor
 */
public abstract class AbstractStatementVisitor<T> implements StatementVisitor<T> {

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Statement)}.
     */
    @Override
    public T visitIf(IfStatement statement) {
        return visitDefault(statement);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Statement)}.
     */
    @Override
    public T visitDo(DoStatement statement) {
        return visitDefault(statement);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Statement)}.
     */
    @Override
    public T visitSwitch(SwitchStatement statement) {
        return visitDefault(statement);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Statement)}.
     */
    @Override
    public T visitSequence(SequenceStatement statement) {
        return visitDefault(statement);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Statement)}.
     */
    @Override
    public T visitBasicBlock(BasicBlockStatement statement) {
        return visitDefault(statement);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Statement)}.
     */
    @Override
    public T visitCatch(CatchStatement statement) {
        return visitDefault(statement);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Statement)}.
     */
    @Override
    public T visitCatchAll(CatchAllStatement statement) {
        return visitDefault(statement);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Statement)}.
     */
    @Override
    public T visitRoot(RootStatement statement) {
        return visitDefault(statement);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Statement)}.
     */
    @Override
    public T visitSync(SynchronizedStatement statement) {
        return visitDefault(statement);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Statement)}.
     */
    @Override
    public T visitGeneral(GeneralStatement statement) {
        return visitDefault(statement);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #visitDefault(Statement)}.
     */
    @Override
    public T visitDummy(DummyExitStatement statement) {
        return visitDefault(statement);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation returns null. Override this method to provide
     * a common fallback behavior for all unhandled Statement types.
     */
    @Override
    public T visitDefault(Statement statement) {
        return null;
    }
}
