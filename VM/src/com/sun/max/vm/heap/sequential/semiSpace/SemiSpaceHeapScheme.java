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
package com.sun.max.vm.heap.sequential.semiSpace;

import static com.sun.max.vm.VMConfiguration.*;
import static com.sun.max.vm.VMOptions.*;
import static com.sun.max.vm.heap.Heap.*;
import static com.sun.max.vm.thread.VmThreadLocal.*;

import java.lang.management.*;

import com.sun.management.*;
import com.sun.management.GarbageCollectorMXBean;
import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.platform.*;
import com.sun.max.unsafe.*;
import com.sun.max.util.timer.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.debug.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.management.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.tele.*;
import com.sun.max.vm.thread.*;

/**
 * A simple semi-space scavenger heap.
 *
 * @author Bernd Mathiske
 * @author Sunil Soman
 * @author Doug Simon
 * @author Hannes Payer
 * @author Laurent Daynes
 * @Author Mick Jordan
 */
public final class SemiSpaceHeapScheme extends HeapSchemeWithTLAB implements CellVisitor {

    public static final String FROM_REGION_NAME = "Heap-From";
    public static final String TO_REGION_NAME = "Heap-To";
    public static final String FROM_GROW_REGION_NAME = "Heap-From-Grow";
    public static final String TO_GROW_REGION_NAME = "Heap-To-Grow";
    public static final String LINEAR_GROW_POLICY_NAME = "Linear";
    public static final String DOUBLE_GROW_POLICY_NAME = "Double";
    public static final String NO_GROW_POLICY_NAME = "None";


    /**
     * A VM option for specifying amount of memory to be reserved for allocating and raising an
     * OutOfMemoryError when insufficient memory is available to satisfy an allocation request.
     *
     * @see #safetyZoneSize
     */
    private static final VMIntOption safetyZoneSizeOption =
        register(new VMIntOption("-XX:OutOfMemoryZoneSize=", 6144,
            "Memory reserved to throw OutOfMemoryError. If using TLABs, then the actual memory reserved " +
            "is the maximum of this option's value and the size of a TLAB."), MaxineVM.Phase.PRISTINE);

    private static final VMStringOption growPolicyOption =
        register(new VMStringOption("-XX:HeapGrowPolicy=", false, DOUBLE_GROW_POLICY_NAME, "Grow policy for heap (Linear|Double)."), MaxineVM.Phase.STARTING);

    /**
     * Procedure used to update a reference so that it points to an object in 'toSpace'.
     */
    private final RefUpdater refUpdater = new RefUpdater();

    private final RefForwarder refForwarder = new RefForwarder();

    /**
     * The procedure that will identify all the GC roots except those in the boot heap and code regions.
     */
    private final SequentialHeapRootsScanner heapRootsScanner = new SequentialHeapRootsScanner(refUpdater);

    /**
     * Procedure used to verify a reference.
     */
    private final RefVerifier refVerifier = new RefVerifier();

    /**
     * A VM option for enabling extra checking of references. This should be disabled when running GC benchmarks.
     * It's enabled by default as the primary goal of this collector are simplicity and robustness,
     * not high performance.
     */
    private static final VMBooleanXXOption verifyReferencesOption =
        register(new VMBooleanXXOption("-XX:+VerifyReferences", "Do extra verification for each reference scanned by the GC."), MaxineVM.Phase.PRISTINE);

    private boolean verifyReferences;

    /**
     * Procedure used to verify GC root reference well-formedness.
     */
    private final SequentialHeapRootsScanner gcRootsVerifier = new SequentialHeapRootsScanner(refVerifier);

    private CollectHeap collectHeap;

    private LinearAllocationMemoryRegion fromSpace = null;
    private LinearAllocationMemoryRegion toSpace = null;

    /**
     * Used when {@linkplain #grow(GrowPolicy) growing} the heap.
     */
    private final LinearAllocationMemoryRegion growFromSpace = new LinearAllocationMemoryRegion(FROM_GROW_REGION_NAME);

    /**
     * Used when {@linkplain #grow(GrowPolicy) growing} the heap.
     */
    private final LinearAllocationMemoryRegion growToSpace = new LinearAllocationMemoryRegion(TO_GROW_REGION_NAME);

    /**
     * The amount of memory reserved for allocating and raising an OutOfMemoryError when insufficient
     * memory is available to satisfy an allocation request.
     *
     * @see #safetyZoneSizeOption
     */
    private int safetyZoneSize;

    private GrowPolicy growPolicy;
    private LinearGrowPolicy increaseGrowPolicy;

    /**
     * The global allocation limit (minus the {@linkplain #safetyZoneSize safety zone}).
     */
    private Address top;

    private final ResetTLAB resetTLAB = new ResetTLAB(){
        @Override
        protected void doBeforeReset(Pointer enabledVmThreadLocals, Pointer tlabMark, Pointer tlabEnd) {
            if (MaxineVM.isDebug()) {
                padTLAB(enabledVmThreadLocals, tlabMark, tlabEnd);
            }
        }
    };

    // Create timing facilities.
    private final TimerMetric clearTimer = new TimerMetric(new SingleUseTimer(HeapScheme.GC_TIMING_CLOCK));
    private final TimerMetric gcTimer = new TimerMetric(new SingleUseTimer(HeapScheme.GC_TIMING_CLOCK));
    private final TimerMetric rootScanTimer = new TimerMetric(new SingleUseTimer(HeapScheme.GC_TIMING_CLOCK));
    private final TimerMetric bootHeapScanTimer = new TimerMetric(new SingleUseTimer(HeapScheme.GC_TIMING_CLOCK));
    private final TimerMetric codeScanTimer = new TimerMetric(new SingleUseTimer(HeapScheme.GC_TIMING_CLOCK));
    private final TimerMetric immortalSpaceScanTimer = new TimerMetric(new SingleUseTimer(HeapScheme.GC_TIMING_CLOCK));
    private final TimerMetric copyTimer = new TimerMetric(new SingleUseTimer(HeapScheme.GC_TIMING_CLOCK));
    private final TimerMetric weakRefTimer = new TimerMetric(new SingleUseTimer(HeapScheme.GC_TIMING_CLOCK));

