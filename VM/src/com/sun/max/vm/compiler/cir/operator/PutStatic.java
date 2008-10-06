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
/*VCSID=96dd0b51-467e-44ee-838a-e749ab6f3513*/
package com.sun.max.vm.compiler.cir.operator;

import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.compiler.b.c.*;
import com.sun.max.vm.compiler.cir.transform.*;
import com.sun.max.vm.type.*;


public class PutStatic extends JavaOperator {
    private FieldActor _fieldActor;
    private final ConstantPool _constantPool;
    private final int _index;
    private final Kind _fieldKind;

    public PutStatic(ConstantPool constantPool, int index) {
        _constantPool = constantPool;
        _index = index;
        final FieldRefConstant ref = constantPool.fieldAt(index);

        _fieldKind = ref.type(constantPool).toKind();

        if (ref.isResolved() /*|| ref.isResolvableWithoutClassLoading(constantPool)*/) {
            _fieldActor = ref.resolve(constantPool, index);
        } else {
            _fieldActor = null;
        }
    }


    public boolean isResolved() {
        return _fieldActor != null;
    }

    public void resolve() {
        _fieldActor = constantPool().fieldAt(index()).resolve(constantPool(), index());
    }

    public boolean isClassInitialized() {
        if (!isResolved()) {
            return false;
        }
        return _fieldActor.holder().isInitialized();
    }

    public void initializeClass() {
        if (!isClassInitialized()) {
            if (!isResolved()) {
                resolve();
            }
            _fieldActor.holder().makeInitialized();
        }
    }

    public FieldActor fieldActor() {
        return _fieldActor;
    }

    public Kind fieldKind() {
        return _fieldKind;
    }

    @Override
    public Kind resultKind() {
        return Kind.VOID;
    }

    @Override
    public void acceptVisitor(CirVisitor visitor) {
        visitor.visitHCirOperator(this);
    }

    @Override
    public void acceptVisitor(HCirOperatorVisitor visitor) {
        visitor.visit(this);
    }


    public ConstantPool constantPool() {
        return _constantPool;
    }


    public int index() {
        return _index;
    }

    @Override
    public String toString() {
        return "Putstatic";
    }
}
