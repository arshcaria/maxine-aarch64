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
/*VCSID=2e5e61db-a09e-4871-ae93-3e48f546d5b6*/
/*
 * @Harness: java
 * @Runs: (0, 11) = 11; (1, 21) = 21; (0, 42) = 42; (3, 0) = !java.lang.ArrayIndexOutOfBoundsException
 */
package test.reflect;

import java.lang.reflect.*;

public class Array_set02 {
    private static final int[] array = { -1, -1, -1 };
    public static int test(int i, int value) {
        Array.set(array, i, value);
        return array[i];
    }
}

