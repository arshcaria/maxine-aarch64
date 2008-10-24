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
package com.sun.max.vm.runtime;

import static com.sun.max.vm.thread.VmThreadLocal.*;

import com.sun.max.annotate.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.debug.*;
import com.sun.max.vm.jit.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.thread.*;

/**
 * This class handles operating systems traps that can arise from implicit null pointer checks,
 * integer divide by zero, GC safepoint triggering, etc. It contains a number of very low-level
 * functions to directly handle operating system signals (e.g. SIGSEGV on POSIX) and dispatch to
 * the correct handler (e.g. construct and throw a null pointer exception object or stop the
 * current thread for GC).
 *
 * A small amount of native code supports this class by connecting to the OS-specific signal
 * handling mechanisms.
 *
 * @author Ben L. Titzer
 */
public final class Trap {
    // note that these constants must be agreed upon in C land.
    public static final int MEMORY_FAULT = 0;
    public static final int STACK_FAULT = 1;
    public static final int ILLEGAL_INSTRUCTION = 2;
    public static final int ARITHMETIC_EXCEPTION = 3;
    public static final int ASYNC_INTERRUPT = 4;

    private static VMOption _dumpStackOnTrap =
        new VMOption("-XX:DumpStackOnTrap", "Reports a stack trace for every trap, regardless of the cause.", MaxineVM.Phase.PRISTINE);

    private static final CriticalMethod _trapStub = new CriticalMethod(Trap.class, "trapStub", CallEntryPoint.C_ENTRY_POINT);
    private static final CriticalMethod _nativeExit = new CriticalNativeMethod(MaxineVM.class, "native_exit");

    @PROTOTYPE_ONLY
    private Trap() {
    }

    @C_FUNCTION
    private static native void native_setJavaTrapStub(int signal, Word trapHandler);

    /**
     * Installs the trap handlers using the operating system's API.
     */
    public static void initialize() {
        final Address stubAddress = _trapStub.address();
        native_setJavaTrapStub(MEMORY_FAULT, stubAddress);
        native_setJavaTrapStub(STACK_FAULT, stubAddress);
        native_setJavaTrapStub(ILLEGAL_INSTRUCTION, stubAddress);
        native_setJavaTrapStub(ASYNC_INTERRUPT, stubAddress);
        native_setJavaTrapStub(ARITHMETIC_EXCEPTION, stubAddress);
    }

    /**
     * This method handles traps that occurred during execution. This method has a special ABI produced
     * by the compiler that saves the entire register state onto the stack before beginning execution.
     * When a trap occurs, the native trap handler (see the native code in <code>trap.c</code>) saves
     * a small amount of state in the disabled thread locals for the thread (the trap number, the instruction
     * pointer, and the fault address) and then returns to this stub. This trap stub saves all of the
     * registers onto the stack which are available in the <code>registerState</code> pointer.
     *
     * @param trap the trap number that occurred
     * @param registerState a pointer to the stack location where registers are stored
     * @param faultAddress the faulting address that caused this trap (memory faults only)
     */
    @C_FUNCTION(isTrapStub = true)
    private static void trapStub(int trap, Pointer registerState, Address faultAddress) {
        if (trap == ASYNC_INTERRUPT) {
            // do nothing for an asynchronous interrupt.
            return;
        }
        final Safepoint safepoint = VMConfiguration.hostOrTarget().safepoint();
        final Pointer instructionPointer = safepoint.getInstructionPointer(registerState);
        final Object origin = checkTrapOrigin(trap, registerState, faultAddress);
        if (origin instanceof TargetMethod) {
            final TargetMethod targetMethod = (TargetMethod) origin;
            // the trap occurred in Java
            final Pointer stackPointer = safepoint.getStackPointer(registerState, targetMethod);
            final Pointer framePointer = safepoint.getFramePointer(registerState, targetMethod);

            switch (trap) {
                case MEMORY_FAULT:
                    handleMemoryFault(instructionPointer, stackPointer, framePointer, registerState, faultAddress);
                    break;
                case STACK_FAULT:
                    // stack overflow
                    final StackOverflowError error = new StackOverflowError();
                    Throw.raise(error, stackPointer, framePointer, instructionPointer);
                    break; // unreachable
                case ILLEGAL_INSTRUCTION:
                    // deoptimization
                    VMConfiguration.hostOrTarget().compilerScheme().fakeCall(TRAP_INSTRUCTION_POINTER.getVariableWord().asAddress());
                    Deoptimizer.deoptimizeTopFrame();
                    break;
                case ARITHMETIC_EXCEPTION:
                    // integer divide by zero
                    final ArithmeticException exception = new ArithmeticException();
                    Throw.raise(exception, stackPointer, framePointer, instructionPointer);
                    break; // unreachable
            }
        } else {
            // the fault occurred in native code
            Debug.out.print("Trap in native code (or runtime stub) @ ");
            Debug.out.print(instructionPointer);
            Debug.out.println(", exiting.");
            FatalError.unexpected("Trap in native code");
        }
    }