    private long collectionCount;
    private long accumulatedGCTime;
    private long lastGCTime;

    /**
     * A VM option for triggering a GC before every allocation.
     */
    static boolean GCBeforeAllocation;
    static {
        VMOptions.addFieldOption("-XX:", "GCBeforeAllocation", SemiSpaceHeapScheme.class,
            "Perform a garbage collection before every allocation from the global heap.", MaxineVM.Phase.PRISTINE);
    }

    public SemiSpaceHeapScheme(VMConfiguration vmConfiguration) {
        super(vmConfiguration);
    }

    @Override
    public void initialize(MaxineVM.Phase phase) {
        super.initialize(phase);

        if (MaxineVM.isHosted()) {
            collectHeap = new CollectHeap();
        }

        if (phase == MaxineVM.Phase.PRISTINE) {

            try {
                Heap.enableImmortalMemoryAllocation();
                fromSpace = new LinearAllocationMemoryRegion(FROM_REGION_NAME);
                toSpace = new LinearAllocationMemoryRegion(TO_REGION_NAME);
            } finally {
                Heap.disableImmortalMemoryAllocation();
            }

            allocateHeap();

            safetyZoneSize = Math.max(safetyZoneSizeOption.getValue(), initialTlabSize().toInt());

            top = toSpace.end().minus(safetyZoneSize);

            if (MaxineVM.isDebug()) {
                zapRegion(toSpace, "at GC initialization");
            }

            verifyReferences = MaxineVM.isDebug() || verifyReferencesOption.getValue();

            lastGCTime = System.currentTimeMillis();

            // From now on we can allocate

            InspectableHeapInfo.init(toSpace, fromSpace);
        } else if (phase == MaxineVM.Phase.STARTING) {
            final String growPolicy = growPolicyOption.getValue();
            if (growPolicy.equals(DOUBLE_GROW_POLICY_NAME)) {
                this.growPolicy = new DoubleGrowPolicy();
            } else if (growPolicy.equals(LINEAR_GROW_POLICY_NAME)) {
                this.growPolicy = new LinearGrowPolicy(growPolicy);
            } else if (growPolicy.equals(NO_GROW_POLICY_NAME)) {
                this.growPolicy = null;
            } else {
                Log.print("Unknown heap growth policy, using default policy");
                this.growPolicy = new DoubleGrowPolicy();
            }
            increaseGrowPolicy = new LinearGrowPolicy();
        }
    }

    private void allocateHeap() {
        boolean heapAllocationOk;
        final Size size = Heap.initialSize().dividedBy(2);
        Size heapAllocationSize = size;
        if (!Heap.gcDisabled()) {
            heapAllocationOk = !allocateSpace(fromSpace, size).isZero() && !allocateSpace(toSpace, size).isZero();
        } else {
            // If GC is disabled, then use all of -Xmx for toSpace
            heapAllocationSize = Heap.maxSize();
            heapAllocationOk = !allocateSpace(toSpace, heapAllocationSize).isZero();
        }

        if (!heapAllocationOk) {
            MaxineVM.reportPristineMemoryFailure("object heap", "allocate", heapAllocationSize);
        } else {
            if (Heap.verbose()) {
                Log.print("Allocated ");
                Log.print(heapAllocationSize.toLong());
                Log.println(" bytes of memory for object heap");
            }
        }
    }

    @INLINE
    private Address allocationMark() {
        return toSpace.mark().asAddress();
    }

    private static void startTimer(Timer timer) {
        if (Heap.traceGCTime()) {
            timer.start();
        }
    }

    private static void stopTimer(Timer timer) {
        if (Heap.traceGCTime()) {
            timer.stop();
        }
    }

    private final class RefForwarder implements SpecialReferenceManager.ReferenceForwarder {

        public boolean isReachable(Reference ref) {
            final Pointer origin = ref.toOrigin();
            if (fromSpace.contains(origin)) {
                final Reference forwardRef = Layout.readForwardRef(origin);
                if (forwardRef.isZero()) {
                    return false;
                }
            }
            return true;
        }

        public boolean isForwarding() {
            return true;
        }

        public Reference getForwardRefence(Reference ref) {
            final Pointer origin = ref.toOrigin();
            if (fromSpace.contains(origin)) {
                return Layout.readForwardRef(origin);
            }
            return ref;
        }
    }

    /**
     * A procedure to update a reference so that it points to an object in 'toSpace'.
     *
     * @see SemiSpaceHeapScheme#mapRef(Reference)
     */
    private final class RefUpdater extends PointerIndexVisitor {
        @Override
        public void visit(Pointer pointer, int wordIndex) {
            final Reference oldRef = pointer.getReference(wordIndex);
            final Reference newRef = mapRef(oldRef);
            if (newRef != oldRef) {
                pointer.setReference(wordIndex, newRef);
            }
        }
    }

    private final class RefVerifier extends PointerIndexVisitor {
        @Override
        public void visit(Pointer pointer, int index) {
            DebugHeap.verifyRefAtIndex(pointer, index, pointer.getReference(index), toSpace, null);
        }
    }

