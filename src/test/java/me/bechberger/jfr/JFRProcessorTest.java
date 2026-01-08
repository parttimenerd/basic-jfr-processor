package me.bechberger.jfr;

import jdk.jfr.*;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import me.bechberger.jfr.util.JFRTestHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static me.bechberger.jfr.util.JFRTestEvents.*;

public class JFRProcessorTest {

    @TempDir
    Path tempDir;

    private JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new JFRTestHelper(tempDir);
    }

    // ========== Basic Roundtrip Tests ==========

    @Test
    public void roundtripPreservesSimpleEvent() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = "Hello World";
            event.count = 42;
            event.flag = true;
            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void roundtripPreservesComplexEvent() throws IOException {
        helper.roundtrip(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "test string";
            event.intField = 42;
            event.longField = 9223372036854775807L;
            event.floatField = 3.14f;
            event.doubleField = 2.71828;
            event.booleanField = true;
            event.byteField = (byte) 127;
            event.shortField = (short) 32767;
            event.charField = 'X';
            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.ComplexEvent");
    }

    @Test
    public void roundtripPreservesMultipleEvents() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 10; i++) {
                SimpleEvent event = new SimpleEvent();
                event.message = "Message " + i;
                event.count = i;
                event.flag = i % 2 == 0;
                event.commit();
            }
        })
        .withNoModification()
        .eventCountPreserved()
        .eventOrderPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void roundtripPreservesMixedEventTypes() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent simple = new SimpleEvent();
            simple.message = "Simple";
            simple.count = 1;
            simple.flag = true;
            simple.commit();

            ComplexEvent complex = new ComplexEvent();
            complex.stringField = "Complex";
            complex.intField = 42;
            complex.longField = 456L;
            complex.floatField = 1.23f;
            complex.doubleField = 4.56;
            complex.booleanField = false;
            complex.byteField = (byte) 7;
            complex.shortField = (short) 89;
            complex.charField = 'A';
            complex.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventOrderPreserved()
        .allEventsFullyPreserved();
    }

    @Test
    public void roundtripPreservesNullValues() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = null;
            event.count = 0;
            event.flag = false;
            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    // ========== Basic Functionality Tests ==========

    @Test
    public void processSimpleEventPreservesAllFields() throws IOException {
        Path inputPath = helper.recording()
                .addSimpleEvent("Hello World", 42, true)
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .process())
                .fileExists()
                .fileNotEmpty()
                .hasEvents()
                .findEvent("test.SimpleEvent")
                .hasString("message", "Hello World")
                .hasInt("count", 42)
                .hasBoolean("flag", true);
    }

    @Test
    public void modifierRemovesMatchingEvents() throws IOException {
        @Name("jdk.SystemProcess")
        class SystemProcessEvent extends Event {
            String processName = "test-process";
        }

        Path inputPath = helper.recording()
                .addEvent(() -> {
                    SimpleEvent simple = new SimpleEvent();
                    simple.message = "Keep this";
                    simple.commit();

                    SystemProcessEvent systemProcess = new SystemProcessEvent();
                    systemProcess.commit();
                })
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .withModifier(new JFREventModifier() {
                    @Override
                    public boolean shouldRemoveEvent(RecordedEvent event) {
                        return "jdk.SystemProcess".equals(event.getEventType().getName());
                    }
                })
                .process())
                .hasNoEventOfType("jdk.SystemProcess")
                .hasEventOfType("test.SimpleEvent", 1);
    }

    @Test
    public void modifierRedactsStringFields() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = "secret";
            event.count = 10;
            event.flag = true;
            event.commit();
        })
        .withModifier(new JFREventModifier() {
            @Override
            public String process(String fieldName, String value) {
                if ("message".equals(fieldName) && value != null) {
                    return "***";
                }
                return value;
            }
        })
        .eventCountPreserved()
        .fieldChanged("test.SimpleEvent", "message");
    }

    @Test
    public void modifierTransformsIntegerFields() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event = new SimpleEvent();
            event.message = "test";
            event.count = 42;
            event.flag = true;
            event.commit();
        })
        .withModifier(new JFREventModifier() {
            @Override
            public int process(String fieldName, int value) {
                if ("count".equals(fieldName)) {
                    return value * 2;
                }
                return value;
            }
        })
        .eventCountPreserved()
        .fieldChanged("test.SimpleEvent", "count");
    }

    @Test
    public void modifierSelectivelyRemovesEvents() throws IOException {
        var verifier = helper.roundtrip(() -> {
            SimpleEvent event1 = new SimpleEvent();
            event1.message = "keep";
            event1.count = 1;
            event1.flag = true;
            event1.commit();

            SimpleEvent event2 = new SimpleEvent();
            event2.message = "remove";
            event2.count = 2;
            event2.flag = false;
            event2.commit();

            SimpleEvent event3 = new SimpleEvent();
            event3.message = "keep";
            event3.count = 3;
            event3.flag = true;
            event3.commit();
        })
        .withModifier(new JFREventModifier() {
            @Override
            public boolean shouldRemoveEvent(RecordedEvent event) {
                if ("test.SimpleEvent".equals(event.getEventType().getName())) {
                    return "remove".equals(event.getString("message"));
                }
                return false;
            }
        });

        // Verify that 2 out of 3 events remain (1 was removed)
        Assertions.assertEquals(2, verifier.getProcessedEventCount("test.SimpleEvent"),
                "Should have 2 SimpleEvents after removing 1");
    }

    @Test
    public void modifierTransformsMultipleComplexFields() throws IOException {
        helper.roundtrip(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "original";
            event.intField = 100;
            event.longField = 200L;
            event.doubleField = 3.14;
            event.booleanField = true;
            event.commit();
        })
        .withModifier(new JFREventModifier() {
            @Override
            public String process(String fieldName, String value) {
                if ("stringField".equals(fieldName)) {
                    return "modified";
                }
                return value;
            }

            @Override
            public long process(String fieldName, long value) {
                if ("longField".equals(fieldName)) {
                    return value + 100;
                }
                return value;
            }
        })
        .eventCountPreserved()
        .fieldChanged("test.ComplexEvent", "stringField")
        .fieldChanged("test.ComplexEvent", "longField");
    }

    // ========== Edge Cases ==========

    @Test
    public void roundtripPreservesBoundaryValues() throws IOException {
        helper.roundtrip(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "";  // Empty string
            event.intField = Integer.MIN_VALUE;
            event.longField = Long.MAX_VALUE;
            event.floatField = Float.MIN_VALUE;
            event.doubleField = Double.MAX_VALUE;
            event.booleanField = false;
            event.byteField = Byte.MAX_VALUE;
            event.shortField = Short.MIN_VALUE;
            event.charField = '\0';
            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.ComplexEvent");
    }

    @Test
    public void roundtripPreservesSpecialFloatingPointValues() throws IOException {
        helper.roundtrip(() -> {
            ComplexEvent event1 = new ComplexEvent();
            event1.stringField = "NaN test";
            event1.floatField = Float.NaN;
            event1.doubleField = Double.NaN;
            event1.commit();

            ComplexEvent event2 = new ComplexEvent();
            event2.stringField = "Infinity test";
            event2.floatField = Float.POSITIVE_INFINITY;
            event2.doubleField = Double.NEGATIVE_INFINITY;
            event2.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.ComplexEvent");
    }

    @Test
    public void roundtripPreservesUnicodeAndSpecialCharacters() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent event1 = new SimpleEvent();
            event1.message = "Unicode: 你好世界 🎉 αβγδ";
            event1.count = 1;
            event1.commit();

            SimpleEvent event2 = new SimpleEvent();
            event2.message = "Special: \n\t\r\"'\\";
            event2.count = 2;
            event2.commit();

            SimpleEvent event3 = new SimpleEvent();
            event3.message = "Emoji: 😀🎨🚀🌟";
            event3.count = 3;
            event3.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void roundtripPreservesLargeEventCount() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 100; i++) {
                SimpleEvent event = new SimpleEvent();
                event.message = "Event " + i;
                event.count = i;
                event.flag = i % 3 == 0;
                event.commit();
            }
        })
        .withNoModification()
        .eventCountPreserved()
        .eventOrderPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void roundtripPreservesStackTrace() throws IOException {
        @Name("test.EventWithStackTrace")
        @StackTrace
        class EventWithStackTrace extends Event {
            @Label("Message")
            String message;

            @Label("Value")
            int value;
        }

        helper.roundtrip(() -> {
            EventWithStackTrace event = new EventWithStackTrace();
            event.message = "Test stack trace preservation";
            event.value = 42;
            event.commit();
        }, EventWithStackTrace.class)
        .withNoModification()
        .verifyEventsFullyEqual();
    }


    // ========== Edge Cases ==========

    @Test
    public void emptyRecordingProducesEmptyOutput() throws IOException {
        Path emptyPath = helper.createTestRecording(() -> {
            // No events
        });

        helper.verify(helper.process()
                .from(emptyPath)
                .process())
                .fileExists()
                .hasTestEventCount(0);
    }

    @Test
    public void testRoundtripWithMultipleEventTypes() throws IOException {
        helper.roundtrip(() -> {
            SimpleEvent simple = new SimpleEvent();
            simple.message = "Simple";
            simple.count = 1;
            simple.flag = true;
            simple.commit();

            ComplexEvent complex = new ComplexEvent();
            complex.stringField = "Complex";
            complex.intField = 42;
            complex.commit();

            NetworkEvent network = new NetworkEvent();
            network.sourceAddress = "10.0.0.1";
            network.protocol = "TCP";
            network.commit();

            // Note: ArrayEvent is not included because JFR does not persist array fields
            // in custom events. While arrays can be set on Event objects in code,
            // the JDK's JFR implementation does not serialize them to the recording file.
        })
        .withNoModification()
        .eventCountPreserved()
        .eventOrderPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent")     // Deep equality
        .eventsOfTypeFullyPreserved("test.ComplexEvent")    // Deep equality
        .eventsOfTypeFullyPreserved("test.NetworkEvent");   // Deep equality
    }

    // ========== Mixed and Interleaved Event Tests ==========

    @Test
    public void roundtripPreservesMixedNullAndNonNullValues() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 5; i++) {
                SimpleEvent event = new SimpleEvent();
                event.message = (i % 2 == 0) ? null : "Message " + i;
                event.count = i;
                event.flag = i % 2 == 0;
                event.commit();
            }
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent");
    }

    @Test
    public void roundtripPreservesInterleavedEventTypes() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 10; i++) {
                SimpleEvent simple = new SimpleEvent();
                simple.message = "Simple " + i;
                simple.count = i;
                simple.commit();

                NetworkEvent network = new NetworkEvent();
                network.sourceAddress = "192.168.1." + i;
                network.destinationAddress = "10.0.0." + i;
                network.sourcePort = 1000 + i;
                network.destinationPort = 2000 + i;
                network.protocol = (i % 2 == 0) ? "TCP" : "UDP";
                network.commit();

                ComplexEvent complex = new ComplexEvent();
                complex.stringField = "Complex " + i;
                complex.intField = i * 100;
                complex.longField = i * 1000L;
                complex.commit();
            }
        })
        .withNoModification()
        .eventCountPreserved()
        .eventOrderPreserved()
        .eventsOfTypeFullyPreserved("test.SimpleEvent")
        .eventsOfTypeFullyPreserved("test.NetworkEvent")
        .eventsOfTypeFullyPreserved("test.ComplexEvent");
    }


    @Test
    public void eventToStringWorksAfterProcessing() throws IOException {
        Path inputPath = helper.recording()
                .addSimpleEvent("Test ToString", 7, false)
                .build();
        RecordedEvent event = RecordingFile.readAllEvents(inputPath).getFirst();
        testEventStackTraceFrames(event);
        try (RecordingFile recordingFile = new RecordingFile(inputPath)) {
            Path path = tempDir.resolve("output.jfr");
            recordingFile.write(path, e -> true);
            event = RecordingFile.readAllEvents(path).getFirst();
            testEventStackTraceFrames(event);
        }
    }

    private static void testEventStackTraceFrames(RecordedEvent event) {
        Assertions.assertDoesNotThrow(event::getStackTrace);
        RecordedStackTrace stackTrace = event.getStackTrace();
        Assertions.assertDoesNotThrow(stackTrace::getFrames);
    }

    @Test
    public void eventFieldsAccessibleAfterProcessing() throws IOException {
        Path inputPath = helper.recording()
                .addSimpleEvent("Test Field Access", 7, false)
                .build();

        helper.verify(helper.process()
                .from(inputPath)
                .process())
                .findEvent("test.SimpleEvent")
                .run(e -> {
                    testEventStackTraceFrames(e);
                    Assertions.assertDoesNotThrow(e.getStackTrace().getFrames().getFirst()::toString);
                    Assertions.assertDoesNotThrow(e.getStackTrace()::toString);
                    Assertions.assertDoesNotThrow(e::toString);
                });
    }

    // ========== Temporal Annotations Tests ==========

    @Test
    public void roundtripPreservesTimestampAnnotations() throws IOException {
        helper.roundtrip(() -> {
            TimestampEvent event = new TimestampEvent();
            long currentTime = System.currentTimeMillis();
            event.timestampStart = currentTime * 1_000_000; // Convert to nanos (TICKS)
            event.timestampEnd = (currentTime + 1000) * 1_000_000; // 1 second later
            event.durationNanos = 1_000_000_000L; // 1 second in nanos
            event.durationMicros = 1_000_000L; // 1 second in micros
            event.durationMillis = 1_000L; // 1 second in millis
            event.durationSeconds = 1L; // 1 second
            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.TimestampEvent");
    }

    @Test
    public void roundtripPreservesDataAmountAnnotations() throws IOException {
        helper.roundtrip(() -> {
            DataAmountEvent event = new DataAmountEvent();
            event.bytes = 1024L * 1024L * 1024L; // 1 GB
            event.bits = 8L * 1024L * 1024L; // 1 MB in bits
            event.hertz = 2_400_000_000L; // 2.4 GHz
            event.address = 0x7FFFFFFF00000000L; // Memory address
            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.DataAmountEvent");
    }

    @Test
    public void roundtripPreservesThreadAnnotations() throws IOException {
        helper.roundtrip(() -> {
            ThreadEvent event = new ThreadEvent();
            event.thread = Thread.currentThread();
            event.clazz = ThreadEvent.class;
            event.threadName = Thread.currentThread().getName();
            event.threadPriority = Thread.currentThread().getPriority();
            event.isDaemon = Thread.currentThread().isDaemon();
            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.ThreadEvent");
    }

    @Test
    public void roundtripPreservesContentTypeAnnotations() throws IOException {
        helper.roundtrip(() -> {
            ContentTypeEvent event = new ContentTypeEvent();
            event.bytes = 512L * 1024L; // 512 KB
            event.percentage = 0.75f; // 75%
            event.memoryAddress = 0xDEADBEEF00000000L;
            event.unsigned = Long.MAX_VALUE; // Large unsigned value
            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.ContentTypeEvent");
    }

    @Test
    public void roundtripPreservesComprehensiveDataAmountAnnotations() throws IOException {
        helper.roundtrip(() -> {
            ComprehensiveDataAmountEvent event = new ComprehensiveDataAmountEvent();
            event.bytesValue = 1024L; // 1 KB
            event.bitsValue = 8192L; // 1 KB in bits
            event.kilobytes = 2048L * 1024L; // 2 MB in bytes
            event.megabytes = 100L * 1024L * 1024L; // 100 MB in bytes
            event.gigabytes = 5L * 1024L * 1024L * 1024L; // 5 GB in bytes
            event.transferRate = 1_000_000_000L; // 1 GB/s
            event.bandwidthUsage = 0.85f; // 85%
            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.ComprehensiveDataAmountEvent");
    }

    @Test
    public void roundtripPreservesAllContentTypeAnnotations() throws IOException {
        helper.roundtrip(() -> {
            AllContentTypesEvent event = new AllContentTypesEvent();
            // Data amounts
            event.bytes = 4096L; // 4 KB
            event.bits = 32768L; // 4 KB in bits

            // Time-related
            long currentTime = System.currentTimeMillis();
            event.timestamp = currentTime * 1_000_000L; // Convert to nanos
            event.timespanNanos = 500_000_000L; // 500 ms
            event.timespanMicros = 500_000L; // 500 ms
            event.timespanMillis = 500L; // 500 ms
            event.timespanSeconds = 1L; // 1 second

            // Numeric content types
            event.frequency = 3_200_000_000L; // 3.2 GHz
            event.memoryAddress = 0xCAFEBABE00000000L;
            event.percentage = 0.95f; // 95%
            event.unsigned = Long.MAX_VALUE;

            // Reference types
            event.thread = Thread.currentThread();
            event.clazz = AllContentTypesEvent.class;

            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventsOfTypeFullyPreserved("test.AllContentTypesEvent");
    }

    // ========== Multi-File Processing Tests ==========

    @Test
    public void concatenatesMultipleRecordingFiles() throws IOException {
        // Create first recording with simple events
        Path recording1 = helper.recording()
                .withName("recording1")
                .addEvent(() -> {
                    for (int i = 0; i < 5; i++) {
                        SimpleEvent event = new SimpleEvent();
                        event.message = "File 1 - Event " + i;
                        event.count = i;
                        event.flag = true;
                        event.commit();
                    }
                })
                .build();

        // Create second recording with different events
        Path recording2 = helper.recording()
                .withName("recording2")
                .addEvent(() -> {
                    for (int i = 0; i < 3; i++) {
                        ComplexEvent event = new ComplexEvent();
                        event.stringField = "File 2 - Event " + i;
                        event.intField = i * 100;
                        event.longField = i * 1000L;
                        event.commit();
                    }
                })
                .build();

        // Create third recording with network events
        Path recording3 = helper.recording()
                .withName("recording3")
                .addEvent(() -> {
                    for (int i = 0; i < 2; i++) {
                        NetworkEvent event = new NetworkEvent();
                        event.sourceAddress = "10.0.0." + i;
                        event.destinationAddress = "192.168.1." + i;
                        event.sourcePort = 1000 + i;
                        event.destinationPort = 2000 + i;
                        event.protocol = "TCP";
                        event.commit();
                    }
                })
                .build();

        // Process multiple files
        Path outputPath = tempDir.resolve("concatenated.jfr");
        List<RecordingFile> inputs = List.of(
                new RecordingFile(recording1),
                new RecordingFile(recording2),
                new RecordingFile(recording3)
        );

        try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            JFRProcessor processor = new JFRProcessor(new JFREventModifier() {}, recording1);
            var recording = processor.processRecordingFilesWithoutAnyProcessing(inputs, output);
            recording.close();

            java.nio.file.Files.write(outputPath, output.toByteArray());
        } finally {
            // Close input recording files
            for (RecordingFile rf : inputs) {
                rf.close();
            }
        }

        // Verify output contains all events from all files
        helper.verify(outputPath)
                .fileExists()
                .fileNotEmpty()
                .hasEventOfType("test.SimpleEvent", 5)
                .hasEventOfType("test.ComplexEvent", 3)
                .hasEventOfType("test.NetworkEvent", 2);
    }
}