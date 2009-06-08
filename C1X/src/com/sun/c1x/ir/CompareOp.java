/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.c1x.ir;

import com.sun.c1x.util.InstructionVisitor;
import com.sun.c1x.util.InstructionClosure;
import com.sun.c1x.value.ValueStack;
import com.sun.c1x.value.ValueType;

/**
 * The <code>CompareOp</code> instruction represents comparisons such as equals, not equal, etc.
 *
 * @author Ben L. Titzer
 */
public class CompareOp extends Op2 {

    ValueStack _stateBefore;

    /**
     * Creates a new compare operation.
     * @param opcode the bytecode opcode
     * @param x the first input
     * @param y the second input
     * @param stateBefore the state before the comparison is performed
     */
    public CompareOp(int opcode, Instruction x, Instruction y, ValueStack stateBefore) {
        super(ValueType.INT_TYPE, opcode, x, y);
        _stateBefore = stateBefore;
    }

    /**
     * Gets the value stack representing the state before the comparison is performed.
     * @return the state before the comparison is performed
     */
    public ValueStack stateBefore() {
        return _stateBefore;
    }

    /**
     * Iterates over the other values in this instruction.
     * @param closure the closure to apply to each value
     */
    public void otherValuesDo(InstructionClosure closure) {
        if (_stateBefore != null) {
            _stateBefore.valuesDo(closure);
        }
    }

    /**
     * Implements this instruction's half of the visitor pattern.
     * @param v the visitor to accept
     */
    public void accept(InstructionVisitor v) {
        v.visitCompareOp(this);
    }
}