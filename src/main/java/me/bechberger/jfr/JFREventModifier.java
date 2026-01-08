package me.bechberger.jfr;

import jdk.jfr.consumer.RecordedEvent;

/**
 * Interface for processing JFR events and their fields.
 * <p>
 * This interface separates the procession logic from the JFR processing logic.
 */
public interface JFREventModifier {

    /**
     * Check if an event should be completely removed from the output.
     *
     * @param event The event to check
     * @return true if the event should be removed, false otherwise
     */
    default boolean shouldRemoveEvent(RecordedEvent event) {
        return false;
    }

    /**
     * Redact a string field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original value
     * @return The processed value
     */
    default String process(String fieldName, String value) {
        return value;
    }

    /**
     * Redact an integer field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original value
     * @return The processed value
     */
    default int process(String fieldName, int value) {
        return value;
    }

    /**
     * Process a long field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original value
     * @return The processed value
     */
    default long process(String fieldName, long value) {
        return value;
    }

    /**
     * Process a boolean field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original value
     * @return The processed value
     */
    default boolean process(String fieldName, boolean value) {
        return value;
    }

    /**
     * Process a byte field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original value
     * @return The processed value
     */
    default byte process(String fieldName, byte value) {
        return value;
    }

    /**
     * Process a char field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original value
     * @return The processed value
     */
    default char process(String fieldName, char value) {
        return value;
    }

    /**
     * Process a short field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original value
     * @return The processed value
     */
    default short process(String fieldName, short value) {
        return value;
    }

    /**
     * Process a float field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original value
     * @return The processed value
     */
    default float process(String fieldName, float value) {
        return value;
    }

    /**
     * Process a double field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original value
     * @return The processed value
     */
    default double process(String fieldName, double value) {
        return value;
    }

    /**
     * Process a string array field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original array
     * @return The processed array
     */
    default String[] process(String fieldName, String[] value) {
        return value;
    }

    /**
     * Process an int array field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original array
     * @return The processed array
     */
    default int[] process(String fieldName, int[] value) {
        return value;
    }

    /**
     * Process a long array field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original array
     * @return The processed array
     */
    default long[] process(String fieldName, long[] value) {
        return value;
    }

    /**
     * Process a byte array field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original array
     * @return The processed array
     */
    default byte[] process(String fieldName, byte[] value) {
        return value;
    }

    /**
     * Process a short array field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original array
     * @return The processed array
     */
    default short[] process(String fieldName, short[] value) {
        return value;
    }

    /**
     * Process a float array field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original array
     * @return The processed array
     */
    default float[] process(String fieldName, float[] value) {
        return value;
    }

    /**
     * Process a double array field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original array
     * @return The processed array
     */
    default double[] process(String fieldName, double[] value) {
        return value;
    }

    /**
     * Process a boolean array field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original array
     * @return The processed array
     */
    default boolean[] process(String fieldName, boolean[] value) {
        return value;
    }

    /**
     * Process a char array field value.
     *
     * @param fieldName The name of the field being processed
     * @param value The original array
     * @return The processed array
     */
    default char[] process(String fieldName, char[] value) {
        return value;
    }
}