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
package com.sun.max.vm.layout.hosted;

import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * @author Bernd Mathiske
 */
public abstract class HostedArrayLayout<Value_Type extends Value<Value_Type>> extends HostedArrayHeaderLayout implements ArrayLayout<Value_Type> {

    protected final Kind<Value_Type> elementKind;

    public HostedArrayLayout(Kind<Value_Type> elementKind) {
        this.elementKind = elementKind;
    }

    public Kind<Value_Type> elementKind() {
        return elementKind;
    }

    public Layout.Category category() {
        return Layout.Category.ARRAY;
    }

    @Override
    public final boolean isReferenceArrayLayout() {
        final Kind rawKind = elementKind;
        return rawKind.isReference;
    }

    public Offset getElementOffsetFromOrigin(int index) {
        throw ProgramError.unexpected("cannot compute cell offset in prototype layout");
    }

    public Offset getElementOffsetInCell(int index) {
        throw ProgramError.unexpected("cannot compute cell offset in prototype layout");
    }

    public int getElementSize() {
        return elementKind().width.numberOfBytes;
    }

    public Size getHeaderSize() {
        throw ProgramError.unexpected();
    }

    public Offset getElementOffset(int index) {
        return Offset.fromInt(index * getElementSize());
    }

    public Size getArraySize(int length) {
        throw ProgramError.unexpected();
    }

    public Size specificSize(Accessor accessor) {
        throw ProgramError.unexpected();
    }

    public Value readValue(Kind kind, ObjectMirror mirror, int offset) {
        throw ProgramError.unexpected();
    }

    public void writeValue(Kind kind, ObjectMirror mirror, int offset, Value value) {
        assert kind == value.kind();
        ProgramError.unexpected();
    }

    public void copyElements(Accessor src, int srcIndex, Object dst, int dstIndex, int length) {
        ProgramError.unexpected();
    }
}