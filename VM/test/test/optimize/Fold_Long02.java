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
package test.optimize;

/*
 * Tests constant folding of integer comparisons.
 * @Harness: java
 * @Runs: 0=true; 1=true; 2=true; 3=false; 4=false; 5=false
 */
public class Fold_Long02 {
    public static boolean test(int arg) {
        if (arg == 0) {
            return equ();
        }
        if (arg == 1) {
            return neq();
        }
        if (arg == 2) {
            return geq();
        }
        if (arg == 3) {
            return ge();
        }
        if (arg == 4) {
            return ltq();
        }
        if (arg == 5) {
            return lt();
        }
        return false;
    }
    static boolean equ() {
        long x = 34;
        return x == 34;
    }
    static boolean neq() {
        long x = 34;
        return x != 33;
    }
    static boolean geq() {
        long x = 34;
        return x >= 33;
    }
    static boolean ge() {
        long x = 34;
        return x > 35;
    }
    static boolean ltq() {
        long x = 34;
        return x <= 32;
    }
    static boolean lt() {
        long x = 34;
        return x < 31;
    }
}