    /**
     * Routine that performs the actual garbage collection.
     */
    final class CollectHeap extends GCOperation {

        public CollectHeap() {
            super("CollectHeap");
        }

        @Override
        public void collect(int invocationCount) {
            try {
                VmThreadMap.ACTIVE.forAllThreadLocals(null, resetTLAB);

                // Pre-verification of the heap.
                verifyObjectSpaces("before GC");

                HeapScheme.Inspect.notifyGCStarted();

                vmConfig().monitorScheme().beforeGarbageCollection();

                final long startGCTime = System.currentTimeMillis();
                collectionCount++;
                startTimer(gcTimer);

                startTimer(clearTimer);
                swapSemiSpaces(); // Swap semi-spaces. From--> To and To-->From
                stopTimer(clearTimer);

                if (Heap.traceGCPhases()) {
                    Log.println("Scanning roots...");
                }
                startTimer(rootScanTimer);
                heapRootsScanner.run(); // Start scanning the reachable objects from my roots.
                stopTimer(rootScanTimer);

                if (Heap.traceGCPhases()) {
                    Log.println("Scanning boot heap...");
                }
                startTimer(bootHeapScanTimer);
                scanBootHeap();
                stopTimer(bootHeapScanTimer);

                if (Heap.traceGCPhases()) {
                    Log.println("Scanning code...");
                }
                startTimer(codeScanTimer);
                scanCode();
                stopTimer(codeScanTimer);

                if (Heap.traceGCPhases()) {
                    Log.println("Scanning immortal heap...");
                }
                startTimer(immortalSpaceScanTimer);
                scanImmortalHeap();
                stopTimer(immortalSpaceScanTimer);

                if (Heap.traceGCPhases()) {
                    Log.println("Moving reachable...");
                }

                startTimer(copyTimer);
                moveReachableObjects(toSpace.start().asPointer());
                stopTimer(copyTimer);

                if (Heap.traceGCPhases()) {
                    Log.println("Processing weak references...");
                }

                startTimer(weakRefTimer);
                SpecialReferenceManager.processDiscoveredSpecialReferences(refForwarder);
                stopTimer(weakRefTimer);
                stopTimer(gcTimer);

                // Bring the inspectable mark up to date, since it is not updated during the move.
                toSpace.mark.set(allocationMark()); // for debugging

                lastGCTime = System.currentTimeMillis();
                accumulatedGCTime += lastGCTime - startGCTime;

                vmConfig().monitorScheme().afterGarbageCollection();

                // Post-verification of the heap.
                verifyObjectSpaces("after GC");

                HeapScheme.Inspect.notifyGCCompleted();

                if (Heap.traceGCTime()) {
                    final boolean lockDisabledSafepoints = Log.lock();
                    Log.print("Timings (");
                    Log.print(TimerUtil.getHzSuffix(HeapScheme.GC_TIMING_CLOCK));
                    Log.print(") for GC ");
                    Log.print(invocationCount);
                    Log.print(": clear & initialize=");
                    Log.print(clearTimer.getLastElapsedTime());
                    Log.print(", root scan=");
                    Log.print(rootScanTimer.getLastElapsedTime());
                    Log.print(", boot heap scan=");
                    Log.print(bootHeapScanTimer.getLastElapsedTime());
                    Log.print(", code scan=");
                    Log.print(codeScanTimer.getLastElapsedTime());
                    Log.print(", copy=");
                    Log.print(copyTimer.getLastElapsedTime());
                    Log.print(", weak refs=");
                    Log.print(weakRefTimer.getLastElapsedTime());
                    Log.print(", total=");
                    Log.println(gcTimer.getLastElapsedTime());
                    Log.unlock(lockDisabledSafepoints);
                }
            } catch (Throwable throwable) {
                FatalError.unexpected("Exception during GC", throwable);
            }
        }
    }

    /**
     * Attempts to allocate memory of given size for given space.
     * If successful sets region start and size.
     */
    private static Address allocateSpace(LinearAllocationMemoryRegion space, Size size) {
        final Address base = VirtualMemory.allocate(size, VirtualMemory.Type.HEAP);
        if (!base.isZero()) {
            space.setStart(base);
            space.mark.set(base); // debugging
            space.setSize(size);
        }
        return base;
    }

    /**
     * Deallocates the memory associated with the given region.
     * Sets the region start to zero but does not change the size.
     */
    private static void deallocateSpace(MemoryRegion space) {
        VirtualMemory.deallocate(space.start(), space.size(), VirtualMemory.Type.HEAP);
        space.setStart(Address.zero());
    }

    /**
     * Copies the state of one space into another.
     * Used when growing the semispaces.
     */
    private static void copySpaceState(LinearAllocationMemoryRegion from, LinearAllocationMemoryRegion to) {
        to.setStart(from.start());
        to.mark.set(from.start());
        to.setSize(from.size());
    }

    private void swapSemiSpaces() {
        final Address oldFromSpaceStart = fromSpace.start();
        final Size oldFromSpaceSize = fromSpace.size();

        fromSpace.setStart(toSpace.start());
        fromSpace.setSize(toSpace.size());
        fromSpace.mark.set(toSpace.getAllocationMark()); // for debugging

        toSpace.setStart(oldFromSpaceStart);
        toSpace.setSize(oldFromSpaceSize);
        toSpace.mark.set(toSpace.start());  // for debugging

        //allocationMark.set(toSpace.start());
        top = toSpace.end();
        // If we are currently using the safety zone, we must not install it in the swapped space
        // as that could cause gcAllocate to fail trying to copying too much live data.
        if (!inSafetyZone) {
            top = top.minus(safetyZoneSize);
        }
    }

