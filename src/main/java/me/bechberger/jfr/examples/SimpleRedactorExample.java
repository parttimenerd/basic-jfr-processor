package me.bechberger.jfr.examples;

import jdk.jfr.consumer.RecordedEvent;
import me.bechberger.jfr.JFREventModifier;
import me.bechberger.jfr.JFRProcessor;
import org.openjdk.jmc.flightrecorder.writer.RecordingImpl;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Simple example that demonstrates using {@link JFRProcessor} to build a simple redactor
 *
 * Usage:
 *   java -cp <project-classpath> me.bechberger.jfr.examples.SimpleRedactorExample input.jfr output.jfr
 */
public class SimpleRedactorExample {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: SimpleRedactorExample <input.jfr> <output.jfr>");
            System.exit(2);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);

        // Create a modifier that drops events
        JFREventModifier modifier = new JFREventModifier() {
            @Override
            public boolean shouldRemoveEvent(RecordedEvent event) {
                return event.getEventType().getName().equals("jdk.InitialEnvironmentVariable");
            }

            @Override
            public String process(String fieldName, String value) {
                if (fieldName.equals("token")) {
                    return "<redacted>";
                }
                return value;
            }

            @Override
            public int process(String fieldName, int value) {
                if (fieldName.equals("port")) {
                    return 0;
                }
                return value;
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