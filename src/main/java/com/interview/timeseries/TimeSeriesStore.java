package com.interview.timeseries;

import java.util.List;
import java.util.Map;

/**
 * Interface for the Time Series Store.
 * Provides operations for inserting and querying time series data.
 * Implementations must be thread-safe and support persistence.
 */
public interface TimeSeriesStore {

    /**
     * Inserts a single data point into the store.
     *
     * @param datapoint The data point to insert.
     * @return true if the insertion was successful, false otherwise.
     */
    boolean insert(DataPoint datapoint);

    /**
     * Queries data points matching the given metric and tag filters within the specified time range.
     *
     * @param metric The metric name to query.
     * @param startTime The start of the time range (inclusive), in milliseconds.
     * @param endTime The end of the time range (exclusive), in milliseconds.
     * @param tagFilters Optional filters on tags (can be null or empty).
     * @return A list of matching data points.
     */
    List<DataPoint> query(String metric, long startTime, long endTime, Map<String, String> tagFilters);

    /**
     * Initializes the store, including loading data from disk if available.
     * Should be called once before any read/write operations.
     *
     * @return true if the initialization was successful, false otherwise.
     */
    boolean initialize();

    /**
     * Shuts down the store gracefully, ensuring that data is saved to disk.
     * Should be called before application exit.
     *
     * @return true if shutdown was successful, false otherwise.
     */
    boolean shutdown();
}
