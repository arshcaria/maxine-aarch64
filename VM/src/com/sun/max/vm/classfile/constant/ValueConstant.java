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
/*VCSID=fd40fe20-93e0-4034-9fda-f97f3ea8a73c*/
package com.sun.max.vm.classfile.constant;

import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.value.*;

/**
 * A value constant is a constant whose value can be directly used as a program value via a {@link Bytecode#LDC},
 * {@link Bytecode#LDC_W} or {@link Bytecode#LDC2_W} instruction.
 * 
 * @author Doug Simon
 */
public interface ValueConstant<PoolConstant_Type extends PoolConstant<PoolConstant_Type>> {

    Value value(ConstantPool pool, int index);
}
