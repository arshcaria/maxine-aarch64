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
/*VCSID=2fd4d8f8-2507-4547-92c3-898092b69e56*/
package com.sun.max.annotate;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)

/**
 * Marker indicating that a field is quasi 'final' for all practical purposes,
 * AFTER it has been set to a value that is not null, 0, etc.
 * because it is only initialized once and only inspected after proper initialization.
 * It is up to the programmer to maintain this invariant!
 * 
 * Note that reading the field's value can only occur AFTER prototyping.
 * Therefore, there is no constant folding during prototyping.
 *
 * @author Bernd Mathiske
 */
public @interface CONSTANT_WHEN_NOT_ZERO {
}
