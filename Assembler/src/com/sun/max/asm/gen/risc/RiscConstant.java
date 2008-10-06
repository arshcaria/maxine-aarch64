/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
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
/*VCSID=4c52046b-1591-4965-be63-ab7226246f03*/
package com.sun.max.asm.gen.risc;

import com.sun.max.asm.*;
import com.sun.max.asm.gen.risc.field.*;

/**
 * 
 *
 * @author Bernd Mathiske
 */
public class RiscConstant {

    private final RiscField _field;
    private final int _value;

    public RiscConstant(RiscField field, Argument argument) {
        _field = field;
        _value = (int) argument.asLong();
    }

    public RiscConstant(RiscField field, int value) {
        _field = field;
        _value = value;
    }

    public RiscField field() {
        return _field;
    }

    public int value() {
        return _value;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof RiscConstant) {
            final RiscConstant riscConstant = (RiscConstant) other;
            return _field.equals(riscConstant._field) && _value == riscConstant._value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return _field.hashCode() ^ _value;
    }

    @Override
    public String toString() {
        return _field.toString() + "(" + _value + ")";
    }
}
