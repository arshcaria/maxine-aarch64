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
/**
 * @author Ben L. Titzer
 * @author Bernd Mathiske
 * @author Doug Simon
 * @author Paul Caprioli
 */
#include <alloca.h>
#include <unistd.h>

#include "os.h"
#include "isa.h"
#include "virtualMemory.h"

#include <stdlib.h>
#include <memory.h>
#include <errno.h>
#include <string.h>
#include <signal.h>
#include <limits.h>
#include "log.h"
#include "image.h"
#include "jni.h"
#include "word.h"
#include "messenger.h"
#include "mutex.h"
#include "threads.h"
#include "threadLocals.h"
#include <sys/mman.h>

#if (os_DARWIN || os_LINUX)
#   include <pthread.h>
#   include <errno.h>
    typedef pthread_t Thread;
#define thread_current() ((Thread) pthread_self())
#elif os_SOLARIS
#   include <thread.h>
    typedef thread_t Thread;
#define thread_current() (thr_self())
#elif os_GUESTVMXEN
#   include "guestvmXen.h"
    typedef guestvmXen_Thread Thread;
#define thread_current() (guestvmXen_get_current())
#endif

/**
 * The native mutex associated with VmThreadMap.ACTIVE which serves the role
 * of being a global lock for thread creation and GC.
 */
Mutex globalThreadAndGCLock;

/**
 * Gets the address and size of the calling thread's stack.
 *
 * @param stackBase the base (i.e. lowest) address of the stack is returned in this argument
 * @param stackSize the size of the stack is returned in this argument
 */
void thread_getStackInfo(Address *stackBase, Size* stackSize) {
#if os_SOLARIS
    stack_t stackInfo;
    int result = thr_stksegment(&stackInfo);
    if (result != 0) {
        log_exit(result, "Could not get the address and size of the current thread [%s]", strerror(result));
    }
    *stackSize = stackInfo.ss_size;
    *stackBase = (Address) stackInfo.ss_sp - stackInfo.ss_size;
#elif os_LINUX
    pthread_attr_t attr;
    int result = pthread_getattr_np(pthread_self(), &attr);
    if (result != 0) {
        log_exit(result, "Could not get the address and size of the current thread [%s]", strerror(result));
    }
    result = pthread_attr_getstack(&attr, (void**) stackBase, (size_t *) stackSize);
    if (result != 0) {
        log_exit(11, "Cannot locate current stack attributes [%s]", strerror(result));
    }
    pthread_attr_destroy(&attr);
#elif os_DARWIN
    pthread_t self = pthread_self();
    void *stackTop = pthread_get_stackaddr_np(self);
    if (stackTop == NULL) {
        log_exit(11, "Cannot get current stack address");
    }
    *stackSize = pthread_get_stacksize_np(self);
    if (*stackSize == 0) {
        log_exit(11, "Cannot get current stack size");
    }
    *stackBase = (Address) stackTop - *stackSize;
#elif os_GUESTVMXEN
    stackinfo_t stackInfo;
    guestvmXen_get_stack_info(&stackInfo);
    *stackBase = stackInfo.ss_sp - stackInfo.ss_size;
    *stackSize = stackInfo.ss_size;
#else
    c_UNIMPLEMENTED();
#endif

}

/* Forward declaration. */
void *thread_run(void *arg);

/**
 * OS-specific thread creation.
 *
 * @param id the identifier reserved in the thread map for the thread to be started
 * @param stackSize the requested size of the thread's stack
 * @param priority the initial priority of the thread
 * @return the native thread handle (e.g. pthread_self()) of the started thread or 0 in the case of failure
 */
static Thread thread_create(jint id, Size stackSize, int priority) {
    Thread thread;
#if !os_GUESTVMXEN
    int error;
#endif

    if (virtualMemory_pageAlign(stackSize) != stackSize) {
        log_println("thread_create: thread stack size must be a multiple of the OS page size (%d)", virtualMemory_getPageSize());
        return (Thread) 0;
    }

#if log_THREADS
    log_println("thread_create: id = %d, stack size = %ld", id, stackSize);
#endif

#if os_GUESTVMXEN
    /* allocate stack if necessary */
    Address stackBase = (Address) guestvmXen_allocate_stack(ntl, stackSize);
    if (stackBase == 0) {
        free(ntl);
        return NULL;
    }
    thread = guestvmXen_create_thread_with_stack("java_thread",
    	(void (*)(void *)) thread_run,
		(void*) stackBase,
	    stackSize,
		priority,
		(void*) ntl);
#elif (os_LINUX || os_DARWIN)
    pthread_attr_t attributes;
    pthread_attr_init(&attributes);

    if (stackSize < PTHREAD_STACK_MIN) {
        stackSize = PTHREAD_STACK_MIN;
    }

    /* The thread library allocates the stack and sets the guard page at the bottom
     * of the stack which we use for the triggered thread locals. */
    pthread_attr_setstacksize(&attributes, stackSize);
    pthread_attr_setguardsize(&attributes, virtualMemory_getPageSize());
    pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_JOINABLE);

    error = pthread_create(&thread, &attributes, (void *(*)(void *)) thread_run, (void *) (Address) id);
    pthread_attr_destroy(&attributes);
    if (error != 0) {
        log_println("pthread_create failed with error: %d", error);
        return (Thread) 0;
    }
