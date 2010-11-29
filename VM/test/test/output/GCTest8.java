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
package test.output;

/**
 * A heavily multi-threaded GC test stress testing GC Safepoint when many threads starts and go.
 */
public class GCTest8 {
    private static Object lock = new Object();
    static int numCompleted = 0;
    static int batchSize = 0;

    static int MAX_CONCURRENT_THREADS;
    static int MAX_TOTAL_THREADS;
    static {
        MAX_CONCURRENT_THREADS = Integer.parseInt(System.getProperty("gctest.thread.concurrent", "20"));
        MAX_TOTAL_THREADS = Integer.parseInt(System.getProperty("gctest.thread.total", "100"));
    }
    public static void main(String[] args) {
        int numStarted = 0;
        // Starts MAX_CONCURRENT_THREADS  threads.
        // Waits to drop down to 15 to starts new threads again.
        for (numStarted = 0; numStarted < MAX_CONCURRENT_THREADS;) {
            new HeapFiller(numStarted++).start();
        }

        batchSize = MAX_CONCURRENT_THREADS  / 4;
        if (batchSize == 0) {
            batchSize = 1;
        }
        final  int minThreadThreshold = MAX_CONCURRENT_THREADS - batchSize;

        while (numStarted < MAX_TOTAL_THREADS) {
            try {
                synchronized (lock) {
                    while ((numStarted - numCompleted) > minThreadThreshold) {
                        lock.wait();
                    }
                }
            } catch (InterruptedException e) {
            }
            // Get another batch started.
            System.out.println("starting fillers #" + numStarted + " to #" + (numStarted + batchSize));
            for (int i = 0; i < batchSize; i++) {
                new HeapFiller(numStarted++).start();
            }
        }
    }

    private static void notifyFillerDone() {
        synchronized (lock) {
            numCompleted++;
            if (numCompleted % batchSize == 0) {
                lock.notifyAll();
            }
        }
    }

    private static class HeapFiller extends Thread {

        HeapFiller(int id) {
            super("HeapFiller #" + id);
        }

        @Override
        public void run() {
            for (int i = 0; i < 10; i++) {
                createGarbage();
            }
            notifyFillerDone();
        }
    }

    /**
     * Create various kinds of garbage.
     */
    private static void createGarbage() {
        final int max = 2000;
        final Object[] objects = new Object[max];
        for (int i = 0; i < max; i++) {
            final int[] ints = new int[i];
            objects[i / 5] = ints;
        }

        for (int i = 0; i < max / 4; i++) {
            objects[i * 4] = new Object();
            objects[i * 4 + 1] = objects;
        }

        for (int i = 0; i < max / 3; i++) {
            final GCTest8 garbageTest = new GCTest8();
            objects[i * 3] = garbageTest;
        }
    }
}