    public boolean isGcThread(Thread thread) {
        return thread instanceof VmOperationThread;
    }

    public int adjustedCardTableShift() {
        return -1;
    }

    public int auxiliarySpaceSize(int bootImageSize) {
        return 0;
    }

    public void initializeAuxiliarySpace(Pointer primordialVmThreadLocals, Pointer auxiliarySpace) {
    }

    private Size immediateFreeSpace() {
        return top.minus(allocationMark()).asSize();
    }

    /**
     * Maps a given reference to the reference of an object in 'toSpace'.
     * The action taken depends on which of the three following states {@code ref} is in:
     * <ul>
     * <li>Points to a not-yet-copied object in 'fromSpace'. The object is
     * copied and a forwarding pointer is installed in the header of
     * the source object (i.e. the one in 'fromSpace'). The reference of the
     * destination object (i.e the one in 'toSpace') is returned.</li>
     * <li>Points to a object in 'fromSpace' for which a copy in 'toSpace' exists.
     * The reference of the 'toSpace' copy is derived from the forwarding pointer and returned.</li>
     * <li>Points to a object in 'toSpace'. The value of {@code ref} is returned.</li>
     * </ul>
     *
     * @param ref a pointer to an object either in 'fromSpace' or 'toSpace'
     * @return the reference to the object in 'toSpace' obtained by the algorithm described above
     */
    private Reference mapRef(Reference ref) {
        final Pointer fromOrigin = ref.toOrigin();
        if (fromSpace.contains(fromOrigin)) {
            final Reference forwardRef = Layout.readForwardRef(fromOrigin);
            if (!forwardRef.isZero()) {
                return forwardRef;
            }
            if (verifyReferences) {
                DebugHeap.verifyRefAtIndex(Address.zero(), 0, ref, toSpace, fromSpace);
            }
            final Pointer fromCell = Layout.originToCell(fromOrigin);
            final Size size = Layout.size(fromOrigin);
            final Pointer toCell = gcAllocate(size);
            if (DebugHeap.isTagging()) {
                DebugHeap.writeCellTag(toCell);
            }

            if (Heap.traceGC()) {
                final boolean lockDisabledSafepoints = Log.lock();
                final Hub hub = UnsafeCast.asHub(Layout.readHubReference(ref).toJava());
                Log.print("Forwarding ");
                Log.print(hub.classActor.name.string);
                Log.print(" from ");
                Log.print(fromCell);
                Log.print(" to ");
                Log.print(toCell);
                Log.print(" [");
                Log.print(size.toInt());
                Log.println(" bytes]");
                Log.unlock(lockDisabledSafepoints);
            }

            Memory.copyBytes(fromCell, toCell, size);

            HeapScheme.Inspect.notifyObjectRelocated(fromCell, toCell);

            final Pointer toOrigin = Layout.cellToOrigin(toCell);
            final Reference toRef = Reference.fromOrigin(toOrigin);
            Layout.writeForwardRef(fromOrigin, toRef);


            return toRef;
        }
        return ref;
    }

    private void scanReferenceArray(Pointer origin) {
        final int length = Layout.readArrayLength(origin);
        for (int index = 0; index < length; index++) {
            final Reference oldRef = Layout.getReference(origin, index);
            final Reference newRef = mapRef(oldRef);
            if (newRef != oldRef) {
                Layout.setReference(origin, index, newRef);
            }
        }
    }

    /**
     * Visits a cell for an object that has been copied to 'toSpace' to
     * update any references in the object. If any of the references being
     * updated still point to objects in 'fromSpace', then those objects
     * will be copied as a side effect of the call to {@link #mapRef(Reference)}
     * that yields the updated value of a reference.
     *
     * @param cell a cell in 'toSpace' to whose references are to be updated
     */
    public Pointer visitCell(Pointer cell) {
        if (Heap.traceGC()) {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.print("Visiting cell ");
            Log.println(cell);
            Log.unlock(lockDisabledSafepoints);
        }
        final Pointer origin = Layout.cellToOrigin(cell);

        // Update the hub first so that is can be dereferenced to obtain
        // the reference map needed to find the other references in the object
        final Reference oldHubRef = Layout.readHubReference(origin);
        final Reference newHubRef = mapRef(oldHubRef);
        if (newHubRef != oldHubRef) {
            // The hub was copied
            Layout.writeHubReference(origin, newHubRef);
        }
        final Hub hub = UnsafeCast.asHub(newHubRef.toJava());

        // Update the other references in the object
        final SpecificLayout specificLayout = hub.specificLayout;
        if (specificLayout.isTupleLayout()) {
            TupleReferenceMap.visitReferences(hub, origin, refUpdater);
            if (hub.isSpecialReference) {
                SpecialReferenceManager.discoverSpecialReference(Reference.fromOrigin(origin));
            }
            return cell.plus(hub.tupleSize);
        }
        if (specificLayout.isHybridLayout()) {
            TupleReferenceMap.visitReferences(hub, origin, refUpdater);
        } else if (specificLayout.isReferenceArrayLayout()) {
            scanReferenceArray(origin);
        }
        return cell.plus(Layout.size(origin));
    }

    void moveReachableObjects(Pointer cell) {
        Pointer first = cell;
        while (cell.lessThan(allocationMark())) {
            cell = DebugHeap.checkDebugCellTag(first, cell);
            cell = visitCell(cell);
        }
    }

    void scanBootHeap() {
        Heap.bootHeapRegion.visitReferences(refUpdater);
    }

