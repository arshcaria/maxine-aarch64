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
#include <sys/time.h>
#include <time.h>
#include <string.h>

#include "log.h"
#include "threads.h"

#include "condition.h"

void condition_initialize(Condition condition) {
#if log_CONDITION
    log_println("condition_initialize(%p, %p)", thread_self(), condition);
#endif
#if os_SOLARIS
    if (cond_init(condition, NULL, NULL) != 0) {
        c_FATAL();
    }
#elif os_LINUX || os_DARWIN
    if (pthread_cond_init(condition, NULL) != 0) {
        c_FATAL();
    }
#elif os_GUESTVMXEN
    *condition = guestvmXen_condition_create();
#else
#   error
#endif
}

void condition_destroy(Condition condition) {
#if log_CONDITION
    log_println("condition_destroy   (%p, %p)", thread_self(), condition);
#endif
#if os_SOLARIS
    if (cond_destroy(condition) != 0) {
        c_FATAL();
    }
#elif os_LINUX || os_DARWIN
    if (pthread_cond_destroy(condition) != 0) {
        c_FATAL();
    }
#endif
}

#if os_GUESTVMXEN
#   define ETIMEDOUT -1
#endif

/*
 * This function returns false if the thread was interrupted or an error occurred, true otherwise
 */
Boolean condition_wait(Condition condition, Mutex mutex) {
#if log_CONDITION
    log_println("condition_wait      (%p, %p, %p)", thread_self(), condition, mutex);
#endif
    int error;
#if (os_DARWIN || os_LINUX)
    error = pthread_cond_wait(condition, mutex);
    if (error == EINTR) {
#if log_CONDITION
        log_println("condition_wait: interrupted");
#endif
        return false;
    }
#elif os_SOLARIS
    error = cond_wait(condition, mutex);
    if (error == EINTR) {
#if log_CONDITION
        log_println("condition_wait: interrupted");
#endif
        return false;
    }
#elif os_GUESTVMXEN
    error = guestvmXen_condition_wait(*condition, *mutex, 0);
    if (error == 1) {
        return false;
    }
#endif
    if (error != 0) {
        log_println("condition_wait: unexpected error code %d", error);
        return false;
    }
#if log_CONDITION
   log_println("condition_wait: finished");
#endif
    return true;
}

#if os_LINUX
static struct timespec* compute_abstime(struct timespec* abstime, jlong millis) {
    if (millis < 0) {
        millis = 0;
    }
    struct timeval now;
    int status = gettimeofday(&now, NULL);
    c_ASSERT(status == 0);
    jlong seconds = millis / 1000;
    millis %= 1000;
    if (seconds > 50000000) { // see man cond_timedwait(3T)
        seconds = 50000000;
    }
    abstime->tv_sec = now.tv_sec  + seconds;
    long       usec = now.tv_usec + millis * 1000;
    if (usec >= 1000000) {
        abstime->tv_sec += 1;
        usec -= 1000000;
    }
    abstime->tv_nsec = usec * 1000;
    return abstime;
}
#endif

/*
 * This function returns false if the thread was interrupted or an error occurred, true otherwise
 */
Boolean condition_timedWait(Condition condition, Mutex mutex, Unsigned8 timeoutMilliSeconds) {
#if log_CONDITION
    log_println("condition_timedWait (%p, %p, %p, %d)", thread_self(), condition, mutex, timeoutMilliSeconds);
#endif
	int error;
	if (timeoutMilliSeconds > 0) {
#if (os_DARWIN || os_LINUX)
        struct timeval now;
        int status = gettimeofday(&now, NULL);
        c_ASSERT(status != -1);
        struct timespec abstime;
        compute_abstime(&abstime, timeoutMilliSeconds);
        error = pthread_cond_timedwait(condition, mutex, &abstime);
        if (error == ETIMEDOUT) {
#if log_CONDITION
            log_println("condition_timedWait: timedout");
#endif
            return true;
        }
        if (error == EINTR) {
#if log_CONDITION
            log_println("condition_timedWait: interrupted");
#endif
            return false;
        }
#elif os_SOLARIS
        timestruc_t reltime;
        reltime.tv_sec = timeoutMilliSeconds / 1000;
        reltime.tv_nsec = (timeoutMilliSeconds % 1000) * 1000000;
        error = cond_reltimedwait(condition, mutex, &reltime);
        if (error == ETIME) {
#if log_CONDITION
            log_println("condition_timedWait: timedout");
#endif
            return true;
        }
        if (error == EINTR) {
#if log_CONDITION
            log_println("condition_timedWait: interrupted");
#endif
            return false;
        }
#elif os_GUESTVMXEN
        struct guestvmXen_TimeSpec reltime;
        reltime.tv_sec = timeoutMilliSeconds / 1000;
        reltime.tv_nsec = (timeoutMilliSeconds % 1000) * 1000000;
        error = guestvmXen_condition_wait(*condition, *mutex, &reltime);
        if (error == 1) {
            return false;
        }
#else
#    error
#endif
	} else {
		return condition_wait(condition, mutex);
	}
	if (error != 0) {
	    log_println("condition_timedWait: unexpected error code %d [%s]", error, strerror(error));
	    return false;
    }
#if log_CONDITION
    log_println("condition_timedWait: finished");
#endif
	return true;
}

Boolean condition_notify(Condition condition) {
#if log_CONDITION
    log_println("condition_notify    (%p, %p)", thread_self(), condition);
#endif
#if (os_DARWIN || os_LINUX)
    return pthread_cond_signal(condition) == 0;
#elif os_SOLARIS
    return cond_signal(condition) == 0;
#elif os_GUESTVMXEN
    return guestvmXen_condition_notify(*condition, 0) == 0;
#else
#  error
#endif
}

Boolean condition_notifyAll(Condition condition) {
#if log_CONDITION
    log_println("condition_notifyAll (%p, %p)", thread_self(), condition);
#endif
#if (os_DARWIN || os_LINUX)
    return pthread_cond_broadcast(condition) == 0;
#elif os_SOLARIS
    return cond_broadcast(condition) == 0;
#elif os_GUESTVMXEN
    return guestvmXen_condition_notify(*condition, 1) == 0;
#else
#   error
#endif
}
