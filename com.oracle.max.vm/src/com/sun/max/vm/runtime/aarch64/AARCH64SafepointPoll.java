/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.max.vm.runtime.aarch64;

import static com.sun.max.platform.Platform.*;

import com.oracle.max.asm.target.aarch64.*;
import com.oracle.max.asm.target.amd64.*;
import com.sun.cri.ci.*;
import com.sun.max.annotate.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.runtime.*;

/**
 * The safepoint poll implementation for AMD64.
 *
 * @see AMD64TrapFrameAccess
 */
public final class AARCH64SafepointPoll extends SafepointPoll {

    /**
     * ATTENTION: must be callee-saved by all C ABIs in use.
     */
    public static final CiRegister LATCH_REGISTER = AARCH64.r26;

    @HOSTED_ONLY
    public AARCH64SafepointPoll() {
    }

    @HOSTED_ONLY
    @Override
    protected byte[] createCode() {
        final AARCH64Assembler asm = new AARCH64Assembler(target(), null);
        // TODO asm.movq(LATCH_REGISTER, new CiAddress(WordUtil.archKind(), LATCH_REGISTER.asValue()));
        return asm.codeBuffer.close(true);
    }
}
