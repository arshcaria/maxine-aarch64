/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele.method;

import java.io.*;
import java.text.*;
import java.util.*;

import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.memory.*;
import com.sun.max.tele.object.*;
import com.sun.max.tele.util.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.compiler.target.*;

/**
 * Singleton access to compiled code in the VM.
 * <p>
 * Much of the important state in the VM about the code cache is contained
 * in a singleton heap object, an instance of {@linkplain com.sun.max.vm.code.CodeManager CodeManager}.
 * <p>
 * The VM's cache allocates memory in one or more memory regions, above and beyond the boot code cache
 * that already exists in the boot image.
 * <p>
 * We attempt to track every method compilation, as soon as it can be discovered.  This is usually done
 * by various concrete subclasses of {@link VmCodeCacheRegion}, each of which specializes in one of the
 * code cache implementations in the VM.  At refresh time, each of those classes interrogates the VM
 * object that manages that code cache region in the VM, and loads summary information about any new
 * compilations instances of {@link TeleTargetMethod}.  Each newly created instance of {@link TeleTargetRegion}
 * registers itself here, so that it can be looked up by address.
 * <p>
 * Not every instance may be created in the usual way. There might be cases where we are inspecting an
 * instance of {@link TeleTargetMethod} as it is being created, which means that it may not yet have been
 * allocated memory in any code cache region. If a compilation is registered before allocation,
 * we set it aside and eventually register when the location information becomes known.
 *
 * @see com.sun.max.vm.code.CodeManager
 * @see TeleTargetMethod
 */
public final class VmCodeCacheAccess extends AbstractVmHolder implements TeleVMCache, MaxCodeCache, AllocationHolder {

    private static final int TRACE_VALUE = 1;

    private static VmCodeCacheAccess vmCodeCacheAccess;

    public static VmCodeCacheAccess make(TeleVM vm) {
        if (vmCodeCacheAccess == null) {
            vmCodeCacheAccess = new VmCodeCacheAccess(vm);
        }
        return vmCodeCacheAccess;
    }

    private final TimedTrace updateTracer;
    private final TimedTrace updateStatusTracer;

    private long lastUpdateEpoch = -1L;

    private final String entityName = "Code Cache";
    private final String entityDescription;

    /**
     * The object in the VM that manages cached compiled code.
     */
    private TeleCodeManager teleCodeManager;

    private final String bootCodeCacheRegionName;

    // The three code cache regions known to be allocated by the VM
    private VmCodeCacheRegion bootCodeCacheRegion = null;
    private VmSemiSpaceCodeCacheRegion dynamicBaselineCodeCacheRegion = null;
    private VmCodeCacheRegion dynamicOptCodeCacheRegion = null;

    /**
     * Unmodifiable list of all regions in which compiled methods are stored.
     */
    private List<MaxCodeCacheRegion> maxCodeCacheRegions = Collections.emptyList();

    /**
     * Regions in which compiled methods are stored, held as the implementation type.
     */
    private List<VmCodeCacheRegion> vmCodeCacheRegions = Collections.emptyList();

    /**
     * Keep track of memory regions allocated from the OS that are <em>owned</em> by the code cache.
     */
    private List<MaxMemoryRegion> allocations = new ArrayList<MaxMemoryRegion>();

    /**
     * A collection of {@link TeleTargetMethod} instances that
     * have been created for inspection (and registered here) before having been
     * allocated memory in the VM.  The registration of these will be completed during the
     * next update cycle after their location and size become known.
     */
    private final Set<TeleTargetMethod> unallocatedTeleTargetMethods = new HashSet<TeleTargetMethod>();

    /**
     * Creates the object that models the cache of machine code in the VM.
     * <p>
     * A subsequent call to {@link #initialize()} is required before this object is functional.
     */
    public VmCodeCacheAccess(TeleVM vm) {
        super(vm);
        assert vm().lockHeldByCurrentThread();
        final TimedTrace tracer = new TimedTrace(TRACE_VALUE, tracePrefix() + "creating");
        tracer.begin();
        this.entityDescription = "Storage managment in the " + vm().entityName() + " for method compilations";
        this.bootCodeCacheRegionName = vm().getString(fields().Code_CODE_BOOT_NAME.readReference(vm()));
        this.updateTracer = new TimedTrace(TRACE_VALUE, tracePrefix() + "updating");
        this.updateStatusTracer = new TimedTrace(TRACE_VALUE, tracePrefix() + "updating code cache status");
        tracer.end(null);
    }

