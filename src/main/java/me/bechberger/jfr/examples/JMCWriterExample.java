package me.bechberger.jfr.examples;

import jdk.jfr.consumer.RecordingFile;
import org.openjdk.jmc.flightrecorder.writer.TypesImpl;
import org.openjdk.jmc.flightrecorder.writer.api.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Example demonstrating how to write a JFR file using JMC Writer API.
 */
public class JMCWriterExample {

    public static void main(String[] args) throws IOException {
        Path outputFile = Path.of("example.jfr");

        final long startTicks = 1;

        try (FileOutputStream fos =
                     new FileOutputStream(outputFile.toFile())) {
            // Initialize a new recording
            Recording recording = Recordings.newRecording(fos, r -> {
                // Optional: configure recording settings
                // Ensure JDK type initialization
                r.withJdkTypeInitialization();
                // Set start ticks for timestamp consistency in nanoseconds
                // 1 seems to work best
                r.withStartTicks(startTicks);
            });

            // Register the event type
            Type myEventType = recording.registerType(
                "com.example.MyEvent",
                "jdk.jfr.Event",
                typeBuilder -> {
                    // Add the implicit startTime field
                    // the implicit fields must come first
                    typeBuilder.addField("startTime",
                            TypesImpl.Builtin.LONG,
                            field ->
                                    field.addAnnotation(
                                            Types.JDK.ANNOTATION_TIMESTAMP,
                                            "TICKS"));
                    // Add the custom field
                    typeBuilder.addField("myfield",
                            Types.Builtin.STRING);
                }
            );

            // Write an event instance
            recording.writeEvent(myEventType.asValue(eventBuilder -> {
                // the fields have to be set in order of declaration
                eventBuilder.putField("startTime", System.nanoTime() - startTicks);
                eventBuilder.putField("myfield", "Hello from JMC!");
            }));

            // Write another event
            recording.writeEvent(myEventType.asValue(eventBuilder -> {
                eventBuilder.putField("startTime", System.nanoTime() - startTicks);
                eventBuilder.putField("myfield", "Another event");
            }));

            // Close the recording to finalize the file
            recording.close();
        }

        // Printing file contents for demonstration
        RecordingFile.readAllEvents(outputFile)
                .forEach(System.out::println);

        System.out.println("JFR file written to: " + outputFile);
    }
}