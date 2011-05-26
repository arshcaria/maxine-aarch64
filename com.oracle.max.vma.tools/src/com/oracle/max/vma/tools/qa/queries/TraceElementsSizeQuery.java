/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.max.vma.tools.qa.queries;

import java.io.*;
import java.util.ArrayList;

import com.oracle.max.vma.tools.qa.*;

/**
 * Debugging tool. Reports on the distribution of the size of the {@link ObjectRecord#getTraceElements()} list.
 */
public class TraceElementsSizeQuery extends QueryBase {
    @Override
    public Object execute(ArrayList<TraceRun> traceRuns, int traceFocus,
            PrintStream ps, String[] args) {
        parseArgs(args);
        final int[] buckets = new int[65536];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = 0;
        }
        TraceRun traceRun = traceRuns.get(traceFocus);
        TraceRun.Visitor visitor = new TraceRun.Visitor() {
            @Override
            public void visit(TraceRun traceRun, ObjectRecord td,
                    PrintStream ps, Object[] args) {
                final int teSize = td.getTraceElements().size();
                if (teSize == 0) {
                    System.console();
                }
                int bucketIndex = teSize >= buckets.length - 1 ? buckets.length - 1
                        : teSize;
                buckets[bucketIndex]++;
                if (verbose) {
                    ps.println("object " + td.getId() + " has " + teSize
                            + " trace elements");
                }
            }
        };
        traceRun.visit(visitor, ps, args);
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i] > 0) {
                ps.println("bucket " + i + " has " + buckets[i] + " entries");
            }
        }
        return null;
    }

}
