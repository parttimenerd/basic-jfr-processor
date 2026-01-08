package me.bechberger.jfr.examples;

import me.bechberger.jfr.JFREventModifier;
import me.bechberger.jfr.JFRProcessor;
import org.openjdk.jmc.flightrecorder.writer.RecordingImpl;
import jdk.jfr.consumer.RecordedEvent;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Simple example that demonstrates using `JFRProcessor` with a basic modifier that drops events of a specified type.
 *
 * Usage:
 *   java -cp <project-classpath> me.bechberger.jfr.examples.SimpleProcessorExample input.jfr output.jfr <event-name-to-drop>
 */
public class SimpleProcessorExample {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: SimpleProcessorExample <input.jfr> <output.jfr> <event-name-to-drop>");
            System.exit(2);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String eventNameToDrop = args[2];

        // Create a modifier that drops events
        JFREventModifier modifier = new JFREventModifier() {
            @Override
            public boolean shouldRemoveEvent(RecordedEvent event) {
                return event.getEventType().getName().equals(eventNameToDrop);
            }
        };

        JFRProcessor processor = new JFRProcessor(modifier, input);

        try (FileOutputStream out = new FileOutputStream(output.toFile())) {
            // process(...) returns a RecordingImpl that should be closed to finalize the file
            RecordingImpl result = processor.process(out);
            // Close the recording to flush any remaining data
            result.close();
            System.out.println("Processing complete. Output written to: " + output);
        } catch (IOException e) {
            System.err.println("Failed to process recording: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}