package com.interview.timeseries;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Performance and stress tests for TimeSeriesStore.
 * These are manual tests intended to evaluate speed, memory usage,
 * and concurrent behavior of the store under load.
 * Uncomment the @Ignore annotations to run specific tests.
 */
public class TimeSeriesStorePerformanceTest {
    private TimeSeriesStore store;

    /**
     * Initializes the store before each test and clears any existing data.
     */
    @Before
    public void setUp() {
        TimeSeriesStoreImpl impl = new TimeSeriesStoreImpl();
        impl.clearAllData(); // Clear persisted and in-memory data
        store = impl;
        store.initialize();
    }

    /**
     * Shuts down the store after each test.
     */
    @After
    public void tearDown() {
        store.shutdown();
    }

    /**
     * Inserts 500,000 data points sequentially to measure insertion performance and memory usage.
     */
    @Test
    @Ignore("Manual test: Heavy insertion")
    public void testLargeInsertPerformance() {
        long now = System.currentTimeMillis();
        int count = 500_000;
        logMemory("Before insert");

        for (int i = 0; i < count; i++) {
            long ts = now - (i % (24 * 60 * 60 * 1000));
            store.insert(new DataPoint(ts, "metric.load", i, Map.of("host", "load" + (i % 100))));
        }

        logMemory("After insert");
        System.out.println("Inserted 500k records");
        System.out.println("Time taken to run the test: " + (System.currentTimeMillis() - now + "ms"));
    }

    /**
     * Tests performance of querying data points after insertion.
     */
    @Test
    @Ignore("Manual test: Query after bulk insert")
    public void testLargeQueryPerformance() {
        long now = System.currentTimeMillis();
        store.insert(new DataPoint(now - 10000, "metric.load", 100, Map.of("host", "load1")));

        long start = System.currentTimeMillis();
        List<DataPoint> results = store.query("metric.load", now - 3600000, now, Map.of("host", "load1"));
        long duration = System.currentTimeMillis() - start;
        System.out.println("Query returned " + results.size() + " results in " + duration + " ms");
    }

    /**
     * Tests concurrent insertions using multiple threads to evaluate thread-safety and parallel performance.
     */
    @Test
    @Ignore("Manual test: Simulate concurrent load")
    public void testConcurrentInsertAndQuery() {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        long now = System.currentTimeMillis();

        for (int i = 0; i < 500_000; i++) {
            int finalI = i;
            executor.submit(() -> {
                long ts = now - (finalI % (24 * 60 * 60 * 1000));
                store.insert(new DataPoint(ts, "metric.concurrent", finalI, Map.of("thread", "T" + (finalI % 4))));
            });
        }

        System.out.println("Time taken to insert 500k records concurrently: " + (System.currentTimeMillis() - now + "ms"));
    }

    /**
     * Simulates mixed concurrent load with writers and readers working in parallel.
     * Writers insert data, and readers perform queries simultaneously.
     */
    @Test
    @Ignore("Manual test: Concurrent reads and writes")
    public void testConcurrentInsertAndQueryMixedLoad() throws InterruptedException {
        int writerThreads = 4;
        int readerThreads = 4;
        int totalThreads = writerThreads + readerThreads;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);

        long now = System.currentTimeMillis();
        int count = 500_000;

        CountDownLatch latch = new CountDownLatch(totalThreads);

        // Writer threads
        for (int i = 0; i < writerThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < count / writerThreads; j++) {
                    long ts = now - (j % (24 * 60 * 60 * 1000));
                    store.insert(new DataPoint(ts, "metric.mixed", j, Map.of("thread", "writer")));
                }
                latch.countDown();
            });
        }

        // Reader threads
        for (int i = 0; i < readerThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < 1000; j++) {
                    long start = now - (2 * 60 * 60 * 1000); // 2 hours ago
                    long end = now;
                    store.query("metric.mixed", start, end, Map.of("thread", "writer"));
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.MINUTES);
        executor.shutdown();
        System.out.println("Concurrent insert + query test completed.");
    }

    /**
     * Tests the time taken to restore data from disk after shutdown.
     * Useful to validate persistence and recovery performance.
     */
    @Test
    @Ignore("Manual test: Persistence restore performance")
    public void testDiskPersistenceRestore() {
        long now = System.currentTimeMillis();
        int count = 200_000;

        for (int i = 0; i < count; i++) {
            long ts = now - (i % (24 * 60 * 60 * 1000));
            store.insert(new DataPoint(ts, "metric.persist", i, Map.of("restore", "test")));
        }

        store.shutdown();

        TimeSeriesStore newStore = new TimeSeriesStoreImpl();
        long start = System.currentTimeMillis();
        newStore.initialize();
        long duration = System.currentTimeMillis() - start;

        System.out.println("Restore time from disk: " + duration + " ms");

        List<DataPoint> result = newStore.query("metric.persist", now - 10000, now, Map.of("restore", "test"));
        System.out.println("Restored " + result.size() + " recent points from disk");
        newStore.shutdown();
    }

    /**
     * Logs memory usage at the current state with a custom label.
     * Helps track JVM heap consumption during performance tests.
     */
    private void logMemory(String label) {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        System.out.println("[" + label + "] Memory used: " + used + " MB");
    }
}