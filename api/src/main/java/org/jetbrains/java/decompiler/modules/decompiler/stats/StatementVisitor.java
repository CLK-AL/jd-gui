/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
 * found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.stats;

/**
 * Visitor interface for the Statement hierarchy.
 * <p>
 * This interface follows the Visitor design pattern to allow operations on Statement
 * objects without modifying their classes. It eliminates the need for instanceof
 * checks and type casting when processing different Statement types.
 * <p>
 * Usage example:
 * <pre>{@code
 * StatementVisitor<String> printer = new AbstractStatementVisitor<String>() {
 *     @Override
 *     public String visitIf(IfStatement statement) {
 *         return "If statement with " + (statement.getElsestat() != null ? "else" : "no else");
 *     }
 *
 *     @Override
 *     public String visitDo(DoStatement statement) {
 *         return "Loop type: " + statement.getLooptype();
 *     }
 * };
 *
 * String result = someStatement.accept(printer);
 * }</pre>
 *
 * @param <T> the return type of the visit operations
 * @see AbstractStatementVisitor
 * @see Statement#accept(StatementVisitor)
 */
public interface StatementVisitor<T> {

    /**
     * Visits an IfStatement (if/else control flow).
     *
     * @param statement the if statement to visit
     * @return the result of visiting
     */
    T visitIf(IfStatement statement);

    /**
     * Visits a DoStatement (loop construct: while, do-while, for, foreach).
     *
     * @param statement the loop statement to visit
     * @return the result of visiting
     */
    T visitDo(DoStatement statement);

    /**
     * Visits a SwitchStatement (switch control flow).
     *
     * @param statement the switch statement to visit
     * @return the result of visiting
     */
    T visitSwitch(SwitchStatement statement);

    /**
     * Visits a SequenceStatement (sequence of statements).
     *
     * @param statement the sequence statement to visit
     * @return the result of visiting
     */
    T visitSequence(SequenceStatement statement);

    /**
     * Visits a BasicBlockStatement (basic block of instructions).
     *
     * @param statement the basic block statement to visit
     * @return the result of visiting
     */
    T visitBasicBlock(BasicBlockStatement statement);

    /**
     * Visits a CatchStatement (try-catch block).
     *
     * @param statement the catch statement to visit
     * @return the result of visiting
     */
    T visitCatch(CatchStatement statement);

    /**
     * Visits a CatchAllStatement (try-finally or try-catch-all block).
     *
     * @param statement the catch-all statement to visit
     * @return the result of visiting
     */
    T visitCatchAll(CatchAllStatement statement);

    /**
     * Visits a RootStatement (root of the statement tree for a method).
     *
     * @param statement the root statement to visit
     * @return the result of visiting
     */
    T visitRoot(RootStatement statement);

    /**
     * Visits a SynchronizedStatement (synchronized block).
     *
     * @param statement the synchronized statement to visit
     * @return the result of visiting
     */
    T visitSync(SynchronizedStatement statement);

    /**
     * Visits a GeneralStatement (abstract/general statement).
     *
     * @param statement the general statement to visit
     * @return the result of visiting
     */
    T visitGeneral(GeneralStatement statement);

    /**
     * Visits a DummyExitStatement (placeholder exit statement).
     *
     * @param statement the dummy exit statement to visit
     * @return the result of visiting
     */
    T visitDummy(DummyExitStatement statement);

    /**
     * Default handler for unknown or unhandled Statement types.
     * <p>
     * This method is called by {@link AbstractStatementVisitor} for any
     * visit method that is not overridden. It can also be used as a
     * fallback for future Statement types.
     *
     * @param statement the statement to visit
     * @return the default result (typically null)
     */
    default T visitDefault(Statement statement) {
        return null;
    }
}
