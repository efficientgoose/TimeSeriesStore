package com.interview.timeseries;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Thread-safe, persistent implementation of the TimeSeriesStore interface.
 * Supports in-memory storage with periodic cleanup of expired entries and disk persistence via CSV.
 */
public class TimeSeriesStoreImpl implements TimeSeriesStore {

    // Stores timestamp -> List of DataPoints, sorted by timestamp
    private final ConcurrentNavigableMap<Long, List<DataPoint>> timeSeriesMap = new ConcurrentSkipListMap<>();

    // Path to the persistence CSV file
    private final String persistenceFile = "data_store.csv";

    // Background cleaner for expired entries
    private final ScheduledExecutorService cleanerExecuter = Executors.newSingleThreadScheduledExecutor();

    // Data older than this duration (24 hours) will be removed
    private final long EXPIRY_DURATION_MS = 24L * 60 * 60 * 1000;

    // Lock for synchronizing file operations
    private final Object diskLock = new Object();

    /**
     * Inserts a new DataPoint into the in-memory store.
     * Avoids duplicates and ensures thread-safe writes.
     */
    @Override
    public boolean insert(DataPoint dataPoint) {
        return timeSeriesMap.compute(dataPoint.getTimestamp(), (k, v) -> {
            if (v == null) v = new CopyOnWriteArrayList<>();
            if (!v.contains(dataPoint)) v.add(dataPoint);
            return v;
        }).contains(dataPoint);
    }

    /**
     * Returns all DataPoints matching the given metric, timestamp range, and tag filters.
     * End time is exclusive. Tag filters must all match.
     */
    @Override
    public List<DataPoint> query(String metric, long startTime, long endTime, Map<String, String> tagFilters) {
        return timeSeriesMap.subMap(startTime, true, endTime, false)
                .values()
                .stream()
                .flatMap(List::stream)
                .filter(dp -> dp.getMetric().equals(metric))
                .filter(dp -> tagFilters == null || tagFilters.entrySet().stream()
                        .allMatch(e -> e.getValue().equals(dp.getTag(e.getKey()))))
                .collect(Collectors.toList());
    }

    /**
     * Initializes the store by loading existing data from disk and starting the cleanup scheduler.
     */
    @Override
    public boolean initialize() {
        try {
            loadFromDisk();
            startCleanupTask();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Shuts down the store gracefully, running cleanup and persisting data.
     */
    @Override
    public boolean shutdown() {
        try {
            runCleanupNow();
            cleanerExecuter.shutdown();
            saveToDisk();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Schedules automatic cleanup of data older than 24 hours every hour.
     */
    private void startCleanupTask() {
        cleanerExecuter.scheduleAtFixedRate(() -> {
            long threshold = System.currentTimeMillis() - EXPIRY_DURATION_MS;
            synchronized (timeSeriesMap) {
                timeSeriesMap.headMap(threshold, false).clear();
            }
        }, 1, 1, TimeUnit.HOURS);
    }

    /**
     * Writes all in-memory data to the CSV file. Thread-safe via diskLock.
     */
    private void saveToDisk() {
        synchronized (diskLock) {
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(persistenceFile))) {
                for (List<DataPoint> dps : timeSeriesMap.values()) {
                    for (DataPoint dp : dps) {
                        writer.write(toCSV(dp));
                        writer.newLine();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Loads data from disk and inserts it into the in-memory store.
     */
    private void loadFromDisk() {
        Path path = Paths.get(persistenceFile);
        if (!Files.exists(path)) return;

        synchronized (diskLock) {
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    DataPoint dp = fromCSV(line);
                    insert(dp); // already thread-safe
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Serializes a DataPoint to CSV format.
     */
    private String toCSV(DataPoint dp) {
        String tagsStr = dp.getTags().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(";"));
        return dp.getTimestamp() + "," + dp.getMetric() + "," + dp.getValue() + "," + tagsStr;
    }

    /**
     * Deserializes a DataPoint from a CSV line.
     */
    private DataPoint fromCSV(String line) {
        String[] parts = line.split(",", 4);
        long timestamp = Long.parseLong(parts[0]);
        String metric = parts[1];
        double value = Double.parseDouble(parts[2]);

        Map<String, String> tags = new HashMap<>();
        if (parts.length == 4) {
            for (String tag : parts[3].split(";")) {
                String[] kv = tag.split("=", 2);
                if (kv.length == 2) tags.put(kv[0], kv[1]);
            }
        }

        return new DataPoint(timestamp, metric, value, tags);
    }

    /**
     * Runs immediate cleanup of expired entries and persists the result.
     */
    public void runCleanupNow() {
        long threshold = System.currentTimeMillis() - EXPIRY_DURATION_MS;
        synchronized (timeSeriesMap) {
            int before = timeSeriesMap.size();
            timeSeriesMap.headMap(threshold, false).clear();
            int after = timeSeriesMap.size();
            System.out.println("Cleanup executed. Removed " + (before - after) + " expired timestamps.");
        }
        saveToDisk(); // diskLock is handled inside
    }

    /**
     * Clears all data from memory and deletes the CSV file.
     * Useful for resetting the store between tests.
     */
    public void clearAllData() {
        synchronized (diskLock) {
            timeSeriesMap.clear();
            File file = new File(persistenceFile);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (!deleted) {
                    System.err.println("Warning: Could not delete " + persistenceFile);
                }
            }
            saveToDisk(); // will create a new empty file
        }
    }
}
