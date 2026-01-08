package me.bechberger.jfr;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import me.bechberger.jfr.util.JFRTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tests using real JDK JFR events (Thread.sleep, System.gc, JVMInformation, etc.)
 * to verify the redaction processor can handle actual JVM-generated events.
 *
 * These tests perform actual roundtrip processing through the redaction engine
 * using the existing test framework to ensure real-world events are preserved correctly.
 *
 * The comprehensive verification checks:
 * - All field values
 * - Field annotations and their values
 * - Event type annotations
 * - Field nullability
 * - Event metadata
 */
@SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
public class JFRRealWorldEventsTest {

    @TempDir
    Path tempDir;

    private JFRTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new JFRTestHelper(tempDir);
    }

    // ========== Parameterized Test Configuration ==========

    /**
     * Configuration wrapper for parameterized tests
     */
    public static class RedactionTestConfig {
        final String name;
        final Consumer<JFRTestHelper.RoundtripVerifier> applier;
        final Consumer<JFRTestHelper.RoundtripVerifier> verifier;

        RedactionTestConfig(String name,
                           Consumer<JFRTestHelper.RoundtripVerifier> applier,
                           Consumer<JFRTestHelper.RoundtripVerifier> verifier) {
            this.name = name;
            this.applier = applier;
            this.verifier = verifier;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // ========== Thread.sleep Events - Roundtrip Tests ==========

    @Test
    public void threadSleepEventsPreservedInRoundtrip() throws Exception {
        JFRTestHelper.RoundtripVerifier verifier = helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1));
                recording.start();

                Thread.sleep(10);
                Thread.sleep(20);
                Thread.sleep(15);

                recording.stop();
            }
        })
        .withNoModification();

        // Verify ThreadSleep events are fully preserved (if any were recorded)
        if (!verifier.getOriginalEventsOfType("jdk.ThreadSleep").isEmpty()) {
            verifier.eventsOfTypeFullyPreserved("jdk.ThreadSleep");
        }
    }

    // ========== System.gc Events - Roundtrip Tests ==========

    @Test
    public void garbageCollectionEventsPreservedInRoundtrip() throws Exception {
        JFRTestHelper.RoundtripVerifier verifier = helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                recording.enable("jdk.GarbageCollection");
                recording.enable("jdk.GCHeapSummary");
                recording.start();

                // Generate garbage and trigger GC
                @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
                List<byte[]> garbage = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    garbage.add(new byte[10240]); // 10KB each
                }
                System.gc();
                Thread.yield();

                recording.stop();
            }
        }).withNoModification();

        // Verify GC events are fully preserved (if any were recorded)
        if (!verifier.getOriginalEventsOfType("jdk.GarbageCollection").isEmpty()) {
            verifier.eventsOfTypeFullyPreserved("jdk.GarbageCollection");
        }
        if (!verifier.getOriginalEventsOfType("jdk.GCHeapSummary").isEmpty()) {
            verifier.eventsOfTypeFullyPreserved("jdk.GCHeapSummary");
        }
    }

    // ========== JVM Information Events - Roundtrip Tests ==========

    @Test
    public void jvmInformationEventsPreservedInRoundtrip() throws Exception {
        JFRTestHelper.RoundtripVerifier verifier = helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                recording.enable("jdk.JVMInformation");
                recording.start();

                Thread.sleep(50);

                recording.stop();
            }
        })
        .withNoModification();

        // Verify JVM information events are fully preserved
        if (!verifier.getOriginalEventsOfType("jdk.JVMInformation").isEmpty()) {
            verifier.eventsOfTypeFullyPreserved("jdk.JVMInformation");
        }
    }

    // ========== Object Allocation Events - Roundtrip Tests ==========

    @Test
    public void objectAllocationEventsPreservedInRoundtrip() throws Exception {
        JFRTestHelper.RoundtripVerifier verifier = helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                recording.enable("jdk.ObjectAllocationInNewTLAB");
                recording.enable("jdk.ObjectAllocationOutsideTLAB");
                recording.start();

                @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
                List<Object> objects = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    objects.add(new byte[1024]);
                    objects.add(new HashMap<String, String>());
                    objects.add("String " + i);
                }

                recording.stop();
            }
        })
        .withNoModification();


        // Verify allocation events are fully preserved (if any were recorded)
        if (!verifier.getOriginalEventsOfType("jdk.ObjectAllocationInNewTLAB").isEmpty()) {
            verifier.eventsOfTypeFullyPreserved("jdk.ObjectAllocationInNewTLAB");
        }
        if (!verifier.getOriginalEventsOfType("jdk.ObjectAllocationOutsideTLAB").isEmpty()) {
            verifier.eventsOfTypeFullyPreserved("jdk.ObjectAllocationOutsideTLAB");
        }
    }

    @Test
    public void classLoadingEventsPreservedInRoundtrip() throws Exception {
        JFRTestHelper.RoundtripVerifier verifier = helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                recording.enable("jdk.ClassLoad");
                recording.enable("jdk.ClassDefine");
                recording.start();

                // Trigger class loading
                try {
                    Class.forName("java.util.concurrent.ConcurrentHashMap");
                    Class.forName("java.util.concurrent.atomic.AtomicInteger");
                    Class.forName("java.util.stream.Collectors");
                } catch (ClassNotFoundException e) {
                    // Ignore
                }

                recording.stop();
            }
        })
        .withNoModification();


        // Verify class loading events are fully preserved (if any were recorded)
        if (!verifier.getOriginalEventsOfType("jdk.ClassLoad").isEmpty()) {
            verifier.eventsOfTypeFullyPreserved("jdk.ClassLoad");
        }
        if (!verifier.getOriginalEventsOfType("jdk.ClassDefine").isEmpty()) {
            verifier.eventsOfTypeFullyPreserved("jdk.ClassDefine");
        }
    }

    // ========== Exception Events - Roundtrip Tests ==========

    @Test
    public void exceptionEventsPreservedInRoundtrip() throws Exception {
        helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                recording.enable("jdk.JavaExceptionThrow");
                recording.start();

                for (int i = 0; i < 5; i++) {
                    try {
                        throw new IllegalArgumentException("Test exception " + i);
                    } catch (RuntimeException e) {
                        // Expected
                    }
                }

                recording.stop();
            }
        })
        .withNoModification()
        .allEventsFullyPreserved();

    }

    @Test
    public void fileIOEventsPreservedInRoundtrip() throws Exception {
        helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                recording.enable("jdk.FileRead");
                recording.enable("jdk.FileWrite");
                recording.start();

                Path testFile = tempDir.resolve("test-io.txt");
                Files.writeString(testFile, "Test content\n".repeat(10));
                @SuppressWarnings("unused")
                String content = Files.readString(testFile);
                Files.delete(testFile);

                recording.stop();
            }
        })
        .withNoModification()
        .allEventsFullyPreserved();

    }

    @Test
    public void monitorEventsPreservedInRoundtrip() throws Exception {
        helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                recording.enable("jdk.JavaMonitorEnter");
                recording.enable("jdk.JavaMonitorWait");
                recording.start();

                Object lock = new Object();
                for (int i = 0; i < 3; i++) {
                    synchronized (lock) {
                        Thread.sleep(5);
                    }
                }

                recording.stop();
            }
        })
        .withNoModification()
        .allEventsFullyPreserved();

    }

    @Test
    public void mixedRealWorldEventsPreservedInRoundtrip() throws Exception {
        helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1));
                recording.enable("jdk.GarbageCollection");
                recording.enable("jdk.ObjectAllocationInNewTLAB");
                recording.enable("jdk.JavaExceptionThrow");
                recording.enable("jdk.JVMInformation");
                recording.start();

                // Generate various events
                @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
                List<Object> allocations = new ArrayList<>();
                for (int i = 0; i < 50; i++) {
                    allocations.add(new byte[1024]);
                }

                Thread.sleep(20);
                System.gc();

                try {
                    throw new RuntimeException("Test");
                } catch (RuntimeException e) {
                    // Expected
                }

                recording.stop();
            }
        })
        .withNoModification()
        .allEventsFullyPreserved();

    }

    @Test
    public void fileIOEventsComprehensiveTest() throws Exception {
        JFRTestHelper.RoundtripVerifier verifier = helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                // Enable file I/O events
                recording.enable("jdk.FileRead");
                recording.enable("jdk.FileWrite");
                recording.enable("jdk.FileForce");
                recording.enable("jdk.ObjectAllocationInNewTLAB");
                recording.enable("jdk.GarbageCollection");
                recording.start();

                // Generate file I/O events
                Path testFile = tempDir.resolve("io-test.txt");
                for (int i = 0; i < 20; i++) {
                    Files.writeString(testFile, "Line " + i + ": " + "x".repeat(100) + "\n");
                    @SuppressWarnings("unused")
                    String content = Files.readString(testFile);
                }

                // Binary I/O
                Path binaryFile = tempDir.resolve("binary-test.dat");
                for (int i = 0; i < 10; i++) {
                    Files.write(binaryFile, new byte[1024]);
                    @SuppressWarnings("unused")
                    byte[] data = Files.readAllBytes(binaryFile);
                }

                Files.deleteIfExists(testFile);
                Files.deleteIfExists(binaryFile);
                System.gc();

                recording.stop();
            }
        })
        .withNoModification()
        .allEventsFullyPreserved();
    }

    @Test
    public void socketAndNetworkEventsPreservedInRoundtrip() throws Exception {
        JFRTestHelper.RoundtripVerifier verifier = helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                // Enable network events
                recording.enable("jdk.SocketRead");
                recording.enable("jdk.SocketWrite");
                recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1));
                recording.enable("jdk.JavaExceptionThrow");
                recording.enable("jdk.ObjectAllocationInNewTLAB");
                recording.start();

                // Create a simple server socket and connect to it
                try (var serverSocket = new java.net.ServerSocket(0)) {
                    serverSocket.setSoTimeout(100);
                    int port = serverSocket.getLocalPort();

                    Thread serverThread = new Thread(() -> {
                        try (var client = serverSocket.accept();
                             var in = client.getInputStream();
                             var out = client.getOutputStream()) {
                            byte[] buffer = new byte[256];
                            int read = in.read(buffer);
                            if (read > 0) {
                                out.write(buffer, 0, read);
                            }
                        } catch (Exception e) {
                            // Expected timeout
                        }
                    });
                    serverThread.start();

                    Thread.sleep(10);

                    // Client connection
                    try (var socket = new java.net.Socket("localhost", port);
                         var out = socket.getOutputStream();
                         var in = socket.getInputStream()) {
                        out.write("Test data".getBytes());
                        out.flush();
                        byte[] buffer = new byte[256];
                        @SuppressWarnings("unused")
                        int read = in.read(buffer);
                    } catch (Exception e) {
                        // Connection might fail, that's ok
                    }

                    serverThread.join(200);
                }

                recording.stop();
            }
        })
        .withNoModification()
        .allEventsFullyPreserved();
    }

    @Test
    public void comprehensiveJDKEventsPreservedInRoundtrip() throws Exception {
        JFRTestHelper.RoundtripVerifier verifier = helper.roundtrip(() -> {
            try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
                // Enable diverse JDK internal events
                recording.enable("jdk.ClassLoad");
                recording.enable("jdk.ClassDefine");
                recording.enable("jdk.Compilation");
                recording.enable("jdk.SafepointBegin");
                recording.enable("jdk.SafepointEnd");
                recording.enable("jdk.GarbageCollection");
                recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1));
                recording.enable("jdk.JavaMonitorEnter");
                recording.enable("jdk.JavaMonitorWait");
                recording.enable("jdk.JavaExceptionThrow");
                recording.enable("jdk.ObjectAllocationInNewTLAB");
                recording.enable("jdk.ThreadStart");
                recording.enable("jdk.ThreadEnd");
                recording.start();

                // Trigger class loading
                try {
                    Class.forName("java.util.concurrent.ConcurrentHashMap");
                    Class.forName("java.util.concurrent.locks.ReentrantLock");
                    Class.forName("java.security.MessageDigest");
                } catch (ClassNotFoundException e) {
                    // Ignore
                }

                // Allocations to trigger GC
                @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
                List<Object> allocations = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    allocations.add(new byte[8192]);
                    allocations.add(new HashMap<String, Object>());
                }
                System.gc();

                // Multithreaded synchronization
                Object lock = new Object();
                Thread[] threads = new Thread[3];
                for (int i = 0; i < threads.length; i++) {
                    threads[i] = new Thread(() -> {
                        try {
                            synchronized (lock) {
                                Thread.sleep(5);
                                lock.wait(5);
                            }

                            // Trigger exception
                            try {
                                throw new IllegalArgumentException("Test");
                            } catch (Exception e) {
                                // Expected
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }, "Worker-" + i);
                    threads[i].start();
                }

                for (Thread thread : threads) {
                    thread.join();
                }


                recording.stop();
            }
        })
        .withNoModification()
        .allEventsFullyPreserved();
    }
}