    void scanCode() {
        // References in the boot code region are immutable and only ever refer
        // to objects in the boot heap region.
        boolean includeBootCode = false;
        Code.visitCells(this, includeBootCode);
    }

    void scanImmortalHeap() {
        ImmortalHeap.visitCells(this);
    }

    private boolean cannotGrow() {
        return fromSpace.size().isZero() || fromSpace.size().greaterEqual(Heap.maxSize().dividedBy(2));
    }

    /**
     * Grow the semispaces to be of larger size.
     *
     * @param preGc true if prior to executing collector thread to copy {@link #toSpace} to (grown) {@link #fromSpace}
     * @return true iff both spaces can be grown
     */
    private boolean growSpaces(boolean preGc, GrowPolicy growPolicy) {
        if (preGc && Heap.verbose()) {
            Log.println("Trying to grow the heap...");
        }
        if (cannotGrow()) {
            if (preGc && Heap.verbose()) {
                Log.println("...failed, max heap size reached");
            }
            return false;
        }
        if (preGc) {
            // It is important to know now that we can allocate both spaces of the new size
            // and, if we cannot, to leave things as they are, so that the VM can continue
            // using the safety zone and perhaps then free enough space to continue.
            final Size size = Size.min(growPolicy.growth(fromSpace.size()), Heap.maxSize().dividedBy(2));
            if (preGc && Heap.verbose()) {
                Log.print("...new heap size: ");
                Log.println(size.toLong());
            }
            final Address fromBase = allocateSpace(growFromSpace, size);
            final Address tempBase = allocateSpace(growToSpace, size);
            if (fromBase.isZero() || tempBase.isZero()) {
                if (!fromBase.isZero()) {
                    deallocateSpace(growFromSpace);
                }
                if (Heap.verbose()) {
                    Log.println("...grow failed, can't allocate spaces");
                }
                return false;
            }
            // return memory in 'fromSpace'
            deallocateSpace(fromSpace);
            copySpaceState(growFromSpace, fromSpace);
        } else {
            // executing the collector thread swapped the spaces
            // so we are again updating _fromSpace but with _growToSpace.
            deallocateSpace(fromSpace);
            copySpaceState(growToSpace, fromSpace);
        }
        if (preGc && Heap.verbose()) {
            Log.println("...grow ok");
        }
        return true;
    }

    private void executeGC() {
        if (!Heap.gcDisabled()) {
            collectHeap.submit();
        }
    }

    private boolean grow(GrowPolicy growPolicy) {
        if (growPolicy == null || cannotGrow()) {
            return false;
        }
        boolean result = true;
        if (!growSpaces(true, growPolicy)) {
            result = false;
        } else {
            executeGC();
            result = growSpaces(false, growPolicy);
        }
        if (Heap.verbose()) {
            logSpaces();
        }
        return result;
    }

    public boolean collectGarbage(Size requestedFreeSpace) {
        if (requestedFreeSpace.toInt() == 0 || immediateFreeSpace().lessThan(requestedFreeSpace)) {
            executeGC();
        }
        if (immediateFreeSpace().greaterEqual(requestedFreeSpace)) {
            // check to see if we can reset safety zone
            if (inSafetyZone) {
                if (top.minus(allocationMark()).greaterThan(safetyZoneSize)) {
                    top = top.minus(safetyZoneSize);
                    inSafetyZone = false;
                }
            }
            return true;
        }
        while (grow(growPolicy)) {
            if (immediateFreeSpace().greaterEqual(requestedFreeSpace)) {
                return true;
            }
        }
        return false;
    }

    public Size reportFreeSpace() {
        return immediateFreeSpace();
    }

    public Size reportUsedSpace() {
        return allocationMark().minus(toSpace.start()).asSize();
    }

    /**
     * Allocates space for a cell being copied to 'to space' during GC.
     * Note that this allocation is only ever performed by the GC thread and so there's no
     * need to use compare-and-swap when updating the allocation mark.
     *
     * @param size the size of the cell being copied
     * @return the start of the allocated cell in 'to space'
     */
    private Pointer gcAllocate(Size size) {
        Pointer cell = allocationMark().asPointer();
        if (DebugHeap.isTagging()) {
            cell = cell.plusWords(1);
        }
        toSpace.mark.set(cell.plus(size));
        FatalError.check(allocationMark().lessThan(top), "GC allocation overflow");
        return cell;
    }

    private boolean inSafetyZone; // set after we have thrown OutOfMemoryError and are using the safety zone

    @Override
    protected void doBeforeTLABRefill(Pointer tlabAllocationMark, Pointer tlabEnd) {
        if (MaxineVM.isDebug()) {
            final Pointer enabledVmThreadLocals = VmThread.currentVmThreadLocals().getWord(SAFEPOINTS_ENABLED_THREAD_LOCALS.index).asPointer();
            padTLAB(enabledVmThreadLocals, tlabAllocationMark, tlabEnd);
        }
    }

