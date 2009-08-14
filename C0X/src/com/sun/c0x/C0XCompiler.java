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
package com.sun.c0x;

import com.sun.c1x.ci.*;
import com.sun.c1x.target.Target;

/**
 * The <code>C0XCompiler</code> class definition.
 *
 * @author Ben L. Titzer
 */
public class C0XCompiler extends CiCompiler {

    public C0XCompiler(CiRuntime runtime, Target target) {
        super(runtime, target);
    }
    /**
     * Compile the specified method.
     *
     * @param method the method to compile
     * @return a {@link com.sun.c1x.ci.CiTargetMethod target method} representing the compiled method
     */
    @Override
    public CiTargetMethod compileMethod(CiMethod method) {
        C0XCompilation comp = new C0XCompilation(runtime, method, null);
        comp.compile();
        return null;
    }

    /**
     * Compile the specified method.
     *
     * @param method the method to compile
     * @param osrBCI the bytecode index of the entrypoint for an on-stack-replacement
     * @return a {@link com.sun.c1x.ci.CiTargetMethod target method} representing the compiled method
     */
    @Override
    public CiTargetMethod compileMethod(CiMethod method, int osrBCI) {
        C0XCompilation comp = new C0XCompilation(runtime, method, null);
        comp.compile();
        return null;
    }
}