    /**
     * Check whether the code cache is prepared to start registering entries, in particular
     * whether it knows about the code regions in the VM.
     *
     * @return whether the code cache is fully initialized and operational.
     */
    public boolean isInitialized() {
        return bootCodeCacheRegion != null;
    }

    /**
     * Completes the initialization of this object:  identifies the VM's code regions, and preloads
     * the cache of information about method compilations in the boot code region.
     */
    public void initialize(long epoch) {
        assert vm().lockHeldByCurrentThread();
        final TimedTrace tracer = new TimedTrace(TRACE_VALUE, tracePrefix() + "initializing");
        tracer.begin();

        teleCodeManager = (TeleCodeManager) objects().makeTeleObject(fields().Code_codeManager.readReference(vm()));
        bootCodeCacheRegion = new VmBootCodeCacheRegion(vm(), teleCodeManager.teleBootCodeRegion(), this);
        dynamicBaselineCodeCacheRegion = new VmSemiSpaceCodeCacheRegion(vm(), teleCodeManager.teleRuntimeBaselineCodeRegion(), this);
        dynamicOptCodeCacheRegion = new VmUnmanagedCodeCacheRegion(vm(), teleCodeManager.teleRuntimeOptCodeRegion(), this);

        vmCodeCacheRegions = Arrays.asList(bootCodeCacheRegion, dynamicBaselineCodeCacheRegion, dynamicOptCodeCacheRegion);
        maxCodeCacheRegions = Collections.unmodifiableList(new ArrayList<MaxCodeCacheRegion>(vmCodeCacheRegions));

        for (VmCodeCacheRegion codeCacheRegion : vmCodeCacheRegions) {
            allocations.add(codeCacheRegion.memoryRegion());
        }

        lastUpdateEpoch = epoch;
        tracer.end(null);
    }

    /** {@inheritDoc}
     * <p>
     * Updates the representation of every <strong>method compilation</strong> surrogate
     * (represented as instances of subclasses of {@link TeleTargetMethod},
     * in case any of the information in the VM's representation has changed since the previous update.  This should not be
     * attempted until all information about allocated regions that might contain objects has been updated.
     */
    public void updateCache(long epoch) {
        if (epoch > lastUpdateEpoch) {
            updateTracer.begin();
            assert vm().lockHeldByCurrentThread();
            for (TeleTargetMethod teleTargetMethod : unallocatedTeleTargetMethods) {
                if (!teleTargetMethod.getRegionStart().isZero() && teleTargetMethod.getRegionNBytes() != 0) {
                    // The compilation has been allocated memory in the VM since the last time we looked; complete its registration.
                    unallocatedTeleTargetMethods.remove(teleTargetMethod);
                    register(teleTargetMethod);
                }
            }
            for (VmCodeCacheRegion region : vmCodeCacheRegions) {
                region.updateCache(epoch);
            }
            lastUpdateEpoch = epoch;
            updateTracer.end(null);
        } else {
            Trace.line(TRACE_VALUE, tracePrefix() + "redundant update epoch=" + epoch);
        }
    }

    public String entityName() {
        return entityName;
    }

    public String entityDescription() {
        return entityDescription;
    }

    public MaxEntityMemoryRegion<MaxCodeCache> memoryRegion() {
        // The code cache has no memory allocation of its own, but
        // rather owns one or more code regions that have memory
        // allocated from the OS.
        return null;
    }

    public boolean contains(Address address) {
        return findCodeCacheRegion(address) != null;
    }

    public TeleObject representation() {
        return teleCodeManager;
    }

    public VmCodeCacheRegion bootCodeRegion() {
        return bootCodeCacheRegion;
    }

    public List<MaxCodeCacheRegion> codeCacheRegions() {
        return maxCodeCacheRegions;
    }

