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
package test.directives;

import com.sun.max.annotate.FOLD;

/*
 * @Harness: java
 * @Runs: 0=true
 */
public class Fold02 {

    public static boolean test(int arg) {
        return fint(10, 10) && ffloat(0.1f, 0.1f) && fobj(null, null);
    }

    @FOLD
    static boolean fint(int x, int y) {
        int j = 2;
        for (int i = 0; i < 100; i++) {
            j = j + 8 / j;
        }
        return x == y;
    }

    @FOLD
    static boolean ffloat(float x, float y) {
        int j = 2;
        for (int i = 0; i < 100; i++) {
            j = j + 8 / j;
        }
        return x == y;
    }

    @FOLD
    static boolean fobj(Object x, Object y) {
        int j = 2;
        for (int i = 0; i < 100; i++) {
            j = j + 8 / j;
        }
        return x == y;
    }
}