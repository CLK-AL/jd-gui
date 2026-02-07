/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package al.clk.gui.view.component;

import org.fife.ui.rsyntaxtextarea.AbstractJavaTokenMaker;
import org.fife.ui.rsyntaxtextarea.TokenMap;
import org.fife.ui.rsyntaxtextarea.Token;

/**
 * Token maker for Java 21 that extends the default Java token maker
 * to add support for Java 9-21 keywords:
 *
 * Java 9 (Modules):
 * - module, open, requires, exports, opens, to, uses, provides, with, transitive
 *
 * Java 10 (Local Variable Type Inference):
 * - var
 *
 * Java 14 (Switch Expressions):
 * - yield
 *
 * Java 16 (Records):
 * - record
 *
 * Java 17 (Sealed Classes):
 * - sealed, permits, non-sealed
 *
 * Java 21 (Pattern Matching):
 * - when
 */
public class Java21TokenMaker extends AbstractJavaTokenMaker {

    /**
     * Returns the words to highlight for Java 21.
     * This includes all standard Java keywords plus Java 9-21 additions.
     *
     * @return A TokenMap containing all Java 21 keywords.
     */
    @Override
    public TokenMap getWordsToHighlight() {
        TokenMap tokenMap = super.getWordsToHighlight();

        // Module keywords (Java 9+) - contextual keywords in module-info.java
        // These are already identifiers in regular Java files
        tokenMap.put("module", Token.RESERVED_WORD);
        tokenMap.put("open", Token.RESERVED_WORD);
        tokenMap.put("requires", Token.RESERVED_WORD);
        tokenMap.put("exports", Token.RESERVED_WORD);
        tokenMap.put("opens", Token.RESERVED_WORD);
        tokenMap.put("to", Token.RESERVED_WORD);
        tokenMap.put("uses", Token.RESERVED_WORD);
        tokenMap.put("provides", Token.RESERVED_WORD);
        tokenMap.put("with", Token.RESERVED_WORD);
        tokenMap.put("transitive", Token.RESERVED_WORD);

        // Local variable type inference (Java 10+)
        tokenMap.put("var", Token.RESERVED_WORD);

        // Switch expressions (Java 14+)
        tokenMap.put("yield", Token.RESERVED_WORD);

        // Records (Java 16+)
        tokenMap.put("record", Token.RESERVED_WORD);

        // Sealed classes (Java 17+)
        tokenMap.put("sealed", Token.RESERVED_WORD);
        tokenMap.put("permits", Token.RESERVED_WORD);
        tokenMap.put("non-sealed", Token.RESERVED_WORD);

        // Pattern matching guards (Java 21+)
        tokenMap.put("when", Token.RESERVED_WORD);

        return tokenMap;
    }
}