#elif os_SOLARIS
    if (stackSize < thr_min_stack()) {
        stackSize = thr_min_stack();
    }
    /* The thread library allocates the stack and sets the guard page at the bottom
     * of the stack which we use for the triggered thread locals. */
    error = thr_create((void *) NULL, (size_t) stackSize, thread_run, (void *) id, THR_NEW_LWP | THR_BOUND, &thread);
    if (error != 0) {
        log_println("thr_create failed with error: %d [%s]", error, strerror(error));
        return (Thread) 0;
    }
#else
    c_UNIMPLEMENTED();
#endif
    return thread;
}

void *thread_self() {
    return (void *) thread_current();
}

static int thread_join(Thread thread) {
    int error;
#if (os_DARWIN || os_LINUX)
    int status;
    error = pthread_join(thread, (void **) &status);
#elif os_SOLARIS
    void *status;
    error = thr_join(thread, NULL, &status);
#elif os_GUESTVMXEN
    error = guestvmXen_thread_join(thread);
#else
    c_UNIMPLEMENTED();
#endif

    if (error != 0) {
        log_println("Joining thread %p with thread %p failed (%s %d)", thread_current(), thread, strerror(error), error);
    }
    return error;
}

/**
 * The start routine called by the native threading library once the new thread starts.
 *
 * @param arg the identifier reserved in the thread map for the thread to be started
 */
void *thread_run(void *arg) {

    jint id = (int) (Address) arg;
    Address nativeThread = (Address) thread_current();

#if log_THREADS
    log_println("thread_run: BEGIN t=%p", nativeThread);
#endif

    Address refMap;
    Address tlBlock = threadLocalsBlock_create(id, &refMap);
    ThreadLocals tl = THREAD_LOCALS_FROM_TLBLOCK(tlBlock);
    NativeThreadLocals ntl = NATIVE_THREAD_LOCALS_FROM_TLBLOCK(tlBlock);

    VmThreadRunMethod method = image_offset_as_address(VmThreadRunMethod, vmThreadRunMethodOffset);

#if log_THREADS
    log_print("thread_run: id=%d, t=%p, calling method: ", id, nativeThread);
    void image_printAddress(Address address);
    image_printAddress((Address) method);
    log_println("");
#endif
    Address stackEnd = ntl->stackBase + ntl->stackSize;
    (*method)(id,
              nativeThread,
              ntl->stackBase,
              stackEnd,
              tl,
              refMap,
              ntl->stackYellowZone);

#if log_THREADS
    log_println("thread_run: END t=%p", nativeThread);
#endif
    /* Successful thread exit */
    return NULL;
}

int thread_attachCurrent(void **penv, JavaVMAttachArgs* args, boolean daemon) {
    Address nativeThread = (Address) thread_current();
#if log_THREADS
    log_println("thread_attach: BEGIN t=%p", nativeThread);
#endif
    int result;
    if (threadLocals_current() != 0) {
        // If the thread has been attached, this operation is a no-op
        extern JNIEnv *currentJniEnv();
        *penv = (void *) currentJniEnv();
#if log_THREADS
    log_println("thread_attach: END t=%p (already attached)", nativeThread);
#endif
        return JNI_OK;
    }

    /* Give the thread a temporary id based on its native handle. The id must
     * be negative to indicate that it is not (yet) in the thread map. */
    jint handle = (jint) nativeThread;
    jint id = handle < 0 ? handle : -handle;

    Address refMap;
    Address tlBlock = threadLocalsBlock_create(id, &refMap);
    ThreadLocals tl = THREAD_LOCALS_FROM_TLBLOCK(tlBlock);
    NativeThreadLocals ntl = NATIVE_THREAD_LOCALS_FROM_TLBLOCK(tlBlock);

    /* Grab the global thread and GC lock so that:
     *   1. We can safely add this thread to the thread list and thread map.
     *   2. We are blocked if a GC is currently underway. Once we have the lock,
     *      GC is blocked and cannot occur until we completed the upcall to
     *      VmThread.attach().
     */
    mutex_enter(globalThreadAndGCLock);

    ThreadLocals threadLocalsListHead = image_read_value(ThreadLocals, threadLocalsListHeadOffset);

    // insert this thread locals into the list
    setConstantThreadLocal(tl, FORWARD_LINK, threadLocalsListHead);
    setConstantThreadLocal(threadLocalsListHead, BACKWARD_LINK, tl);
    // at the head
    image_write_value(ThreadLocals, threadLocalsListHeadOffset, tl);

#if log_THREADS
    log_println("thread %3d: forwardLink = %p (id=%d)", id, threadLocalsListHead, getThreadLocal(int, threadLocalsListHead, ID));
#endif
    Address stackEnd = ntl->stackBase + ntl->stackSize;
    VmThreadAttachMethod method = image_offset_as_address(VmThreadAttachMethod, vmThreadAttachMethodOffset);
    result = (*method)(nativeThread,
              (Address) args->name,
              (Address) args->group,
              daemon,
              ntl->stackBase,
              stackEnd,
              tl,
              refMap,
              ntl->stackYellowZone);
    mutex_exit(globalThreadAndGCLock);

#if log_THREADS
    log_println("thread_attach: id=%d, t=%p", id, nativeThread);
#endif

    if (result == JNI_OK) {
        *penv = (JNIEnv *) getThreadLocalAddress(tl, JNI_ENV);
    } else {
        if (result == JNI_EDETACHED) {
            log_println("Cannot attach thread to a VM whose main thread has exited");
        }
        *penv = NULL;
    }
    return result;
}

