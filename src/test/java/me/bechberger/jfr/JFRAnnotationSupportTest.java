package me.bechberger.jfr;

import me.bechberger.jfr.util.JFRTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static me.bechberger.jfr.util.JFRTestEvents.*;

/**
 * Comprehensive tests for all JFR event annotations and RedactionEngine.NONE.
 * Tests verify that all JFR annotations are properly supported during roundtrip processing.
 */
public class JFRAnnotationSupportTest {

    @TempDir
    Path tempDir;

    private JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new JFRTestHelper(tempDir);
    }

    // ========== RedactionEngine.NONE Tests ==========

    @Test
    public void noModificationPreservesSimpleEvent() throws IOException {
        Path recording = helper.recording()
                .addSimpleEvent("Original Message", 42, true)
                .build();

        Path processed = helper.process()
                .from(recording)
                .withNoModification()
                .process();

        helper.verify(processed)
                .fileExists()
                .hasEvents()
                .findEvent("test.SimpleEvent")
                .hasString("message", "Original Message")
                .hasInt("count", 42)
                .hasBoolean("flag", true);
    }

    @Test
    public void noModificationPreservesComplexEvent() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            ComplexEvent event = new ComplexEvent();
            event.stringField = "test string";
            event.intField = 12345;
            event.longField = 9876543210L;
            event.floatField = 3.14159f;
            event.doubleField = 2.718281828;
            event.booleanField = true;
            event.byteField = (byte) 127;
            event.shortField = (short) 32000;
            event.charField = 'Z';
            event.commit();
        });

        Path processed = helper.process()
                .from(recording)
                .process();

        helper.verify(processed)
                .findEvent("test.ComplexEvent")
                .hasString("stringField", "test string")
                .hasInt("intField", 12345)
                .hasLong("longField", 9876543210L)
                .hasFloat("floatField", 3.14159f, 0.0001f)
                .hasDouble("doubleField", 2.718281828, 0.000001)
                .hasBoolean("booleanField", true)
                .hasByte("byteField", (byte) 127)
                .hasShort("shortField", (short) 32000)
                .hasChar("charField", 'Z');
    }

    @Test
    public void roundtripWithoutModificationPreservesAllData() throws IOException {
        helper.roundtrip(() -> {
            for (int i = 0; i < 5; i++) {
                SimpleEvent event = new SimpleEvent();
                event.message = "Message " + i;
                event.count = i * 10;
                event.flag = i % 2 == 0;
                event.commit();
            }
        })
        .withNoModification()
        .eventCountPreserved()
        .eventTypeCountPreserved("test.SimpleEvent")
        .fieldPreserved("test.SimpleEvent", "message")
        .fieldPreserved("test.SimpleEvent", "count")
        .fieldPreserved("test.SimpleEvent", "flag");
    }

    // ========== Annotation Support Tests ==========

    @Test
    public void annotatedEventPreservesAllAnnotations() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            AnnotatedEvent event = new AnnotatedEvent();
            event.eventTime = System.currentTimeMillis();
            event.duration2 = 1000000L; // 1ms in nanos
            event.dataSize = 1024 * 1024; // 1MB
            event.frequency = 60; // 60 Hz
            event.memoryAddress = 0x7fff5fbff000L;
            event.percentage = 0.95f; // 95%
            event.unsignedValue = Integer.MAX_VALUE;
            event.thread = Thread.currentThread();
            event.clazz = AnnotatedEvent.class;
            event.experimentalFlag = true;
            event.commit();
        });

        helper.verify(recording)
                .fileExists()
                .hasEvents()
                .findEvent("test.AnnotatedEvent")
                .fieldNotNull("eventTime")
                .fieldNotNull("duration2")
                .fieldNotNull("dataSize")
                .fieldNotNull("frequency")
                .fieldNotNull("memoryAddress")
                .fieldNotNull("percentage")
                .fieldNotNull("unsignedValue")
                .fieldNotNull("thread")
                .fieldNotNull("clazz")
                .hasBoolean("experimentalFlag", true);
    }

    @Test
    public void timestampEventPreservesAllTimestampAnnotations() throws IOException {
        long now = System.nanoTime();
        Path recording = helper.createTestRecording(() -> {
            TimestampEvent event = new TimestampEvent();
            event.timestampStart = now;
            event.timestampEnd = now + 1000000000L; // +1 second
            event.durationNanos = 1000L;
            event.durationMicros = 500L;
            event.durationMillis = 250L;
            event.durationSeconds = 10L;
            event.commit();
        });

        helper.verify(recording)
                .fileExists()
                .hasEvents()
                .findEvent("test.TimestampEvent")
                .hasLong("timestampStart", now)
                .hasLong("timestampEnd", now + 1000000000L)
                .hasLong("durationNanos", 1000L)
                .hasLong("durationMicros", 500L)
                .hasLong("durationMillis", 250L)
                .hasLong("durationSeconds", 10L);
    }

    @Test
    public void dataAmountEventPreservesAllDataAnnotations() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            DataAmountEvent event = new DataAmountEvent();
            event.bytes = 1024 * 1024; // 1MB
            event.bits = 8 * 1024; // 8Kb
            event.hertz = 2400000000L; // 2.4 GHz
            event.address = 0x00007fff5fbff5a0L;
            event.commit();
        });

        helper.verify(recording)
                .fileExists()
                .hasEvents()
                .findEvent("test.DataAmountEvent")
                .hasLong("bytes", 1024 * 1024)
                .hasLong("bits", 8 * 1024)
                .hasLong("hertz", 2400000000L)
                .hasLong("address", 0x00007fff5fbff5a0L);
    }

    @Test
    public void threadEventPreservesThreadAndClassAnnotations() throws IOException {
        Thread currentThread = Thread.currentThread();
        Path recording = helper.createTestRecording(() -> {
            ThreadEvent event = new ThreadEvent();
            event.thread = Thread.currentThread();
            event.clazz = ThreadEvent.class;
            event.threadName = Thread.currentThread().getName();
            event.threadPriority = Thread.currentThread().getPriority();
            event.isDaemon = Thread.currentThread().isDaemon();
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.ThreadEvent")
                .fieldNotNull("thread")
                .fieldNotNull("clazz")
                .hasString("threadName", currentThread.getName())
                .hasInt("threadPriority", currentThread.getPriority())
                .hasBoolean("isDaemon", currentThread.isDaemon());
    }

    @Test
    public void contentTypeEventPreservesContentTypeAnnotations() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            ContentTypeEvent event = new ContentTypeEvent();
            event.bytes = 2048;
            event.percentage = 0.75f;
            event.memoryAddress = 0x123456789ABCDEFL;
            event.unsigned = 4294967295L; // Max unsigned 32-bit
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.ContentTypeEvent")
                .hasLong("bytes", 2048)
                .hasFloat("percentage", 0.75f, 0.001f)
                .hasLong("memoryAddress", 0x123456789ABCDEFL)
                .hasLong("unsigned", 4294967295L);
    }

    // ========== Roundtrip Tests with Complex Annotations ==========

    @Test
    public void roundtripPreservesAnnotatedEventWithoutRedaction() throws IOException {
        long testTime = System.currentTimeMillis();
        helper.roundtrip(() -> {
            AnnotatedEvent event = new AnnotatedEvent();
            event.eventTime = testTime;
            event.duration2 = 5000000L;
            event.dataSize = 1024;
            event.frequency = 100;
            event.memoryAddress = 0xDEADBEEFL;
            event.percentage = 0.5f;
            event.unsignedValue = 1000;
            event.thread = Thread.currentThread();
            event.clazz = String.class;
            event.experimentalFlag = false;
            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventTypeCountPreserved("test.AnnotatedEvent")
        .fieldPreserved("test.AnnotatedEvent", "eventTime")
        .fieldPreserved("test.AnnotatedEvent", "duration")
        .fieldPreserved("test.AnnotatedEvent", "dataSize")
        .fieldPreserved("test.AnnotatedEvent", "frequency")
        .fieldPreserved("test.AnnotatedEvent", "memoryAddress")
        .fieldPreserved("test.AnnotatedEvent", "percentage")
        .fieldPreserved("test.AnnotatedEvent", "unsignedValue")
        .fieldPreserved("test.AnnotatedEvent", "experimentalFlag");
    }

    @Test
    public void roundtripPreservesMixedComplexEventsWithoutRedaction() throws IOException {
        helper.roundtrip(() -> {
            // Timestamp event
            TimestampEvent ts = new TimestampEvent();
            ts.timestampStart = System.nanoTime();
            ts.timestampEnd = System.nanoTime() + 1000000L;
            ts.durationNanos = 1000L;
            ts.durationMicros = 1L;
            ts.durationMillis = 1L;
            ts.durationSeconds = 1L;
            ts.commit();

            // Data amount event
            DataAmountEvent da = new DataAmountEvent();
            da.bytes = 8192;
            da.bits = 65536;
            da.hertz = 1000000L;
            da.address = 0x1234L;
            da.commit();

            // Thread event
            ThreadEvent te = new ThreadEvent();
            te.thread = Thread.currentThread();
            te.clazz = Object.class;
            te.threadName = "test-thread";
            te.threadPriority = 5;
            te.isDaemon = false;
            te.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventTypeCountPreserved("test.TimestampEvent")
        .eventTypeCountPreserved("test.DataAmountEvent")
        .eventTypeCountPreserved("test.ThreadEvent")
        .eventOrderPreserved();
    }

    // ========== Comprehensive DataAmount Tests ==========

    @Test
    public void comprehensiveDataAmountEventPreservesAllSizes() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            ComprehensiveDataAmountEvent event = new ComprehensiveDataAmountEvent();
            event.bytesValue = 1024L; // 1KB
            event.bitsValue = 8192L; // 1KB in bits
            event.kilobytes = 1024L * 10; // 10KB
            event.megabytes = 1024L * 1024 * 5; // 5MB
            event.gigabytes = 1024L * 1024 * 1024 * 2; // 2GB
            event.transferRate = 1000000L; // 1MB/s
            event.bandwidthUsage = 0.75f; // 75%
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.ComprehensiveDataAmountEvent")
                .hasLong("bytesValue", 1024L)
                .hasLong("bitsValue", 8192L)
                .hasLong("kilobytes", 10 * 1024L)
                .hasLong("megabytes", 5L * 1024 * 1024)
                .hasLong("gigabytes", 2L * 1024 * 1024 * 1024)
                .hasLong("transferRate", 1000000L)
                .hasFloat("bandwidthUsage", 0.75f, 0.001f);
    }

    @Test
    public void dataAmountRoundtripPreservesAllFields() throws IOException {
        helper.roundtrip(() -> {
            ComprehensiveDataAmountEvent event = new ComprehensiveDataAmountEvent();
            event.bytesValue = 512L;
            event.bitsValue = 4096L;
            event.kilobytes = 1024L;
            event.megabytes = 1024L * 1024;
            event.gigabytes = 1024L * 1024 * 1024;
            event.transferRate = 100000L;
            event.bandwidthUsage = 0.5f;
            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .fieldPreserved("test.ComprehensiveDataAmountEvent", "bytesValue")
        .fieldPreserved("test.ComprehensiveDataAmountEvent", "bitsValue")
        .fieldPreserved("test.ComprehensiveDataAmountEvent", "kilobytes")
        .fieldPreserved("test.ComprehensiveDataAmountEvent", "megabytes")
        .fieldPreserved("test.ComprehensiveDataAmountEvent", "gigabytes")
        .fieldPreserved("test.ComprehensiveDataAmountEvent", "transferRate")
        .fieldPreserved("test.ComprehensiveDataAmountEvent", "bandwidthUsage");
    }

    @Test
    public void allContentTypesEventPreservesAllFields() throws IOException {
        long now = System.currentTimeMillis();
        Thread currentThread = Thread.currentThread();

        Path recording = helper.createTestRecording(() -> {
            AllContentTypesEvent event = new AllContentTypesEvent();
            // Data amounts
            event.bytes = 8192L;
            event.bits = 65536L;
            // Time-related
            event.timestamp = now;
            event.timespanNanos = 1000L;
            event.timespanMicros = 500L;
            event.timespanMillis = 250L;
            event.timespanSeconds = 10L;
            // Numeric content types
            event.frequency = 2400000000L;
            event.memoryAddress = 0xDEADBEEFCAFEBABEL;
            event.percentage = 0.85f;
            event.unsigned = 4294967295L;
            // Reference types
            event.thread = currentThread;
            event.clazz = AllContentTypesEvent.class;
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.AllContentTypesEvent")
                .hasLong("bytes", 8192L)
                .hasLong("bits", 65536L)
                .hasLong("timestamp", now)
                .hasLong("timespanNanos", 1000L)
                .hasLong("timespanMicros", 500L)
                .hasLong("timespanMillis", 250L)
                .hasLong("timespanSeconds", 10L)
                .hasLong("frequency", 2400000000L)
                .hasLong("memoryAddress", 0xDEADBEEFCAFEBABEL)
                .hasFloat("percentage", 0.85f, 0.001f)
                .hasLong("unsigned", 4294967295L)
                .fieldNotNull("thread")
                .fieldNotNull("clazz");
    }

    @Test
    public void allContentTypesRoundtripPreservesAllFields() throws IOException {
        long testTime = System.currentTimeMillis();

        helper.roundtrip(() -> {
            AllContentTypesEvent event = new AllContentTypesEvent();
            event.bytes = 1024L;
            event.bits = 8192L;
            event.timestamp = testTime;
            event.timespanNanos = 100L;
            event.timespanMicros = 10L;
            event.timespanMillis = 1L;
            event.timespanSeconds = 1L;
            event.frequency = 1000000L;
            event.memoryAddress = 0x1234L;
            event.percentage = 0.5f;
            event.unsigned = 1000L;
            event.thread = Thread.currentThread();
            event.clazz = Object.class;
            event.commit();
        })
        .withNoModification()
        .eventCountPreserved()
        .eventTypeCountPreserved("test.AllContentTypesEvent")
        .fieldPreserved("test.AllContentTypesEvent", "bytes")
        .fieldPreserved("test.AllContentTypesEvent", "bits")
        .fieldPreserved("test.AllContentTypesEvent", "timestamp")
        .fieldPreserved("test.AllContentTypesEvent", "timespanNanos")
        .fieldPreserved("test.AllContentTypesEvent", "frequency")
        .fieldPreserved("test.AllContentTypesEvent", "percentage")
        .fieldPreserved("test.AllContentTypesEvent", "unsigned");
    }

    @Test
    public void dataAmountHandlesZeroValues() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            ComprehensiveDataAmountEvent event = new ComprehensiveDataAmountEvent();
            event.bytesValue = 0L;
            event.bitsValue = 0L;
            event.kilobytes = 0L;
            event.megabytes = 0L;
            event.gigabytes = 0L;
            event.transferRate = 0L;
            event.bandwidthUsage = 0.0f;
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.ComprehensiveDataAmountEvent")
                .hasLong("bytesValue", 0L)
                .hasLong("bitsValue", 0L)
                .hasFloat("bandwidthUsage", 0.0f, 0.001f);
    }

    @Test
    public void dataAmountHandlesMaxValues() throws IOException {
        Path recording = helper.createTestRecording(() -> {
            ComprehensiveDataAmountEvent event = new ComprehensiveDataAmountEvent();
            event.bytesValue = Long.MAX_VALUE;
            event.bitsValue = Long.MAX_VALUE;
            event.kilobytes = Long.MAX_VALUE;
            event.megabytes = Long.MAX_VALUE / 1024; // Avoid overflow
            event.gigabytes = Long.MAX_VALUE / (1024 * 1024); // Avoid overflow
            event.transferRate = Long.MAX_VALUE;
            event.bandwidthUsage = 1.0f;
            event.commit();
        });

        helper.verify(recording)
                .findEvent("test.ComprehensiveDataAmountEvent")
                .hasLong("bytesValue", Long.MAX_VALUE)
                .hasLong("bitsValue", Long.MAX_VALUE)
                .hasFloat("bandwidthUsage", 1.0f, 0.001f);
    }
}