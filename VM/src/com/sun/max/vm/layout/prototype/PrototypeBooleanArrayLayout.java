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
/*VCSID=e14e6ba6-f111-4289-82c6-108341c02705*/
package com.sun.max.vm.layout.prototype;

import com.sun.max.unsafe.*;
import com.sun.max.vm.grip.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * @author Bernd Mathiske
 */
public class PrototypeBooleanArrayLayout extends PrototypePrimitiveArrayLayout<BooleanValue> implements com.sun.max.vm.layout.BooleanArrayLayout {

    public PrototypeBooleanArrayLayout(GripScheme gripScheme) {
        super(gripScheme, Kind.BOOLEAN);
    }

    public boolean getBoolean(Accessor accessor, int index) {
        return accessor.getBoolean(0, index);
    }

    public void setBoolean(Accessor accessor, int index, boolean value) {
        accessor.setBoolean(0, index, value);
    }

}
