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

package com.sun.max.vm.compiler.cir.optimize;

import com.sun.max.collect.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.b.c.*;
import com.sun.max.vm.compiler.cir.*;
import com.sun.max.vm.compiler.cir.operator.*;
import com.sun.max.vm.compiler.cir.transform.*;
import com.sun.max.vm.compiler.cir.variable.*;

/**
 * A simple data flow analyzer that determines what values can/cannot be
 * null at different points in the program.  It currently recognizes the
 * following operations on objects: New, NewArray, GetField, PutField,
 * ArrayLength, ArrayLoad, ArrayStore, InvokeVirtual, and InvokeInterface.
 *
 * @author Aziz Ghuloum
 */
public class EnvBasedInitializedAnalysis extends EnvBasedDFA<InitializedDomain.Set>{

    public EnvBasedInitializedAnalysis(InitializedDomain domain) {
        super(domain);
    }

    @Override
    protected void analyzeJavaOperatorCall(CirCall call, Environment<CirVariable, InitializedDomain.Set> env) {
        final JavaOperator op = (JavaOperator) call.procedure();
        final CirValue[] args = call.arguments();
        final CirValue sk = args[args.length - 2];
        final CirValue fk = args[args.length - 1];
        if (op instanceof New || op instanceof NewArray) {
            /* New and NewArray are special in that if they succeed, they
             * produce a value that's known to be not null.
             */
            visitContinuation(sk, InitializedDomain.DOMAIN.getInitialized(), env);
            visitContinuation(fk, env);
        } else {
            if (op instanceof GetField      ||
                op instanceof PutField      ||
                op instanceof ArrayLoad     ||
                op instanceof ArrayLength   ||
                op instanceof ArrayStore    ||
                op instanceof InvokeVirtual ||
                op instanceof InvokeInterface) {
                final CirValue receiver = args[0];
                final InitializedDomain.Set t = lookupArg(receiver, env);
                /* record the actual value of the receiver at this
                 * point in the program.
                 */
                rememberMapping(call, t);
                /* in the normal continuation, the value of the receiver is known to be
                 * not null, so, we extend the environment to map the receiver to "not null"
                 */
                visitContinuation(sk,
                    (receiver instanceof CirVariable)
                    ? env.extend((CirVariable) receiver, InitializedDomain.DOMAIN.getInitialized())
                    : env);
                /* we don't care about the exception continuation except that we
                 * have to process to visit it.
                 */
                visitContinuation(fk, env);
            } else {
                super.analyzeJavaOperatorCall(call, env);
            }
        }
    }

    /**
     * Here, we use the results of the analysis that we performed above
     * in order to mark the flag "canRaiseNullPointerException" in the
     * different operations which are called with a receiver that's known
     * to be initialized (i.e., not null).
     *
     * @author Aziz Ghuloum
     */
    public static class InitializedResult {
        private static class OpVisitor extends HCirOperatorDefaultVisitor {
            final CirCall _call;
            InitializedDomain.Set[] _set;
            private InitializedDomain.Set val(int i) {
                if (i < _set.length) {
                    return _set[i];
                }
                return InitializedDomain.DOMAIN.getBottom();
            }
            OpVisitor(CirCall call, InitializedDomain.Set[] set) {
                _call = call;
                _set = set;
            }

            @Override
            public void visit(GetField op) {
                op.setCanRaiseNullPointerException(!val(0).isInitialized());
            }
            @Override
            public void visit(PutField op) {
                op.setCanRaiseNullPointerException(!val(0).isInitialized());
            }
            @Override
            public void visit(ArrayLength op) {
                op.setCanRaiseNullPointerException(!val(0).isInitialized());
            }
            @Override
            public void visit(ArrayLoad op) {
                if (val(0).isInitialized()) {
                    op.removeThrownExceptions(ExceptionThrower.NULL_POINTER_EXCEPTION);
                }
            }
            @Override
            public void visit(ArrayStore op) {
                if (val(0).isInitialized()) {
                    op.removeThrownExceptions(ExceptionThrower.NULL_POINTER_EXCEPTION);
                }
            }
        }

        private static class TreeVisitor extends CirVisitor {
            final IdentityHashMapping<CirCall, InitializedDomain.Set[]> _results;
            TreeVisitor(IdentityHashMapping<CirCall, InitializedDomain.Set[]> results) {
                _results = results;
            }
            @Override
            public void visitCall(CirCall call) {
                final CirValue op = call.procedure();
                if (op instanceof JavaOperator) {
                    final InitializedDomain.Set[] set = _results.get(call);
                    if (set != null) {
                        final OpVisitor vis = new OpVisitor(call, set);
                        ((JavaOperator) op).acceptVisitor(vis);
                    }
                }
            }
        }

        public static void applyResult(CirClosure closure, IdentityHashMapping<CirCall, InitializedDomain.Set[]> results) {
            final TreeVisitor visitor = new TreeVisitor(results);
            CirVisitingTraversal.apply(closure, visitor);
        }
    }
}
