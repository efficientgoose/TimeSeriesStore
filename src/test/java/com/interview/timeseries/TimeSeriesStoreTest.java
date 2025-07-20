package com.interview.timeseries;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for the TimeSeriesStore implementation.
 * Covers insertion, time range queries, tag filters, and cleanup.
 */
public class TimeSeriesStoreTest {

    private TimeSeriesStore store;

    @Before
    public void setUp() {
        // Create and initialize a fresh store instance before each test
        TimeSeriesStoreImpl impl = new TimeSeriesStoreImpl();
        impl.clearAllData(); // Clear previous data (both in-memory and disk)
        store = impl;
        store.initialize();
    }

    @After
    public void tearDown() {
        // Ensure proper cleanup after each test
        store.shutdown();
    }

    @Test
    public void testInsertAndQueryBasic() {
        // Test basic insert and retrieval of a single DataPoint
        long now = System.currentTimeMillis();
        Map<String, String> tags = new HashMap<>();
        tags.put("host", "server1");

        assertTrue(store.insert(new DataPoint(now, "cpu.usage", 45.2, tags)));

        List<DataPoint> results = store.query("cpu.usage", now, now + 1, tags);
        assertEquals(1, results.size());

        DataPoint dp = results.get(0);
        assertEquals("cpu.usage", dp.getMetric());
        assertEquals(45.2, dp.getValue(), 0.001);
        assertEquals("server1", dp.getTags().get("host"));
    }

    @Test
    public void testQueryTimeRange() {
        // Test filtering results by timestamp range
        long start = System.currentTimeMillis();
        Map<String, String> tags = Map.of("host", "server1");

        store.insert(new DataPoint(start, "cpu.usage", 45.2, tags));
        store.insert(new DataPoint(start + 1000, "cpu.usage", 48.3, tags));
        store.insert(new DataPoint(start + 2000, "cpu.usage", 51.7, tags));

        List<DataPoint> results = store.query("cpu.usage", start, start + 1500, tags);
        assertEquals(2, results.size());
    }

    @Test
    public void testQueryWithFilters() {
        // Test filtering by specific tags
        long now = System.currentTimeMillis();

        Map<String, String> tags1 = Map.of("host", "server1", "datacenter", "us-west");
        Map<String, String> tags2 = Map.of("host", "server2", "datacenter", "us-west");

        store.insert(new DataPoint(now, "cpu.usage", 45.2, tags1));
        store.insert(new DataPoint(now, "cpu.usage", 42.1, tags2));

        Map<String, String> filter = Map.of("datacenter", "us-west");

        List<DataPoint> results = store.query("cpu.usage", now, now + 1, filter);
        assertEquals(2, results.size());

        filter = Map.of("host", "server1");
        results = store.query("cpu.usage", now, now + 1, filter);
        assertEquals(1, results.size());
        assertEquals("server1", results.get(0).getTags().get("host"));
    }

    @Test
    public void testQueryWithNoResults() {
        // Ensure empty results are returned when no matches found
        long now = System.currentTimeMillis();
        store.insert(new DataPoint(now, "cpu.usage", 20.5, Map.of("host", "server1")));

        List<DataPoint> results = store.query("cpu.usage", now + 10000, now + 20000, Map.of("host", "server1"));
        assertTrue(results.isEmpty());
    }

    @Test
    public void testMultipleMetricsInsertedAndQueriedSeparately() {
        // Verify different metrics can be inserted and queried independently
        long now = System.currentTimeMillis();
        store.insert(new DataPoint(now, "cpu.usage", 10.5, Map.of("host", "server1")));
        store.insert(new DataPoint(now, "memory.used", 60.0, Map.of("host", "server1")));

        List<DataPoint> cpu = store.query("cpu.usage", now, now + 1, Map.of("host", "server1"));
        List<DataPoint> memory = store.query("memory.used", now, now + 1, Map.of("host", "server1"));

        assertEquals(1, cpu.size());
        assertEquals(1, memory.size());
        assertEquals("cpu.usage", cpu.get(0).getMetric());
        assertEquals("memory.used", memory.get(0).getMetric());
    }

    @Test
    public void testQueryMultipleTagFilters() {
        // Test multiple tag filters in the same query
        long now = System.currentTimeMillis();
        store.insert(new DataPoint(now, "network.in.bytes", 1030.5, Map.of("datacenter", "us-west", "service", "api")));
        store.insert(new DataPoint(now + 1000, "network.in.bytes", 987.0, Map.of("datacenter", "us-west", "service", "web")));
        store.insert(new DataPoint(now + 2000, "network.in.bytes", 1500.0, Map.of("datacenter", "us-west", "service", "api")));

        List<DataPoint> results = store.query("network.in.bytes", now, now + 5000, Map.of("datacenter", "us-west", "service", "api"));
        assertEquals(2, results.size());

        for (DataPoint dp : results) {
            assertEquals("us-west", dp.getTags().get("datacenter"));
            assertEquals("api", dp.getTags().get("service"));
        }
    }

    @Test
    public void testCleanupRemovesOldData() {
        // Insert old data and verify that cleanup removes it
        long now = System.currentTimeMillis();
        long oldTimestamp = now - (25L * 60 * 60 * 1000); // 25 hours ago

        store.insert(new DataPoint(oldTimestamp, "cpu.usage", 50.0, Map.of("host", "server1")));
        ((TimeSeriesStoreImpl) store).runCleanupNow(); // Manually trigger cleanup

        List<DataPoint> results = store.query("cpu.usage", oldTimestamp, now, Map.of("host", "server1"));
        assertTrue(results.isEmpty());
    }
}
