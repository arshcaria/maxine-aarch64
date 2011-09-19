/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vm.ext.maxri;

import static com.sun.cri.bytecode.Bytecodes.*;
import static com.sun.max.platform.Platform.*;
import static com.sun.max.vm.MaxineVM.*;
import static com.sun.max.vm.compiler.target.Stub.Type.*;
import static com.sun.max.vm.stack.VMFrameLayout.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.max.asm.target.amd64.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.Call;
import com.sun.cri.ci.CiTargetMethod.DataPatch;
import com.sun.cri.ci.CiTargetMethod.Safepoint;
import com.sun.cri.ci.CiUtil.RefMapFormatter;
import com.sun.cri.ri.*;
import com.sun.max.annotate.*;
import com.sun.max.lang.*;
import com.sun.max.platform.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.adaptive.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.value.*;

/**
 * The {@code MaxRiRuntime} class implements the runtime interface needed by C1X.
 * This includes access to runtime features such as class and method representations,
 * constant pools, as well as some compiler tuning.
 */
public class MaxRuntime implements RiRuntime {

    private RiSnippets snippets;

    private static MaxRuntime instance = new MaxRuntime();

    public static MaxRuntime getInstance() {
        return instance;
    }

    private MaxRuntime() {
    }

    /**
     * Gets the constant pool for a specified method.
     * @param method the compiler interface method
     * @return the compiler interface constant pool for the specified method
     */
    public RiConstantPool getConstantPool(RiMethod method) {
        return asClassMethodActor(method, "getConstantPool()").compilee().codeAttribute().cp;
    }

    public RiOsrFrame getOsrFrame(RiMethod method, int bci) {
        throw new InternalError("unimplemented");
    }

    /**
     * Checks whether the runtime requires inlining of the specified method.
     * @param method the method to inline
     * @return {@code true} if the method must be inlined; {@code false}
     * to allow the compiler to use its own heuristics
     */
    public boolean mustInline(RiMethod method) {
        if (!method.isResolved()) {
            return false;
        }

        ClassMethodActor methodActor = asClassMethodActor(method, "mustNotInline()");
        if (methodActor.isInline()) {
            return true;
        }

        // Remove the indirection through the "access$..." methods generated by the Java
        // source compiler for the purpose of implementing access to private fields between
        // in an inner-class relationship.
        if (MaxineVM.isHosted() && methodActor.isSynthetic() && methodActor.isStatic() && methodActor.name().startsWith("access$")) {
            return true;
        }

        return methodActor.isInline();
    }

    /**
     * Checks whether the runtime forbids inlining of the specified method.
     * @param method the method to inline
     * @return {@code true} if the runtime forbids inlining of the specified method;
     * {@code false} to allow the compiler to use its own heuristics
     */
    public boolean mustNotInline(RiMethod method) {
        if (!method.isResolved()) {
            return false;
        }
        final ClassMethodActor classMethodActor = asClassMethodActor(method, "mustNotInline()");
        if (classMethodActor.isNative()) {
            // Native stubs must not be inlined as there is a 1:1 relationship between
            // a NativeFunction and the TargetMethod from which it is called. This
            // required so that the exact position of the native function call
            // site can be recorded in the NativeFunction instance. The call site
            // is used by
            return true;
        }

        if (isHosted() && AdaptiveCompilationScheme.compileWithBaseline.contains(classMethodActor.holder().javaClass())) {
            // Ensure that methods intended to be compiled by the baseline compiler for the
            // purpose of a JTT test are not inlined.
            return true;
        }

        return classMethodActor.codeAttribute() == null || classMethodActor.isNeverInline();
    }

    /**
     * Checks whether the runtime forbids compilation of the specified method.
     * @param method the method to compile
     * @return {@code true} if the runtime forbids compilation of the specified method;
     * {@code false} to allow the compiler to compile the method
     */
    public boolean mustNotCompile(RiMethod method) {
        return false;
    }

    static ClassMethodActor asClassMethodActor(RiMethod method, String operation) {
        if (method instanceof ClassMethodActor) {
            return (ClassMethodActor) method;
        }
        throw new CiUnresolvedException("invalid RiMethod instance: " + method.getClass());
    }

    public int basicObjectLockOffsetInBytes() {
        // Must not be called if the size of the lock object is 0.
        throw new InternalError("should not reach here");
    }

    public int sizeOfBasicObjectLock() {
        // locks are not placed on the stack
        return 0;
    }

    public int codeOffset() {
        return CallEntryPoint.OPTIMIZED_ENTRY_POINT.offset();
    }

    @Override
    public String disassemble(RiMethod method) {
        ClassMethodActor classMethodActor = asClassMethodActor(method, "disassemble()");
        return classMethodActor.format("%f %R %H.%n(%P)") + String.format("%n%s", CodeAttributePrinter.toString(classMethodActor.codeAttribute()));
    }

