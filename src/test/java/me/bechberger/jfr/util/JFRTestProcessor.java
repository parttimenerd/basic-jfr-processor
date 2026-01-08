package me.bechberger.jfr.util;

import me.bechberger.jfr.JFREventModifier;
import me.bechberger.jfr.JFRProcessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper for processing JFR recordings with a fluent API.
 * Supports roundtrip testing: record -> process -> read back.
 */
public class JFRTestProcessor {
    private final Path tempDir;
    private Path inputPath;
    private JFREventModifier modifier = new JFREventModifier() {}; // No-op modifier by default
    private String outputName = "output";

    public JFRTestProcessor(Path tempDir) {
        this.tempDir = tempDir;
    }

    public JFRTestProcessor from(Path inputPath) {
        this.inputPath = inputPath;
        return this;
    }

    /**
     * Use a custom modifier for processing.
     */
    public JFRTestProcessor withModifier(JFREventModifier modifier) {
        this.modifier = modifier;
        return this;
    }

    /**
     * Use no modification (passthrough).
     */
    public JFRTestProcessor withNoModification() {
        this.modifier = new JFREventModifier() {};
        return this;
    }

    public JFRTestProcessor outputTo(String name) {
        this.outputName = name;
        return this;
    }

    public Path process() throws IOException {
        Path outputPath = tempDir.resolve(outputName + ".jfr");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            JFRProcessor processor = new JFRProcessor(modifier, inputPath);
            var recording = processor.process(output);
            recording.close(); // Must close to flush data to output stream

            Files.write(outputPath, output.toByteArray());
        }
        return outputPath;
    }

    public JFREventVerifier processAndVerify() throws IOException {
        return new JFREventVerifier(process());
    }
}