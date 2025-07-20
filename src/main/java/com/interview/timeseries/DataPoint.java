package com.interview.timeseries;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single metric data point with a timestamp, value, and optional tags.
 */
public class DataPoint {
    private final long timestamp;
    private final String metric;
    private final double value;
    private final Map<String, String> tags;

    /**
     * Constructs a new DataPoint instance.
     *
     * @param timestamp The time the metric was recorded in milliseconds since the epoch.
     * @param metric The name of the metric (e.g., "cpu.usage").
     * @param value The value of the metric.
     * @param tags Key-value metadata associated with this data point (can be null).
     */
    public DataPoint(long timestamp, String metric, double value, Map<String, String> tags) {
        this.timestamp = timestamp;
        this.metric = metric;
        this.value = value;
        this.tags = tags != null ? new HashMap<>(tags) : new HashMap<>();
    }

    /**
     * @return The timestamp of the data point.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return The metric name.
     */
    public String getMetric() {
        return metric;
    }

    /**
     * @return The numeric value of the metric.
     */
    public double getValue() {
        return value;
    }

    /**
     * @return A copy of the tags associated with this data point.
     */
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * Returns the value of a tag by key.
     *
     * @param key The tag key to retrieve.
     * @return The tag value, or empty string if the key is not found.
     */
    public String getTag(String key) {
        return tags.getOrDefault(key, "");
    }

    @Override
    public String toString() {
        return "DataPoint{" +
                "timestamp=" + timestamp +
                ", metric='" + metric + '\'' +
                ", value=" + value +
                ", tags=" + tags +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DataPoint)) return false;
        DataPoint that = (DataPoint) o;
        return timestamp == that.timestamp &&
                Double.compare(that.value, value) == 0 &&
                Objects.equals(metric, that.metric) &&
                Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, metric, value, tags);
    }
}
