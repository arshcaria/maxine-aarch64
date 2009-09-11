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
package com.sun.max.vm.compiler.c1x;

import java.io.*;
import java.util.*;

import com.sun.c1x.ci.*;
import com.sun.c1x.ci.CiTargetMethod.*;
import com.sun.c1x.ri.*;
import com.sun.max.vm.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.collect.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.annotate.*;
import com.sun.max.collect.*;
import com.sun.max.lang.*;
import com.sun.max.platform.*;
import com.sun.max.unsafe.*;

/**
 * This class implements a {@link com.sun.max.vm.compiler.target.TargetMethod target method} for
 * the Maxine VM that represents a compiled method generated by C1X.
 *
 * @author Ben L. Titzer
 * @author Thomas Wuerthinger
 */
public class C1XTargetMethod extends TargetMethod {

    private int[] exceptionPositionsToCatchPositions;
    private ClassActor[] exceptionClassActors;
    private byte[] referenceMap;
    private int referenceRegisterCount = -1;

    @PROTOTYPE_ONLY
    private CiTargetMethod prototypingCiTargetMethod;

    public C1XTargetMethod(RuntimeCompilerScheme compilerScheme, ClassMethodActor classMethodActor, CiTargetMethod ciTargetMethod) {
        super(classMethodActor, compilerScheme,  VMConfiguration.target().targetABIsScheme().optimizedJavaABI());
        init(ciTargetMethod);
    }

    public C1XTargetMethod(RuntimeCompilerScheme compilerScheme, String stubName, CiTargetMethod ciTargetMethod) {
        super(stubName, compilerScheme,  VMConfiguration.target().targetABIsScheme().optimizedJavaABI());
        init(ciTargetMethod);
    }

    private void init(CiTargetMethod ciTargetMethod) {

        if (MaxineVM.isPrototyping()) {
            // Save the target method for later gathering of calls
            this.prototypingCiTargetMethod = ciTargetMethod;
        }

        initCodeBuffer(ciTargetMethod);
        initFrameLayout(ciTargetMethod);
        initStopPositions(ciTargetMethod);
        initExceptionTable(ciTargetMethod);
    }

    private int neededBytes(int value) {
        return ((value - 1) / Bytes.SIZE) + 1;
    }

    private int registerReferenceMapBytes() {
        assert referenceRegisterCount != -1 : "register size not yet initialized";
        return neededBytes(referenceRegisterCount);
    }

    private int frameReferenceMapBits() {
        return frameSize() / VMConfiguration.hostOrTarget().platform.wordWidth().numberOfBytes;
    }

    private int frameReferenceMapBytes() {
        return neededBytes(frameReferenceMapBits());
    }

    private int totalReferenceMapBytes() {
        return registerReferenceMapBytes() + frameReferenceMapBytes();
    }

    private void setRegisterReferenceMapBit(int stopIndex, int registerIndex) {
        assert registerIndex >= 0 && registerIndex < referenceRegisterCount;
        int byteIndex = stopIndex * totalReferenceMapBytes() + frameReferenceMapBytes();
        ByteArrayBitMap.set(referenceMap, byteIndex, registerReferenceMapBytes(), registerIndex);
    }

    private void setFrameReferenceMapBit(int stopIndex, int slotIndex) {
        assert slotIndex >= 0 && slotIndex < frameSize();
        int byteIndex = stopIndex * totalReferenceMapBytes();
        ByteArrayBitMap.set(referenceMap, byteIndex, frameReferenceMapBytes(), slotIndex);
    }

    private boolean isRegisterReferenceMapBitSet(int stopIndex, int registerIndex) {
        assert registerIndex >= 0 && registerIndex < referenceRegisterCount;
        int byteIndex = stopIndex * totalReferenceMapBytes() + frameReferenceMapBytes();
        return ByteArrayBitMap.isSet(referenceMap, byteIndex, registerReferenceMapBytes(), registerIndex);
    }

    private boolean isFrameReferenceMapBitSet(int stopIndex, int slotIndex) {
        assert slotIndex >= 0 && slotIndex < frameSize();
        int byteIndex = stopIndex * totalReferenceMapBytes();
        return ByteArrayBitMap.isSet(referenceMap, byteIndex, frameReferenceMapBytes(), slotIndex);
    }