    /**
     * Checks the origin of a trap by looking for a target method or runtime stub in the code regions. If found,
     * this method will return a reference to the <code>TargetMethod</code> that produces the error. If a runtime
     * stub produced the error, this method will return a reference to that runtime stub. Otherwise, this method
     * returns <code>null</code>, indicating the fault occurred in native code.
     *
     * @param trap the trap number
     * @param registerState the saved register state on the stack
     * @param faultAddress the faulting address
     * @return a reference to the <code>TargetMethod</code> containing the instruction pointer that caused the fault;
     * <code>null</code> if neither a runtime stub nor a target method produced the fault
     */
    private static Object checkTrapOrigin(int trap, Pointer registerState, Address faultAddress) {
        final Safepoint safepoint = VMConfiguration.hostOrTarget().safepoint();
        final Pointer instructionPointer = safepoint.getInstructionPointer(registerState);

        if (_dumpStackOnTrap.isPresent()) {
            Debug.print("Trap ");
            Debug.print(trap);
            Debug.print(" @ ");
            Debug.print(instructionPointer);
            Debug.print(", fault address: ");
            Debug.print(faultAddress);
            Throw.stackDump("MemoryFault", instructionPointer, VMRegister.getCpuStackPointer(), VMRegister.getCpuFramePointer());
        }

        // check to see if this fault originated in a target method
        final TargetMethod targetMethod = Code.codePointerToTargetMethod(instructionPointer);
        if (targetMethod != null) {
            return targetMethod;
        }

        // check to see if this fault originated in a runtime stub
        final RuntimeStub runtimeStub = Code.codePointerToRuntimeStub(instructionPointer);
        if (runtimeStub != null) {
            Debug.print("Fault ");
            Debug.print(trap);
            Debug.print(" in runtime stub @ ");
            return runtimeStub;
        }

        // this fault occurred in native code
        Debug.print("Fault ");
        Debug.print(trap);
        Debug.print(" in native code @ ");
        Debug.println(instructionPointer);

        return null;
    }

    /**
     * Handle a memory fault for this thread. A memory fault can be caused by an implicit null pointer check,
     * a segmentation fault, a safepoint being triggered, or an error in native code.
     *
     * @param instructionPointer the instruction pointer that caused the fault
     * @param stackPointer the stack pointer at the time of the fault
     * @param framePointer the frame pointer at the time of the fault
     * @param registerState a pointer to the register state at the time of the fault
     * @param faultAddress the address that caused the fault
     */
    private static void handleMemoryFault(Pointer instructionPointer, Pointer stackPointer, Pointer framePointer, Pointer registerState, Address faultAddress) {
        final Pointer disabledVmThreadLocals = VmThread.currentVmThreadLocals();

        final Safepoint safepoint = VMConfiguration.hostOrTarget().safepoint();
        final Pointer triggeredVmThreadLocals = VmThreadLocal.SAFEPOINTS_TRIGGERED_THREAD_LOCALS.getConstantWord(disabledVmThreadLocals).asPointer();
        final Pointer safepointLatch = safepoint.getSafepointLatch(registerState);

        // check to see if a safepoint has been triggered for this thread
        if (safepointLatch.equals(triggeredVmThreadLocals) && safepoint.isAt(instructionPointer)) {
            // a safepoint has been triggered for this thread. run the specified procedure
            final Reference reference = VmThreadLocal.SAFEPOINT_PROCEDURE.getVariableReference(triggeredVmThreadLocals);
            final Safepoint.Procedure runnable = UnsafeLoophole.cast(reference.toJava());
            if (runnable != null) {
                // run the procedure and then set the vm thread local to null
                runnable.run(registerState);
                // the state of the safepoint latch was TRIGGERED when the trap happened. reset it back to ENABLED.
                final Pointer enabledVmThreadLocals = VmThreadLocal.SAFEPOINTS_ENABLED_THREAD_LOCALS.getConstantWord(disabledVmThreadLocals).asPointer();
                safepoint.setSafepointLatch(registerState, enabledVmThreadLocals);
                Safepoint.reset(enabledVmThreadLocals);
                // reset the procedure to be null
                VmThreadLocal.SAFEPOINT_PROCEDURE.setVariableReference(triggeredVmThreadLocals, null);
            }
        } else if (inJava(disabledVmThreadLocals)) {
            // null pointer exception
            final NullPointerException error = new NullPointerException();
            Throw.raise(error, stackPointer, framePointer, instructionPointer);
        } else {
            // segmentation fault happened in native code somewhere, die.
            MaxineVM.native_trap_exit(MaxineVM.HARD_EXIT_CODE, instructionPointer);
        }
    }
}
