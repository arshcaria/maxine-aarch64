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
package com.sun.max.tele.grip;

import java.lang.ref.*;

import com.sun.max.collect.*;
import com.sun.max.tele.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.grip.*;

/**
 * @author Bernd Mathiske
 * @author Michael Van De Vanter
 */
public abstract class TeleGripScheme extends AbstractVMScheme implements GripScheme, TeleVMHolder {

    private TeleVM teleVM;
    private TeleRoots teleRoots;


    protected TeleGripScheme(VMConfiguration vmConfiguration) {
        super(vmConfiguration);
    }

    public void setTeleVM(TeleVM teleVM) {
        this.teleVM = teleVM;
        this.teleRoots = new TeleRoots(this);
    }

    public TeleVM teleVM() {
        return teleVM;
    }

    private VariableMapping<Long, WeakReference<RemoteTeleGrip>> rawGripToRemoteTeleGrip = HashMapping.createVariableEqualityMapping();

    /**
     * Called by MutableTeleGrip.finalize() and CanonicalConstantTeleGrip.finalize().
     */
    synchronized void finalizeCanonicalConstantTeleGrip(CanonicalConstantTeleGrip canonicalConstantTeleGrip) {
        rawGripToRemoteTeleGrip.remove(canonicalConstantTeleGrip.raw().toLong());
    }

    /**
     * Rebuild the canonicalization table when we know that the raw (remote) bits of the remote location have changed by GC.
     */
    private void refreshTeleGripCanonicalization() {
        final VariableMapping<Long, WeakReference<RemoteTeleGrip>> newMapping = HashMapping.createVariableEqualityMapping();
        for (WeakReference<RemoteTeleGrip> r : rawGripToRemoteTeleGrip.values()) {
            final RemoteTeleGrip remoteTeleGrip = r.get();
            if (remoteTeleGrip != null) {
                newMapping.put(remoteTeleGrip.raw().toLong(), r);
            }
        }
        rawGripToRemoteTeleGrip = newMapping;
    }

    /**
     * Update Inspector state after a change to the remote contents of the Inspector root table.
     */
    public void refresh() {
        // Update Inspector's local cache of the remote Inspector root table.
        teleRoots.refresh();
        // Rebuild the canonicalization map.
        refreshTeleGripCanonicalization();
    }

    /**
     * Returns a canonicalized tele grip associated with the given raw grip in the tele VM.
     */
    public synchronized TeleGrip makeTeleGrip(Address rawGrip) {
        if (rawGrip.isZero()) {
            return TeleGrip.ZERO;
        }
        final WeakReference<RemoteTeleGrip> r = rawGripToRemoteTeleGrip.get(rawGrip.toLong());
        RemoteTeleGrip remoteTeleGrip;
        if (r != null) {
            remoteTeleGrip = r.get();
            if (remoteTeleGrip != null) {
                return remoteTeleGrip;
            }
        }
        remoteTeleGrip = createTemporaryRemoteTeleGrip(rawGrip);
        if (teleVM.isValidOrigin(remoteTeleGrip.toOrigin())) {
            if (teleVM().containsInDynamicHeap(remoteTeleGrip.toOrigin())) {
                final int index = teleRoots.register(rawGrip);
                remoteTeleGrip = new MutableTeleGrip(this, index);
            } else {
                remoteTeleGrip = new CanonicalConstantTeleGrip(this, rawGrip);
            }
            rawGripToRemoteTeleGrip.put(rawGrip.toLong(), new WeakReference<RemoteTeleGrip>(remoteTeleGrip));
        }


        return remoteTeleGrip;
    }

    synchronized Address getRawGrip(MutableTeleGrip mutableTeleGrip) {
        return teleRoots.getRawGrip(mutableTeleGrip.index());
    }

    synchronized void finalizeMutableTeleGrip(int index) {
        rawGripToRemoteTeleGrip.remove(teleRoots.getRawGrip(index).toLong());
        teleRoots.unregister(index);
    }

    private final VariableMapping<Object, WeakReference<LocalTeleGrip>> objectToLocalTeleGrip = HashMapping.createVariableIdentityMapping();

    /**
     * Called by LocalTeleGrip.finalize().
     */
    synchronized void disposeCanonicalLocalGrip(Object object) {
        objectToLocalTeleGrip.remove(object);
    }

    /**
     * Returns a canonicalized local grip associated with the given local object.
     */
    public synchronized TeleGrip makeLocalGrip(Object object) {
        if (object == null) {
            return TeleGrip.ZERO;
        }
        final WeakReference<LocalTeleGrip> r = objectToLocalTeleGrip.get(object);
        if (r != null) {
            return r.get();
        }
        final LocalTeleGrip localTeleGrip = new LocalTeleGrip(this, object);
        objectToLocalTeleGrip.put(object, new WeakReference<LocalTeleGrip>(localTeleGrip));
        return localTeleGrip;
    }

    public RemoteTeleGrip createTemporaryRemoteTeleGrip(Address rawGrip) {
        return new TemporaryTeleGrip(this, rawGrip);
    }

    public abstract RemoteTeleGrip temporaryRemoteTeleGripFromOrigin(Word origin);

}