    /**
     * Allocate a chunk of memory of the specified size and refill a thread's TLAB with it.
     * @param enabledVmThreadLocals the thread whose TLAB will be refilled
     * @param tlabSize the size of the chunk of memory used to refill the TLAB
     */
    private void allocateAndRefillTLAB(Pointer enabledVmThreadLocals, Size tlabSize) {
        Pointer tlab = retryAllocate(tlabSize, false);
        refillTLAB(enabledVmThreadLocals, tlab, tlabSize);
        if (Heap.traceAllocation()) {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.printCurrentThread(false);
            Log.print(": Allocated TLAB at ");
            Log.print(tlab);
            Log.print(" [TOP=");
            Log.print(tlab.plus(tlabSize));
            Log.print(", end=");
            Log.print(tlab.plus(tlabSize));
            Log.print(", size=");
            Log.print(tlabSize.toInt());
            Log.println("]");
            Log.unlock(lockDisabledSafepoints);
        }
    }
    /**
     * Handling of TLAB Overflow. This may refill the TLAB or allocate memory directly from the underlying heap.
     * This will always be taken when not using TLABs which is fine as the cost of the
     * compare-and-swap on {@link #allocationMark} will dominate.
     *
     * @param size the allocation size requested to the tlab
     * @param enabledVmThreadLocals
     * @param tlabMark allocation mark of the tlab
     * @param tlabEnd soft limit in the tlab to trigger overflow (may equals the actual end of the TLAB, depending on implementation).
     * @throws OutOfMemoryError if the allocation request cannot be satisfied.
     * @return the address of the allocated cell. Space for the {@linkplain DebugHeap#writeCellTag(Pointer) debug tag}
     *         will have been reserved immediately before the allocated cell.
     *
     */
    @Override
    @NEVER_INLINE
    protected Pointer handleTLABOverflow(Size size, Pointer enabledVmThreadLocals, Pointer tlabMark, Pointer tlabEnd) {
        // Should we refill the TLAB ?
        final TLABRefillPolicy refillPolicy = TLABRefillPolicy.getForCurrentThread(enabledVmThreadLocals);
        if (refillPolicy == null) {
            // No policy yet for the current thread. This must be the first time this thread uses a TLAB (it does not have one yet).
            FatalError.check(tlabMark.isZero(), "thread must not have a TLAB yet");

            if (!usesTLAB()) {
                // We're not using TLAB. So let's assign the never refill tlab policy.
                TLABRefillPolicy.setForCurrentThread(enabledVmThreadLocals, NEVER_REFILL_TLAB);
                return retryAllocate(size, true);
            }
            // Allocate an initial TLAB and a refill policy. For simplicity, this one is allocated from the TLAB (see comment below).
            final Size tlabSize = initialTlabSize();
            allocateAndRefillTLAB(enabledVmThreadLocals, tlabSize);
            // Let's do a bit of meta-circularity. The TLAB is refilled, and no-one except the current thread can use it.
            // So the TLAB allocation is going to succeed here
            TLABRefillPolicy.setForCurrentThread(enabledVmThreadLocals, new SimpleTLABRefillPolicy(tlabSize));
            // Now, address the initial request. Note that we may recurse down to handleTLABOverflow again here if the
            // request is larger than the TLAB size. However, this second call will succeed and allocate outside of the TLAB.
            return tlabAllocate(size);
        }
        final Size nextTLABSize = refillPolicy.nextTlabSize();
        if (size.greaterThan(nextTLABSize)) {
            // This couldn't be allocated in a TLAB, so go directly to direct allocation routine.
            // NOTE: this is where we always go if we don't use TLABs (the "never refill" TLAB policy
            // always return zero for the next TLAB size.
            return retryAllocate(size, true);
        }
        if (!refillPolicy.shouldRefill(size, tlabMark)) {
            // Size would fit in a new tlab, but the policy says we shouldn't refill the TLAB yet, so allocate directly in the heap.
            return retryAllocate(size, true);
        }
        // Refill TLAB and allocate (we know the request can be satisfied with a fresh TLAB and will therefore succeed).
        allocateAndRefillTLAB(enabledVmThreadLocals, nextTLABSize);
        return tlabAllocate(size);
    }

    /**
     * Allocates a cell from the global heap, using compare-and-swap to resolve any thread race condition.
     *
     * If allocation fails in this routine, then a garbage collection is performed. If a collection
     * does not free up enough space to satisfy the allocation request, then the heap is expanded.
     * If there is still not enough space after heap expansion, a {@link OutOfMemoryError} is thrown.
     *
     * @param size the requested cell size to be allocated
     * @param adjustForDebugTag specifies if an extra word is to be reserved before the cell for the debug tag word
     * @return the allocated and zeroed chunk
     */
    @NEVER_INLINE
    private Pointer retryAllocate(Size size, boolean adjustForDebugTag) {
        Pointer oldAllocationMark;
        Pointer cell;
        Address end;
        do {
            if (GCBeforeAllocation && !VmThread.isAttaching()) {
                Heap.collectGarbage(size);
            }
            oldAllocationMark = allocationMark().asPointer();
            cell = adjustForDebugTag ? DebugHeap.adjustForDebugTag(oldAllocationMark) : oldAllocationMark;
            end = cell.plus(size);
            while (end.greaterThan(top)) {
                if (!Heap.collectGarbage(size)) {
                    /*
                     * The OutOfMemoryError condition happens when we cannot satisfy a request after running a garbage collection and we
                     * cannot grow the heap any further (i.e. we are at the limit set by -Xmx). In that case we raise 'top' to the actual end of
                     * the active space and set 'inSafetyZone' to true. If this happens recursively we fast fail the VM via MaxineVM.native_exit.
                     * On the other hand if a subsequent GC manages to find enough space to allow the safety zone to be re-established
                     * we set 'inSafetyZone' to false.
                     */
                    if (inSafetyZone) {
                        FatalError.unexpected("Out of memory again after throwing OutOfMemoryError");
                    } else {
                        // Use the safety region to do the throw
                        top = top.plus(safetyZoneSize);
                        inSafetyZone = true;
                        // This new will now be ok
                        if (Heap.verbose()) {
                            Log.println("Throwing OutOfMemoryError");
                        }
                        throw new OutOfMemoryError();
                    }
                }
                oldAllocationMark = allocationMark().asPointer();
                cell = adjustForDebugTag ? DebugHeap.adjustForDebugTag(oldAllocationMark) : oldAllocationMark;
                end = cell.plus(size);
            }
        } while (toSpace.mark.compareAndSwap(oldAllocationMark, end) != oldAllocationMark);

        // Zero the allocated chunk before returning
        Memory.clearWords(cell, size.dividedBy(Word.size()).toInt());

        return cell;
    }

