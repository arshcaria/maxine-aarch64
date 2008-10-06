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
/*VCSID=204f3561-46ef-49b5-9b19-450473aa4a7c*/
package com.sun.max.memory;

import com.sun.max.annotate.*;
import com.sun.max.unsafe.*;

public class RuntimeMemoryRegion implements MemoryRegion {

    @INSPECTED
    private Address _start;

    @INSPECTED
    private Address _top;

    @INSPECTED
    protected Address _mark;

    @INLINE
    public Address start() {
        return _start;
    }

    public void setStart(Address start) {
        _start = start;
    }


    public final Address top() {
        return _top;
    }

    public void setTop(Address top) {
        _top = top;
    }

    @INSPECTED
    private Size _size;

    public Size size() {
        return _size;
    }

    public void setSize(Size size) {
        _size = size;
    }

    public RuntimeMemoryRegion() {
        _start = Address.zero();
        _size = Size.zero();
    }

    public RuntimeMemoryRegion(Size size) {
        _start = Address.zero();
        _size = size;
    }

    public RuntimeMemoryRegion(Address start, Size size) {
        _start = start;
        _size = size;
    }

    public RuntimeMemoryRegion(MemoryRegion memoryRegion) {
        _start = memoryRegion.start();
        _size = memoryRegion.size();
    }

    public Address end() {
        return start().plus(size());
    }

    public boolean contains(Address address) {
        return address.greaterEqual(start()) && address.lessThan(end());
    }

    public boolean overlaps(MemoryRegion memoryRegion) {
        return (_start.greaterEqual(memoryRegion.start()) && _start.lessThan(memoryRegion.end())) ||
               (end().greaterEqual(memoryRegion.start()) && end().lessThan(memoryRegion.end()));
    }

    public void reset() {
        _top = _start;
    }

    public void clear() {
        Memory.clear(start().asPointer(), size());
        _top = _start;
    }

    @Override
    public String toString() {
        return "[" + _start.toHexString() + " - " + end().minus(1).toHexString() + "]";
    }
    public Address getAllocationMark() {
        return _mark;
    }

}