int thread_detachCurrent() {
    Address tlBlock = threadLocalsBlock_current();
    if (tlBlock == 0) {
        // If the thread has been detached, this operation is a no-op
#if log_THREADS
    log_println("thread_detach: END (already detached)");
#endif
        return JNI_OK;
    }
    threadLocalsBlock_setCurrent(0);
    threadLocalsBlock_destroy(tlBlock);
    return JNI_OK;
}

/**
 * Declared in VmThreadMap.java.
 */
void nativeSetGlobalThreadAndGCLock(Mutex mutex) {
#if log_THREADS
    log_println("Global thread lock mutex: %p", mutex);
#endif
    globalThreadAndGCLock = mutex;
}

/*
 * Create a thread.
 * @C_FUNCTION - called from Java
 */
Address nativeThreadCreate(jint id, Size stackSize, jint priority) {
    return (Address) thread_create(id, stackSize, priority);
}

/*
 * Join a thread.
 * @C_FUNCTION - called from Java
 */
jboolean nonJniNativeJoin(Address thread) {
#if log_THREADS
    log_println("BEGIN nativeJoin: %p", thread);
#endif
    if (thread == 0L) {
        return false;
    }
    jboolean result = thread_join((Thread) thread) == 0;
#if log_THREADS
    log_println("END nativeJoin: %p", thread);
#endif
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_sun_max_vm_thread_VmThread_nativeJoin(JNIEnv *env, jclass c, Address thread) {
	return nonJniNativeJoin(thread);
}

JNIEXPORT void JNICALL
Java_com_sun_max_vm_thread_VmThread_nativeYield(JNIEnv *env, jclass c) {
#if os_SOLARIS
    thr_yield();
#elif os_DARWIN
    sched_yield();
#elif os_LINUX
    pthread_yield();
#elif os_GUESTVMXEN
    guestvmXen_yield();
#else
    c_UNIMPLEMENTED();
#endif
}

JNIEXPORT void JNICALL
Java_com_sun_max_vm_thread_VmThread_nativeInterrupt(JNIEnv *env, jclass c, Address nativeThread) {
#if log_MONITORS
    log_println("Interrupting thread %p", nativeThread);
#endif
#if os_SOLARIS
    // Signals the thread
    int result = thr_kill(nativeThread, SIGUSR1);
    if (result != 0) {
        log_exit(11, "Error sending signal SIGUSR1 to native thread %p", nativeThread);
    }
#elif os_LINUX || os_DARWIN
    // Signals the thread
    int result = pthread_kill((pthread_t) nativeThread, SIGUSR1);
    if (result != 0) {
        log_exit(11, "Error sending signal SIGUSR1 to native thread %p", nativeThread);
    }
#elif os_GUESTVMXEN
	guestvmXen_interrupt((void*) nativeThread);
#else
    c_UNIMPLEMENTED();
 #endif
}

jboolean thread_sleep(jlong numberOfMilliSeconds) {
#if os_GUESTVMXEN
    return guestvmXen_sleep(numberOfMilliSeconds * 1000000);
#else
    struct timespec time, remainder;

    time.tv_sec = numberOfMilliSeconds / 1000;
    time.tv_nsec = (numberOfMilliSeconds % 1000) * 1000000;
    int value = nanosleep(&time, &remainder);

    if (value == -1) {
        int error = errno;
        if (error != EINTR && error != 0) {
            log_println("Call to nanosleep failed (other than by being interrupted): %s [remaining sec: %d, remaining nano sec: %d]", strerror(error), remainder.tv_sec, remainder.tv_nsec);
        }
    }
    return value;
#endif
}

void nonJniNativeSleep(long numberOfMilliSeconds) {
    thread_sleep(numberOfMilliSeconds);
}

JNIEXPORT jboolean JNICALL
Java_com_sun_max_vm_thread_VmThread_nativeSleep(JNIEnv *env, jclass c, jlong numberOfMilliSeconds) {
    return thread_sleep(numberOfMilliSeconds);
}

JNIEXPORT void JNICALL
Java_com_sun_max_vm_thread_VmThread_nativeSetPriority(JNIEnv *env, jclass c, Address nativeThread, jint priority) {
#if os_SOLARIS
    int err = thr_setprio(nativeThread, priority);
    c_ASSERT(err != ESRCH);
    c_ASSERT(err != EINVAL);
#elif os_GUESTVMXEN
    guestvmXen_set_priority((void *) nativeThread, priority);
#else
    //    log_println("nativeSetPriority %d ignored!", priority);
#endif
}