    private void initCodeBuffer(CiTargetMethod ciTargetMethod) {

        // Create the arrays for the scalar and the object reference literals
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Object> objectReferences = new ArrayList<Object>();
        int[] relativeDataPos = serializeLiterals(ciTargetMethod, output, objectReferences);
        byte[] scalarLiterals = output.toByteArray();
        Object[] referenceLiterals = objectReferences.toArray();

        // Allocate and set the code and data buffer
        final TargetBundleLayout targetBundleLayout = new TargetBundleLayout(scalarLiterals.length, referenceLiterals.length, ciTargetMethod.targetCodeSize);
        Code.allocate(targetBundleLayout, this);
        this.setData(scalarLiterals, referenceLiterals, ciTargetMethod.targetCode);

        // Patch relative instructions in the code buffer
        patchInstructions(targetBundleLayout, ciTargetMethod, relativeDataPos);
    }

    private int[] serializeLiterals(CiTargetMethod ciTargetMethod, ByteArrayOutputStream output, List<Object> objectReferences) {
        Endianness endianness = Platform.hostOrTarget().endianess();
        int[] relativeDataPos = new int[ciTargetMethod.dataPatchSites.size()];
        int z = 0;
        int currentPos = 0;
        for (DataPatchSite site : ciTargetMethod.dataPatchSites) {

            final CiConstant data = site.data;
            relativeDataPos[z] = currentPos;

            try {

                switch (data.basicType) {

                    case Double:
                        endianness.writeLong(output, Double.doubleToLongBits(data.asDouble()));
                        currentPos += Long.SIZE / Byte.SIZE;
                        break;

                    case Float:
                        endianness.writeInt(output, Float.floatToIntBits(data.asFloat()));
                        currentPos += Integer.SIZE / Byte.SIZE;
                        break;

                    case Int:
                        endianness.writeInt(output, data.asInt());
                        currentPos += Integer.SIZE / Byte.SIZE;
                        break;

                    case Long:
                        endianness.writeLong(output, data.asLong());
                        currentPos += Long.SIZE / Byte.SIZE;
                        break;

                    case Object:
                        objectReferences.add(data.asObject());
                        break;

                    default:
                        throw new IllegalArgumentException("Unknown constant type!");
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Align on word boundary
            while (currentPos % Platform.hostOrTarget().wordWidth().numberOfBytes != 0) {
                output.write(0);
                currentPos++;
            }

            z++;
        }

        return relativeDataPos;
    }

    private void patchInstructions(TargetBundleLayout targetBundleLayout, CiTargetMethod ciTargetMethod, int[] relativeDataPositions) {

        Offset codeStart = targetBundleLayout.cellOffset(TargetBundleLayout.ArrayField.code);

        Offset dataDiff = Offset.zero();
        if (this.scalarLiterals != null) {
            Offset dataStart = targetBundleLayout.cellOffset(TargetBundleLayout.ArrayField.scalarLiterals);
            dataDiff = dataStart.minus(codeStart).asOffset();
        }

        Offset referenceDiff = Offset.zero();
        if (this.referenceLiterals() != null) {
            Offset referenceStart = targetBundleLayout.cellOffset(TargetBundleLayout.ArrayField.referenceLiterals);
            referenceDiff = referenceStart.minus(codeStart).asOffset();
        }


        int objectReferenceIndex = 0;
        int refSize = Platform.hostOrTarget().wordWidth().numberOfBytes;

        int z = 0;
        for (DataPatchSite site : ciTargetMethod.dataPatchSites) {

            switch (site.data.basicType) {

                case Double: // fall through
                case Float: // fall through
                case Int: // fall through
                case Long:
                    patchRelativeInstruction(site.codePos, dataDiff.plus(relativeDataPositions[z] - site.codePos).toInt());
                    break;

                case Object:
                    patchRelativeInstruction(site.codePos, referenceDiff.plus(objectReferenceIndex * refSize - site.codePos).toInt());
                    objectReferenceIndex++;
                    break;

                default:
                    throw new IllegalArgumentException("Unknown constant type!");
            }

            z++;
        }
    }

    private void patchRelativeInstruction(int codePos, int displacement) {
        X86InstructionDecoder.patchRelativeInstruction(code(), codePos, displacement);
    }

    private void initFrameLayout(CiTargetMethod ciTargetMethod) {
        this.referenceRegisterCount = ciTargetMethod.referenceRegisterCount();
        this.setFrameSize(ciTargetMethod.frameSize());
        this.setRegisterRestoreEpilogueOffset(ciTargetMethod.registerRestoreEpilogueOffset);
    }

    private void initStopPositions(CiTargetMethod ciTargetMethod) {


        int numberOfIndirectCalls = ciTargetMethod.indirectCallSites.size();
        int numberOfSafepoints = ciTargetMethod.safepointRefMaps.size();
        int totalStopPositions = ciTargetMethod.directCallSites.size() + numberOfIndirectCalls + numberOfSafepoints;

        referenceMap = new byte[totalReferenceMapBytes() * totalStopPositions];

        int z = 0;
        int[] stopPositions = new int[totalStopPositions];
        Object[] directCallees = new Object[ciTargetMethod.directCallSites.size()];

        for (CallSite site : ciTargetMethod.directCallSites) {
            initStopPosition(ciTargetMethod, z, stopPositions, site.codePos, site.registerMap, site.stackMap);

            if (site.globalStubID != null) {
                TargetMethod globalStubMethod = (TargetMethod) site.globalStubID;
                assert globalStubMethod != null;
                directCallees[z] = globalStubMethod;
            } else {
                final ClassMethodActor cma = getClassMethodActor(site.runtimeCall, site.method);
                assert cma != null : "unresolved direct call!";
                directCallees[z] = cma;
            }

            assert directCallees[z] != null;

            z++;
        }

        for (CallSite site : ciTargetMethod.indirectCallSites) {
            initStopPosition(ciTargetMethod, z, stopPositions, site.codePos, site.registerMap, site.stackMap);
            z++;
        }

        for (SafepointRefMap safepoint : ciTargetMethod.safepointRefMaps) {
            initStopPosition(ciTargetMethod, z, stopPositions, safepoint.codePos, safepoint.registerMap, safepoint.stackMap);
            z++;
        }

        this.setStopPositions(stopPositions, directCallees, numberOfIndirectCalls, numberOfSafepoints);
    }

    private void initStopPosition(CiTargetMethod ciTargetMethod, int index, int[] stopPositions, int codePos, boolean[] registerMap, boolean[] stackMap) {
        stopPositions[index] = codePos;

        if (registerMap != null) {
            initRegisterMap(index, registerMap);
        }

        if (stackMap != null) {
            initStackMap(index, stackMap);
        }
    }

    private void initRegisterMap(int index, boolean[] registerMap) {
        assert registerMap.length == referenceRegisterCount;
        for (int i = 0; i < registerMap.length; i++) {
            if (registerMap[i]) {
                setRegisterReferenceMapBit(index, i);
            }
        }
    }

    private void initStackMap(int index, boolean[] stackMap) {
        assert stackMap.length == frameReferenceMapBits();
        for (int i = 0; i < stackMap.length; i++) {
            if (stackMap[i]) {
                setFrameReferenceMapBit(index, i);
            }
        }
    }

    private void initExceptionTable(CiTargetMethod ciTargetMethod) {
        if (ciTargetMethod.exceptionHandlers.size() > 0) {
            exceptionPositionsToCatchPositions = new int[ciTargetMethod.exceptionHandlers.size() * 2];
            exceptionClassActors = new ClassActor[ciTargetMethod.exceptionHandlers.size()];

            int z = 0;
            for (ExceptionHandler handler : ciTargetMethod.exceptionHandlers) {
                exceptionPositionsToCatchPositions[z * 2] = handler.codePos;
                exceptionPositionsToCatchPositions[z * 2 + 1] = handler.handlerPos;
                exceptionClassActors[z] = (handler.exceptionType == null) ? null : ((MaxRiType) handler.exceptionType).classActor;
                z++;
            }
        }
    }

    @Override
    public final void patchCallSite(int callOffset, Word callEntryPoint) {
        final int displacement = callEntryPoint.asAddress().minus(codeStart().plus(callOffset)).toInt();
        X86InstructionDecoder.patchRelativeInstruction(code(), callOffset, displacement);
    }

    @Override
    public void forwardTo(TargetMethod newTargetMethod) {
        forwardTo(this, newTargetMethod);
    }

    // TODO: (tw) Get rid of these!!!!!!!
    private static final int RJMP = 0xe9;

    public static void forwardTo(TargetMethod oldTargetMethod, TargetMethod newTargetMethod) {
        assert oldTargetMethod != newTargetMethod;
        assert oldTargetMethod.abi().callEntryPoint() != CallEntryPoint.C_ENTRY_POINT;

        final long newOptEntry = CallEntryPoint.OPTIMIZED_ENTRY_POINT.in(newTargetMethod).asAddress().toLong();
        final long newJitEntry = CallEntryPoint.JIT_ENTRY_POINT.in(newTargetMethod).asAddress().toLong();

        patchCode(oldTargetMethod, CallEntryPoint.OPTIMIZED_ENTRY_POINT.offsetFromCodeStart(), newOptEntry, RJMP);
        patchCode(oldTargetMethod, CallEntryPoint.JIT_ENTRY_POINT.offsetFromCodeStart(), newJitEntry, RJMP);
    }

    private static void patchCode(TargetMethod targetMethod, int offset, long target, int controlTransferOpcode) {
        final Pointer callSite = targetMethod.codeStart().plus(offset);
        final long displacement = (target - (callSite.toLong() + 5L)) & 0xFFFFFFFFL;
        if (MaxineVM.isPrototyping()) {
            final byte[] code = targetMethod.code();
            code[offset] = (byte) controlTransferOpcode;
            code[offset + 1] = (byte) displacement;
            code[offset + 2] = (byte) (displacement >> 8);
            code[offset + 3] = (byte) (displacement >> 16);
            code[offset + 4] = (byte) (displacement >> 24);
        } else {
            // TODO: Patching code is probably not thread safe!
            //       Patch location must not straddle a cache-line (32-byte) boundary.
            FatalError.check(true | callSite.isWordAligned(), "Method " + targetMethod.classMethodActor().format("%H.%n(%p)") + " entry point is not word aligned.");
            // The read, modify, write below should be changed to simply a write once we have the method entry point alignment fixed.
            final Word patch = callSite.readWord(0).asAddress().and(0xFFFFFF0000000000L).or((displacement << 8) | controlTransferOpcode);
            callSite.writeWord(0, patch);
        }
    }

    @Override
    public Address throwAddressToCatchAddress(boolean isTopFrame, Address throwAddress, Class<? extends Throwable> throwableClass) {

        final int throwOffset = throwAddress.minus(codeStart).toInt();
        for (int i = 0; i < getExceptionHandlerCount(); i++) {
            int codePos = getCodePosAt(i);
            int catchPos = getCatchPosAt(i);
            ClassActor catchType = getTypeAt(i);

            if (codePos == throwOffset && checkType(throwableClass, catchType)) {
                return codeStart.plus(catchPos);
            }
        }
        return Address.zero();
    }

    private boolean isCatchAll(ClassActor type) {
        return type == null;
    }

    private boolean checkType(Class<? extends Throwable> throwableClass, ClassActor catchType) {
        return isCatchAll(catchType) || catchType.isAssignableFrom(ClassActor.fromJava(throwableClass));
    }

    private int getCodePosAt(int i) {
        return exceptionPositionsToCatchPositions[i * 2];
    }

    private int getCatchPosAt(int i) {
        return exceptionPositionsToCatchPositions[i * 2 + 1];
    }

    private ClassActor getTypeAt(int i) {
        return exceptionClassActors[i];
    }

    private int getExceptionHandlerCount() {
        return exceptionClassActors == null ? 0 : exceptionClassActors.length;
    }

    @Override
    @PROTOTYPE_ONLY
    public void gatherCalls(AppendableSequence<MethodActor> directCalls, AppendableSequence<MethodActor> virtualCalls, AppendableSequence<MethodActor> interfaceCalls) {

        // iterate over direct calls
        for (CiTargetMethod.CallSite site : prototypingCiTargetMethod.directCallSites) {
            if (site.runtimeCall != null) {
                directCalls.append(getClassMethodActor(site.runtimeCall, site.method));
            } else if (site.method != null) {
                MethodActor methodActor = ((MaxRiMethod) site.method).asMethodActor("gatherCalls()");
                directCalls.append(methodActor);
            }
        }

        // iterate over all the calls and append them to the appropriate lists
        for (CiTargetMethod.CallSite site : prototypingCiTargetMethod.indirectCallSites) {
            assert site.method != null;
            if (site.method.isLoaded()) {
                MethodActor methodActor = ((MaxRiMethod) site.method).asMethodActor("gatherCalls()");
                if (site.method.holder().isInterface()) {
                    interfaceCalls.append(methodActor);
                } else {
                    virtualCalls.append(methodActor);
                }
            }
        }
    }

    private ClassMethodActor getClassMethodActor(CiRuntimeCall runtimeCall, RiMethod method) {

        if (method != null) {
            final MaxRiMethod maxMethod = (MaxRiMethod) method;
            return maxMethod.asClassMethodActor("directCall()");
        }

        assert runtimeCall != null : "A call can either be a call to a method or a runtime call";
        return C1XRuntimeCalls.getClassMethodActor(runtimeCall);
    }

}
