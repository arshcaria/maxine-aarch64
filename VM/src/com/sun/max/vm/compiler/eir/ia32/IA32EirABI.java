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
/*VCSID=7c9b5799-ee8d-408d-8fdc-58538ab4ed94*/
package com.sun.max.vm.compiler.eir.ia32;

import com.sun.max.asm.ia32.*;
import com.sun.max.collect.*;
import com.sun.max.lang.*;
import com.sun.max.vm.*;
import com.sun.max.vm.compiler.eir.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.runtime.*;

/**
 * @author Bernd Mathiske
 */
public abstract class IA32EirABI extends EirABI<IA32EirRegister> {

    protected IA32EirABI(VMConfiguration vmConfiguration, TargetABI <IA32GeneralRegister32, IA32XMMRegister> targetABI) {
        super(vmConfiguration, IA32EirRegister.class);
        _targetABI = targetABI;
    }

    @Override
    public int stackSlotSize() {
        return Longs.SIZE;
    }

    @Override
    public Pool<IA32EirRegister> registerPool() {
        return IA32EirRegister.pool();
    }


    @Override
    public IA32EirRegister.General integerRegisterActingAs(VMRegister.Role role) {
        return IA32EirRegister.General.from(null); // TODO
    }

    @Override
    public IA32EirRegister.General floatingPointRegisterActingAs(VMRegister.Role role) {
        return null; // TODO
    }

    protected void initTargetABI(TargetABI<IA32GeneralRegister32, IA32XMMRegister> targetABI) {
        _targetABI = targetABI;
    }

    private TargetABI<IA32GeneralRegister32, IA32XMMRegister> _targetABI;

    @Override
    public TargetABI<IA32GeneralRegister32, IA32XMMRegister> targetABI() {
        return _targetABI;
    }
}
