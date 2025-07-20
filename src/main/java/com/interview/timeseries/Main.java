package com.interview.timeseries;

import java.util.List;
import java.util.Map;

/**
 * Simple main class for manual testing of the TimeSeriesStore implementation.
 * Demonstrates inserting data and running queries with and without tag filters.
 */
public class Main {
    public static void main(String[] args) {
        // Create and initialize the store
        TimeSeriesStore store = new TimeSeriesStoreImpl();
        boolean initialized = store.initialize();
        System.out.println("Store initialized: " + initialized);

        if (!initialized) {
            System.err.println("Failed to initialize the store. Exiting.");
            return;
        }

        try {
            long now = System.currentTimeMillis();

            // Insert sample cpu.usage metrics
            store.insert(new DataPoint(now, "cpu.usage", 25.1, Map.of("host", "server1", "datacenter", "us-west")));
            store.insert(new DataPoint(now + 5000, "cpu.usage", 27.3, Map.of("host", "server2", "datacenter", "us-west")));
            store.insert(new DataPoint(now + 10000, "cpu.usage", 22.9, Map.of("host", "server3", "datacenter", "us-east")));

            // Insert sample memory.used metrics
            store.insert(new DataPoint(now, "memory.used", 60.0, Map.of("host", "server1", "datacenter", "us-west")));
            store.insert(new DataPoint(now + 20000, "memory.used", 62.5, Map.of("host", "server1", "datacenter", "us-east")));

            // Insert sample network.in.bytes metrics
            store.insert(new DataPoint(now + 30000, "network.in.bytes", 1030.5, Map.of("datacenter", "us-west", "service", "api")));
            store.insert(new DataPoint(now + 35000, "network.in.bytes", 987.0, Map.of("datacenter", "us-west", "service", "web")));
            store.insert(new DataPoint(now + 32000, "network.in.bytes", 1500.0, Map.of("datacenter", "us-west", "service", "api")));

            System.out.println("Test data inserted.\n");

            // ------------------------
            // Sample Query Executions
            // ------------------------

            // Query 1: Time range query for cpu.usage within 15 seconds
            System.out.println("QUERY 1: Time Range Query (cpu.usage over 15 seconds)");
            List<DataPoint> cpuUsage = store.query("cpu.usage", now, now + 15000, Map.of());
            cpuUsage.forEach(System.out::println);

            // Query 2: Single tag filter (memory.used where host = server1)
            System.out.println("\nQUERY 2: Single Tag Filter (memory.used on host=server1 over 30 seconds)");
            List<DataPoint> memoryUsed = store.query("memory.used", now, now + 30000, Map.of("host", "server1"));
            memoryUsed.forEach(System.out::println);

            // Query 3: Multi-tag filter (network.in.bytes for API in us-west)
            System.out.println("\nQUERY 3: Multiple Tag Filter (network.in.bytes for API in us-west, 60s window)");
            Map<String, String> filter = Map.of("datacenter", "us-west", "service", "api");
            List<DataPoint> networkIn = store.query("network.in.bytes", now, now + 60000, filter);
            networkIn.forEach(System.out::println);
        } finally {
            // Always shut down the store to persist data
            boolean shutdown = store.shutdown();
            System.out.println("Store shutdown: " + shutdown);
        }
    }
}