    public String disassemble(byte[] code, long address) {
        final Platform platform = Platform.platform();
        CiHexCodeFile hcf = new CiHexCodeFile(code, address, platform.isa.name(), platform.wordWidth().numberOfBits);
        return hcf.toEmbeddedString();
    }

    @Override
    public String disassemble(final CiTargetMethod tm) {
        return disassemble(tm, null);
    }

    public String disassemble(CiTargetMethod ciTM, MaxTargetMethod maxTM) {
        byte[] code = maxTM == null ? Arrays.copyOf(ciTM.targetCode(), ciTM.targetCodeSize()) : maxTM.code();
        final Platform platform = Platform.platform();

        long startAddress = maxTM == null ? 0L : maxTM.codeStart().toLong();
        CiHexCodeFile hcf = new CiHexCodeFile(code, startAddress, platform.isa.name(), platform.wordWidth().numberOfBits);
        CiUtil.addAnnotations(hcf, ciTM.annotations());
        addExceptionHandlersComment(ciTM, hcf);
        CiRegister fp;
        int refMapToFPOffset;
        if (platform.isa == ISA.AMD64) {
            fp = AMD64.rsp;
            refMapToFPOffset = 0;
        } else {
            throw FatalError.unimplemented();
        }
        RefMapFormatter slotFormatter = new RefMapFormatter(target().arch, target().spillSlotSize, fp, refMapToFPOffset);
        for (Safepoint safepoint : ciTM.safepoints) {
            if (safepoint instanceof Call) {
                Call call = (Call) safepoint;
                if (call.debugInfo != null) {
                    hcf.addComment(Safepoints.safepointPosForCall(call.pcOffset, call.size), CiUtil.append(new StringBuilder(100), call.debugInfo, slotFormatter).toString());
                }
                addOperandComment(hcf, call.pcOffset, "{" + call.target + "}");
            } else {
                if (safepoint.debugInfo != null) {
                    hcf.addComment(safepoint.pcOffset, CiUtil.append(new StringBuilder(100), safepoint.debugInfo, slotFormatter).toString());
                }
                addOperandComment(hcf, safepoint.pcOffset, "{safepoint}");
            }
        }
        for (DataPatch site : ciTM.dataReferences) {
            hcf.addOperandComment(site.pcOffset, "{" + site.constant + "}");
        }

        return hcf.toEmbeddedString();
    }

    private static void addExceptionHandlersComment(CiTargetMethod tm, CiHexCodeFile hcf) {
        if (!tm.exceptionHandlers.isEmpty()) {
            String nl = CiHexCodeFile.NEW_LINE;
            StringBuilder buf = new StringBuilder("------ Exception Handlers ------").append(nl);
            for (CiTargetMethod.ExceptionHandler e : tm.exceptionHandlers) {
                buf.append("    ").
                    append(e.pcOffset).append(" -> ").
                    append(e.handlerPos).
                    append("  ").append(e.exceptionType == null ? "<any>" : e.exceptionType).
                    append(nl);
            }
            hcf.addComment(0, buf.toString());
        }
    }

    private static void addOperandComment(CiHexCodeFile hcf, int pos, String comment) {
        String oldValue = hcf.addOperandComment(pos, comment);
        assert oldValue == null : "multiple comments for operand of instruction at " + pos + ": " + comment + ", " + oldValue;
    }

    protected static class CachedInvocation {
        public CachedInvocation(Value[] args, CiConstant result) {
            this.args = args;
            this.result = result;
        }
        protected final Value[] args;
        protected final CiConstant result;
    }

    /**
     * Cache to speed up compile-time folding. This works as an invocation of a {@linkplain FOLD foldable}
     * method is guaranteed to be idempotent with respect its arguments.
     */
    private final ConcurrentHashMap<MethodActor, CachedInvocation> cache = new ConcurrentHashMap<MethodActor, CachedInvocation>();

    protected final boolean canonicalizeFoldableMethods() {
        return true;
    }

