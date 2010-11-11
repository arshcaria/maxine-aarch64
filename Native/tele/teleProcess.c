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
#include <string.h>
#include <stdlib.h>
#include <alloca.h>

#include "c.h"
#include "log.h"
#include "jni.h"
#include "threadLocals.h"
#include "teleProcess.h"

static jmethodID jniGatherThreadID = NULL;

void teleProcess_jniGatherThread(JNIEnv *env, jobject teleProcess, jobject threadList, jlong localHandle, ThreadState_t state, jlong instructionPointer, TLA tla) {
    if (jniGatherThreadID == NULL) {
        jclass c = (*env)->GetObjectClass(env, teleProcess);
        c_ASSERT(c != NULL);
        jniGatherThreadID = (*env)->GetMethodID(env, c, "jniGatherThread", "(Ljava/util/List;IJJIJJJJJI)V");
        c_ASSERT(jniGatherThreadID != NULL);
    }
    const int size = tlaSize();
    TLA noTLA = (TLA) alloca(size);
    NativeThreadLocalsStruct noNativeThreadLocalsStruct;
    NativeThreadLocals ntl;
    if (tla == 0) {
        tla = noTLA;
        ntl = &noNativeThreadLocalsStruct;
        memset((void *) tla, 0, size);
        memset(ntl, 0, sizeof(NativeThreadLocalsStruct));
        jint id = localHandle;
        /* Make id negative to indicate no thread locals were available for the thread.
         * This will be the case for a native thread or a Java thread that has not yet
         * executed past the point in VmThread.run() where it is added to the active
         * thread list.
         */
        tla_store(tla, ID, id < 0 ? id : -id);
        tla_store(tla, NATIVE_THREAD_LOCALS, ntl);
    } else {
        ntl = tla_load(NativeThreadLocals, tla, NATIVE_THREAD_LOCALS);
    }

#if defined(_LP64)
    tele_log_println("Gathered thread[id=%d, localHandle=%lu, handle=%p, pc=%p, stackBase=%p, stackEnd=%p, stackSize=%lu, tlb=%p, tlbSize=%ld, tlaSize=%d]",
#else
    // for 32 bit hosting
    tele_log_println("Gathered thread[id=%d, localHandle=%llu, handle=%llx, pc=%llx, stackBase=%llx, stackEnd=%llx, stackSize=%llu, tlb=%llx, tlbSize=%lld, tlaSize=%d]",
#endif
                    tla_load(int, tla, ID),
                    localHandle,
                    ntl->handle,
                    instructionPointer,
                    ntl->stackBase,
                    ntl->stackBase + ntl->stackSize,
                    ntl->stackSize,
                    ntl->tlBlock,
                    ntl->tlBlockSize,
                    size);

    (*env)->CallVoidMethod(env, teleProcess, jniGatherThreadID, threadList,
                    tla_load(int, tla, ID),
                    localHandle,
                    ntl->handle,
                    state,
                    instructionPointer,
                    ntl->stackBase,
                    ntl->stackSize,
                    ntl->tlBlock,
                    ntl->tlBlockSize,
                    size);
}

static boolean isTLAForStackPointer(ProcessHandle ph, Address stackPointer, Address tla, TLA tlaCopy, NativeThreadLocals ntlCopy) {
    Address ntl;

    readProcessMemory(ph, tla, tlaCopy, tlaSize());
    ntl = tla_load(Address, tlaCopy, NATIVE_THREAD_LOCALS);
    readProcessMemory(ph, ntl, ntlCopy, sizeof(NativeThreadLocalsStruct));
    tla_store(tlaCopy, NATIVE_THREAD_LOCALS, ntlCopy);
#if log_TELE
    log_print("teleProcess_findTLA(%p): ", stackPointer);
    tla_println(tlaCopy);
#endif
    Address stackBase = ntlCopy->stackBase;
    Size stackSize = ntlCopy->stackSize;
    return stackBase <= stackPointer && stackPointer < (stackBase + stackSize);
}

TLA teleProcess_findTLA(ProcessHandle ph, Address tlaList, Address primordialTLA, Address stackPointer, TLA tlaCopy, NativeThreadLocals ntlCopy) {
    memset((void *) tlaCopy, 0, tlaSize());
    memset((void *) ntlCopy, 0, sizeof(NativeThreadLocalsStruct));

    if (tlaList != 0) {
        Address tla = tlaList;
        while (tla != 0) {
            if (isTLAForStackPointer(ph, stackPointer, tla, tlaCopy, ntlCopy)) {
                return tlaCopy;
            }
            tla = tla_load(Address, tlaCopy, FORWARD_LINK);
        };
    }
    if (primordialTLA != 0) {
        if (isTLAForStackPointer(ph, stackPointer, primordialTLA, tlaCopy, ntlCopy)) {
            return tlaCopy;
        }
    }
    return 0;
}

int teleProcess_read(ProcessHandle ph, JNIEnv *env, jclass c, jlong src, jobject dst, jboolean isDirectByteBuffer, jint offset, jint length) {
    Word bufferWord;
    void* dstBuffer;
    size_t size = (size_t) length;
    if (isDirectByteBuffer) {
        // Direct ByteBuffer: get the address of the buffer and adjust it by offset
        dstBuffer = (*env)->GetDirectBufferAddress(env, dst);
        if (dstBuffer == 0) {
            log_println("Failed to get address from NIO direct buffer");
            return -1;
        }
        dstBuffer = (jbyte *) dstBuffer + offset;
    } else {
        if (size >  sizeof(Word)) {
            // More than a word's worth of bytes: allocate a buffer
            dstBuffer = (void *) malloc(length * sizeof(jbyte));
            if (dstBuffer == 0) {
                log_println("Failed to malloc byte array of %d bytes", length);
                return -1;
            }
        } else {
            // Less than or equal to a word's woth of bytes: use stack memory
            dstBuffer = (void *) &bufferWord;
        }
    }

    // Do the read
    jint bytesRead = readProcessMemory(ph, src, dstBuffer, size);

    if (!isDirectByteBuffer) {
        if (bytesRead > 0) {
            (*env)->SetByteArrayRegion(env, dst, offset, bytesRead, dstBuffer);
        }
        if (dstBuffer != (void *) &bufferWord) {
            free(dstBuffer);
        }
    }
    return bytesRead;
}

int teleProcess_write(ProcessHandle ph, JNIEnv *env, jclass c, jlong dst, jobject src, jboolean isDirectByteBuffer, jint offset, jint length) {
    Word bufferWord;
    void* srcBuffer;
    size_t size = (size_t) length;
    if (isDirectByteBuffer) {
        srcBuffer = (*env)->GetDirectBufferAddress(env, src);
        if (srcBuffer == 0) {
            log_println("Failed to get address from NIO direct buffer");
            return -1;
        }
        srcBuffer = (jbyte *) srcBuffer + offset;
    } else {
        if (size >  sizeof(Word)) {
            srcBuffer = malloc(length * sizeof(jbyte));
            if (srcBuffer == 0) {
                log_println("failed to malloc byte array of %d bytes", length);
                return -1;
            }
        } else {
            // Less than or equal to a word's woth of bytes: use stack memory
            srcBuffer = (void *) &bufferWord;
        }
        (*env)->GetByteArrayRegion(env, src, offset, length, srcBuffer);
        if ((*env)->ExceptionOccurred(env) != NULL) {
            log_println("failed to copy %d bytes from byte array into buffer", length);
            return -1;
        }
    }

    int result = writeProcessMemory(ph, dst, srcBuffer, size);

    if (!isDirectByteBuffer) {
        if (srcBuffer != (void *) &bufferWord) {
            free(srcBuffer);
        }
    }
    return result;
}

