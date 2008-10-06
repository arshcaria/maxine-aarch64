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
/*VCSID=59bbad7f-90ef-427d-ba99-e0f2ef88da4f*/
package com.sun.max.tele.object;

import com.sun.max.tele.*;
import com.sun.max.tele.value.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.value.*;


/**
 * Canonical surrogate for an object of type {@link ObjectReferenceValue} in the tele VM.
 *
 * @author Michael Van De Vanter
 */
public class TeleObjectReferenceValue extends TeleTupleObject {

    protected TeleObjectReferenceValue(TeleVM teleVM, Reference teleObjectReferenceValueReference) {
        super(teleVM, teleObjectReferenceValueReference);
    }

    /**
     * @return a local wrapper for the {@link ReferenceValue} in the tele VM.
     */
    public TeleReferenceValue getTeleReferenceValue() {
        return TeleReferenceValue.from(teleVM(), teleVM().fields().ObjectReferenceValue_value.readReference(reference()));
    }

    @Override
    protected Object createDeepCopy(DeepCopyContext context) {
        // Translate into local equivalent
        return getTeleReferenceValue();
    }

}