    public VmCodeCacheRegion findCodeCacheRegion(Address address) {
        for (VmCodeCacheRegion codeCacheRegion : vmCodeCacheRegions) {
            if (codeCacheRegion.memoryRegion().contains(address)) {
                return codeCacheRegion;
            }
        }
        return null;
    }

    public List<MaxMemoryRegion> memoryAllocations() {
        return allocations;
    }

    public void printSessionStats(PrintStream printStream, int indent, boolean verbose) {
        final String indentation = Strings.times(' ', indent);
        final NumberFormat formatter = NumberFormat.getInstance();
        int compilationCount = 0;
        int loadedCompilationCount = 0;
        for (VmCodeCacheRegion codeCacheRegion : vmCodeCacheRegions) {
            compilationCount += codeCacheRegion.compilationCount();
            loadedCompilationCount += codeCacheRegion.loadedCompilationCount();
        }
        printStream.print(indentation + "Total compilations registered: " + formatter.format(compilationCount));
        if (!unallocatedTeleTargetMethods.isEmpty()) {
            printStream.print(" (" + formatter.format(unallocatedTeleTargetMethods.size()) + " unallocated");
        }
        printStream.print(" (code loaded: " + formatter.format(loadedCompilationCount) + ")\n");
        printStream.print(indentation + "Regions: \n");
        for (VmCodeCacheRegion codeCacheRegion : vmCodeCacheRegions) {
            codeCacheRegion.printSessionStats(printStream, indent + 4, verbose);
        }
    }

    public void writeSummary(PrintStream printStream) {
        for (VmCodeCacheRegion codeCacheRegion : vmCodeCacheRegions) {
            codeCacheRegion.writeSummary(printStream);
        }
    }

    /**
     * Gets the name used by the VM to identify the distinguished
     * boot code region, determined by static inspection of the field
     * that holds the value in the VM.
     *
     * @return the name assigned to the VM's boot code memory region
     * @see Code
     */
    public String bootCodeRegionName() {
        return bootCodeCacheRegionName;
    }

    /**
     * Adds a {@link MaxCompilation} entry to the code registry, indexed by code address.
     * This should only be called from a constructor of a {@link TeleTargetMethod} subclass.
     *
     * @param teleTargetMethod the compiled method whose memory region is to be added to this registry
     * @throws IllegalArgumentException when the memory region of {@link teleTargetMethod} overlaps one already in this registry.
     */
    public void register(TeleTargetMethod teleTargetMethod) {
        if (teleTargetMethod.getRegionStart().isZero() || teleTargetMethod.getRegionNBytes() == 0) {
            // The compilation is being constructed, which is to say it exists in the VM but has not
            // had an allocation of memory assigned to it.
            unallocatedTeleTargetMethods.add(teleTargetMethod);
            TeleWarning.message(tracePrefix() + " unallocated TargetMethod registered");
        } else {
            // Find the code cache region in which the compilation has been allocated, and add it to
            // the registry we keep for that code region.
            final VmCodeCacheRegion codeCacheRegion = findCodeCacheRegion(teleTargetMethod.getRegionStart());
            assert codeCacheRegion != null;
            teleTargetMethod.setCodeCacheRegion(codeCacheRegion);
            codeCacheRegion.register(teleTargetMethod);
        }
    }

    public List<VmCodeCacheRegion> vmCodeCacheRegions() {
        return vmCodeCacheRegions;
    }

    /**
     * Refresh enough state to understand the state of the code cache, for example whether
     * new code cache regions have been created and whether
     * eviction is underway; this all gets done without necessarily doing any other cache updates.
     * <p>
     * This must be done before heap objects are refreshed, in particular so that the
     * code cache region status can be used to determine refresh behavior in instances of
     * {@link TargetMethod}.
     */
    public void updateStatus(long epoch) {
        updateStatusTracer.begin();
        for (VmCodeCacheRegion region : vmCodeCacheRegions) {
            region.updateStatus(epoch);
        }
        updateStatusTracer.end();
    }

    /**
     * @return whether eviction is underway in any part of the code cache
     */
    public boolean isInEviction() {
        for (VmCodeCacheRegion codeCacheRegion : vmCodeCacheRegions) {
            if (codeCacheRegion.isInEviction()) {
                return true;
            }
        }
        return false;
    }

}