    @Override
    public CiConstant invoke(RiMethod method, CiMethodInvokeArguments args) {
        if (canonicalizeFoldableMethods() && method.isResolved()) {
            MethodActor methodActor = (MethodActor) method;
            if (Actor.isDeclaredFoldable(methodActor.flags())) {
                Value[] values;
                int length = methodActor.descriptor().argumentCount(!methodActor.isStatic());
                if (length == 0) {
                    values = Value.NONE;
                } else {
                    values = new Value[length];
                    for (int i = 0; i < length; ++i) {
                        CiConstant arg = args.nextArg();
                        if (arg == null) {
                            return null;
                        }
                        Value value;
                        // Checkstyle: stop
                        switch (arg.kind) {
                            case Boolean: value = BooleanValue.from(arg.asBoolean()); break;
                            case Byte:    value = ByteValue.from((byte) arg.asInt()); break;
                            case Char:    value = CharValue.from((char) arg.asInt()); break;
                            case Double:  value = DoubleValue.from(arg.asDouble()); break;
                            case Float:   value = FloatValue.from(arg.asFloat()); break;
                            case Int:     value = IntValue.from(arg.asInt()); break;
                            case Long:    value = LongValue.from(arg.asLong()); break;
                            case Object:  value = ReferenceValue.from(arg.asObject()); break;
                            case Short:   value = ShortValue.from((short) arg.asInt()); break;
                            case Word:    value = WordValue.from(Address.fromLong(arg.asLong())); break;
                            default: throw new IllegalArgumentException();
                        }
                        // Checkstyle: resume
                        values[i] = value;
                    }
                }

                if (!isHosted()) {
                    CachedInvocation cachedInvocation = cache.get(methodActor);
                    if (cachedInvocation != null && Arrays.equals(values, cachedInvocation.args)) {
                        return cachedInvocation.result;
                    }
                }

                try {
                    // attempt to invoke the method
                    CiConstant result = methodActor.invoke(values).asCiConstant();
                    // set the result of this instruction to be the result of invocation
                    notifyMethodFolded();

                    if (!isHosted()) {
                        cache.put(methodActor, new CachedInvocation(values, result));
                    }

                    return result;
                    // note that for void, we will have a void constant with value null
                } catch (IllegalAccessException e) {
                    // folding failed; too bad
                } catch (InvocationTargetException e) {
                    // folding failed; too bad
                } catch (ExceptionInInitializerError e) {
                    // folding failed; too bad
                }
                return null;
            }
        }
        return null;
    }

    protected void notifyMethodFolded() {
    }

    public CiConstant foldWordOperation(int opcode, CiMethodInvokeArguments args) {
        CiConstant x = args.nextArg();
        CiConstant y = args.nextArg();
        switch (opcode) {
            case WDIV:
                return CiConstant.forWord(Address.fromLong(x.asLong()).dividedBy(Address.fromLong(y.asLong())).toLong());
            case WDIVI:
                return CiConstant.forWord(Address.fromLong(x.asLong()).dividedBy(y.asInt()).toLong());
            case WREM:
                return CiConstant.forWord(Address.fromLong(x.asLong()).remainder(Address.fromLong(y.asLong())).toLong());
            case WREMI:
                return CiConstant.forInt(Address.fromLong(x.asLong()).remainder(y.asInt()));
        }
        return null;
    }

    public Object registerCompilerStub(CiTargetMethod ciTargetMethod, String name) {
        return new Stub(CompilerStub, name, ciTargetMethod);
    }

    public RiType getType(Class<?> javaClass) {
        return ClassActor.fromJava(javaClass);
    }

    public RiType asRiType(CiKind kind) {
        return getType(kind.toJavaClass());
    }

    public RiType getTypeOf(CiConstant constant) {
        if (constant.kind.isObject()) {
            Object o = constant.asObject();
            if (o != null) {
                return ClassActor.fromJava(o.getClass());
            }
        }
        return null;
    }

    public Object asJavaObject(CiConstant c) {
        return c.asObject();
    }

    public Class<?> asJavaClass(CiConstant c) {
        Object o = c.asObject();
        if (o instanceof Class) {
            return (Class) o;
        }
        return null;
    }

    @Override
    public Object asCallTarget(Object target) {
        if (target instanceof CiRuntimeCall) {
            target = MaxRuntimeCalls.getClassMethodActor((CiRuntimeCall) target);
        } else if (target == null) {
            target = CallTarget.TEMPLATE_CALL;
        }
        CallTarget.assertSupportedTarget(target);
        return target;
    }

    public boolean isExceptionType(RiType type) {
        return type.isSubtypeOf(getType(Throwable.class));
    }

    public RiSnippets getSnippets() {
        if (snippets == null) {
            snippets = new MaxSnippets(this);
        }
        return snippets;
    }

    public boolean areConstantObjectsEqual(CiConstant x, CiConstant y) {
        assert x.kind.isObject() && y.kind.isObject();
        return x.asObject() == y.asObject();
    }

    public RiRegisterConfig getRegisterConfig(RiMethod method) {
        return vm().registerConfigs.getRegisterConfig((ClassMethodActor) method);
    }

    /**
     * Reserves a word at the bottom of the frame for saving an overwritten return address during the deoptimization process.
     */
    public int getCustomStackAreaSize() {
        return STACK_SLOT_SIZE;
    }

    public boolean supportsArrayIntrinsics() {
        return false;
    }

    public int getArrayLength(CiConstant array) {
        return Array.getLength(array.asObject());
    }
}
