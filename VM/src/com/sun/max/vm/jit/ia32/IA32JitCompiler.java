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
/*VCSID=7e4a689a-b38f-41fc-b770-0d41df5e290c*/
package com.sun.max.vm.jit.ia32;

import com.sun.max.collect.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.compiler.ir.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.jit.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.stack.StackFrameWalker.*;
import com.sun.max.vm.stack.ia32.*;
import com.sun.max.vm.template.*;

/**
 * Template-based implementation of JIT compiler for IA32.
 *
 * @author Bernd Mathiske
 */
public class IA32JitCompiler extends JitCompiler {

    public IA32JitCompiler(VMConfiguration vmConfiguration) {
        super(vmConfiguration);
    }

    public IA32JitCompiler(VMConfiguration vmConfiguration, TemplateTable templateTable) {
        this(vmConfiguration);
    }

    @Override
    protected TemplateBasedTargetGenerator targetGenerator() {
        return null; // TODO
    }

    @Override
    public Sequence<IrGenerator> irGenerators() {
        return Sequence.Static.empty(IrGenerator.class); // TODO
    }

    /**
     * @see IA32JitStackFrame
     */
    public boolean walkFrame(StackFrameWalker stackFrameWalker, boolean isTopFrame, TargetMethod targetMethod, Purpose purpose, Object context) {
        Problem.unimplemented();
        return true;
    }

    public void advance(StackFrameWalker stackFrameWalker, Word instructionPointer, Word stackPointer, Word framePointer) {
        Problem.unimplemented();
    }

}