    /**
     * Inserts {@linkplain DebugHeap#writeCellPadding(Pointer, int) padding} into the unused portion of a thread's TLAB.
     * This is required if {@linkplain DebugHeap#verifyRegion(String, Pointer, Address, MemoryRegion, PointerOffsetVisitor) verification}
     * of the heap will be performed.
     *
     * @param enabledVmThreadLocals the pointer to the safepoint-enabled VM thread locals for the thread whose TLAB is
     *            to be padded
     */
    static void padTLAB(Pointer enabledVmThreadLocals, Pointer tlabMark, Pointer tlabTop) {
        final int padWords = DebugHeap.writeCellPadding(tlabMark, tlabTop);
        if (Heap.traceAllocation()) {
            final boolean lockDisabledSafepoints = Log.lock();
            final VmThread vmThread = UnsafeCast.asVmThread(enabledVmThreadLocals.getReference(VM_THREAD.index).toJava());
            Log.printThread(vmThread, false);
            Log.print(": Placed TLAB padding at ");
            Log.print(tlabMark);
            Log.print(" [words=");
            Log.print(padWords);
            Log.println("]");
            Log.unlock(lockDisabledSafepoints);
        }
    }

    public boolean contains(Address address) {
        return toSpace.contains(address);
    }

    public void runFinalization() {
    }

    @INLINE
    public boolean pin(Object object) {
        return false;
    }

    @INLINE
    public void unpin(Object object) {
    }

    @INLINE
    public boolean isPinned(Object object) {
        return false;
    }

    /**
     * Verifies invariants for memory spaces (i.e. heap, code caches, thread stacks) that contain
     * objects and/or object references.
     *
     * @param when a description of the current GC phase
     */
    private void verifyObjectSpaces(String when) {
        if (!MaxineVM.isDebug() && !verifyReferences) {
            return;
        }

        if (MaxineVM.isDebug()) {
            zapRegion(fromSpace, when);
        }

        if (Heap.traceGCPhases()) {
            Log.print("Verifying object spaces ");
            Log.println(when);
        }

        if (Heap.traceGCPhases()) {
            Log.println("Verifying object references on thread stacks...");
        }
        gcRootsVerifier.run();
        if (MaxineVM.isDebug()) {
            if (Heap.traceGCPhases()) {
                Log.println("Verifying heap objects...");
            }
            DebugHeap.verifyRegion(toSpace.regionName(), toSpace.start().asPointer(), allocationMark(), toSpace, refVerifier);
            if (Heap.traceGCPhases()) {
                Log.println("Verifying code objects...");
            }

            CodeRegion codeRegion = Code.getCodeManager().getRuntimeCodeRegion();
            if (!codeRegion.size().isZero()) {
                DebugHeap.verifyRegion(codeRegion.regionName(), codeRegion.start().asPointer(), codeRegion.getAllocationMark(), toSpace, refVerifier);
            }

        }

        if (Heap.traceGCPhases()) {
            Log.print("Verifying object spaces ");
            Log.print(when);
            Log.println(": DONE");
        }
    }

    private void zapRegion(MemoryRegion region, String when) {
        if (Heap.traceGCPhases()) {
            Log.print("Zapping region ");
            Log.print(region.regionName());
            Log.print(' ');
            Log.println(when);
        }
        Memory.zapRegion(region);
    }

    private void logSpaces() {
        if (Heap.verbose()) {
            logSpace(fromSpace);
            logSpace(toSpace);
            Log.print("top "); Log.print(top);
            Log.print(", allocation mark ");
            Log.println(allocationMark());
        }
    }

    private void logSpace(MemoryRegion space) {
        Log.print(space.regionName());
        Log.print(" start "); Log.print(space.start());
        Log.print(", end "); Log.print(space.end());
        Log.print(", size "); Log.print(space.size());
        Log.println("");
    }

    /**
     * Operation to shrink the heap.
     */
    final class ShrinkHeap extends VmOperation {

        boolean result;
        final Size amount;

        public ShrinkHeap(Size amount) {
            super("ShrinkHeap", null, Mode.Safepoint);
            this.amount = amount;
        }

        @Override
        protected void doIt() {
            final Size pageAlignedAmount = amount.asAddress().aligned(Platform.platform().pageSize).asSize().dividedBy(2);
            logSpaces();
            executeGC();
            if (immediateFreeSpace().greaterEqual(pageAlignedAmount)) {
                // give back part of the existing spaces
                if (Heap.verbose()) {
                    logSpaces();
                }
                final int amountAsInt = pageAlignedAmount.toInt();
                fromSpace.setSize(fromSpace.size().minus(amountAsInt));
                toSpace.setSize(toSpace.size().minus(amountAsInt));
                top = top.minus(amountAsInt);
                VirtualMemory.deallocate(fromSpace.end(), pageAlignedAmount, VirtualMemory.Type.HEAP);
                VirtualMemory.deallocate(toSpace.end(), pageAlignedAmount, VirtualMemory.Type.HEAP);
                logSpaces();
                result = true;
            }
        }
    }

    @Override
    public boolean decreaseMemory(Size amount) {
        HeapScheme.Inspect.notifyDecreaseMemoryRequested(amount);
        ShrinkHeap shrinkHeap = new ShrinkHeap(amount);
        synchronized (HEAP_LOCK) {
            shrinkHeap.submit();
        }
        return shrinkHeap.result;
    }

    final class GrowHeap extends VmOperation {

        boolean result;
        final Size amount;

        public GrowHeap(Size amount) {
            super("GrowHeap", null, Mode.Safepoint);
            this.amount = amount;
        }

        @Override
        protected void doIt() {
            /* The conservative assumption is that "amount" is the total amount that we could
             * allocate. Since we can't deallocate our existing spaces until we know we can allocate
             * the new ones, our new spaces cannot be greater than amount/2 in size.
             * This could be smaller than the existing spaces so we need to check.
             * It's unfortunate but that's the nature of the semispace scheme.
             */
            final Size pageAlignedAmount = amount.asAddress().aligned(Platform.platform().pageSize).asSize().dividedBy(2);
            if (pageAlignedAmount.greaterThan(fromSpace.size())) {
                // grow adds the current space size to the amount in the grow policy
                increaseGrowPolicy.setAmount(pageAlignedAmount.minus(fromSpace.size()));
                result = grow(increaseGrowPolicy);
            }
        }
    }

    @Override
    public boolean increaseMemory(Size amount) {
        HeapScheme.Inspect.notifyIncreaseMemoryRequested(amount);
        GrowHeap growHeap = new GrowHeap(amount);
        synchronized (HEAP_LOCK) {
            growHeap.submit();
        }
        return growHeap.result;
    }

    @Override
    public void finalize(MaxineVM.Phase phase) {
        if (MaxineVM.Phase.RUNNING == phase) {
            if (Heap.traceGCTime()) {
                final boolean lockDisabledSafepoints = Log.lock();
                Log.print("Timings (");
                Log.print(TimerUtil.getHzSuffix(HeapScheme.GC_TIMING_CLOCK));
                Log.print(") for all GC: clear & initialize=");
                Log.print(clearTimer.getElapsedTime());
                Log.print(", root scan=");
                Log.print(rootScanTimer.getElapsedTime());
                Log.print(", boot heap scan=");
                Log.print(bootHeapScanTimer.getElapsedTime());
                Log.print(", code scan=");
                Log.print(codeScanTimer.getElapsedTime());
                Log.print(", copy=");
                Log.print(copyTimer.getElapsedTime());
                Log.print(", weak refs=");
                Log.print(weakRefTimer.getElapsedTime());
                Log.print(", total=");
                Log.println(gcTimer.getElapsedTime());
                Log.unlock(lockDisabledSafepoints);
            }
        }
    }

    @INLINE
    public void writeBarrier(Reference from, Reference to) {
        // do nothing.
    }

    /**
     * The policy for how to grow the heap.
     */
    private abstract class GrowPolicy {
        /**
         * Returns the new size given the old.
         * @param current the current size
         * @return the new size
         */
        abstract Size growth(Size current);
    }

    private class DoubleGrowPolicy extends GrowPolicy {
        @Override
        Size growth(Size current) {
            return current.times(2);
        }
    }

    private class LinearGrowPolicy extends GrowPolicy {
        int amount;

        LinearGrowPolicy(String s) {
            amount = Heap.initialSize().toInt();
        }

        LinearGrowPolicy() {
        }

        @Override
        Size growth(Size current) {
            return current.plus(amount);
        }

        void setAmount(int amount) {
            this.amount = amount;
        }

        void setAmount(Size amount) {
            this.amount = amount.toInt();
        }
    }

    @Override
    public void disableImmortalMemoryAllocation() {
        final Pointer enabledVmThreadLocals = VmThread.currentVmThreadLocals().getWord(VmThreadLocal.SAFEPOINTS_ENABLED_THREAD_LOCALS.index).asPointer();
        enabledVmThreadLocals.setWord(IMMORTAL_ALLOCATION_ENABLED.index, Word.zero());
        if (usesTLAB()) {
            super.disableImmortalMemoryAllocation();
        }
    }

    @Override
    public void enableImmortalMemoryAllocation() {
        final Pointer enabledVmThreadLocals = VmThread.currentVmThreadLocals().getWord(VmThreadLocal.SAFEPOINTS_ENABLED_THREAD_LOCALS.index).asPointer();
        enabledVmThreadLocals.setWord(IMMORTAL_ALLOCATION_ENABLED.index, Word.allOnes());
        if (usesTLAB()) {
            super.enableImmortalMemoryAllocation();
        }
    }

    @Override
    public long maxObjectInspectionAge() {
        return System.currentTimeMillis() - lastGCTime;
    }

    @Override
    public GarbageCollectorMXBean getGarbageCollectorMXBean() {
        return new SemiSpaceGarbageCollectorMXBean();
    }

    private final class SemiSpaceGarbageCollectorMXBean extends HeapSchemeAdaptor.GarbageCollectorMXBeanAdaptor {
        private SemiSpaceGarbageCollectorMXBean() {
            super("SemiSpace");
            add(new SemiSpaceMemoryPoolMXBean(fromSpace, this));
            add(new SemiSpaceMemoryPoolMXBean(toSpace, this));
        }

        @Override
        public GcInfo getLastGcInfo() {
            return null;
        }

        @Override
        public long getCollectionCount() {
            return collectionCount;
        }

        @Override
        public long getCollectionTime() {
            return accumulatedGCTime;
        }

    }

    private final class SemiSpaceMemoryPoolMXBean extends MemoryPoolMXBeanAdaptor {
        SemiSpaceMemoryPoolMXBean(MemoryRegion region, MemoryManagerMXBean manager) {
            super(MemoryType.HEAP, region, manager);
        }
    }

}
