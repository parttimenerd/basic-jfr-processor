# Java Thread Dump Parser Library

[![CI](https://github.com/parttimenerd/basic-jfr-processor/actions/workflows/ci.yml/badge.svg)](https://github.com/parttimenerd/jthreaddump/actions/workflows/ci.yml)

A small Java library for using the 
[JMC Flight Recorder (JFR) APIs](https://central.sonatype.com/artifact/org.openjdk.jmc/flightrecorder.writer) 
to process and write [JFR API Events](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/consumer/RecordedEvent.html).

## Features

- Roundtrip test: Read JFR file -> process -> write JFR file -> and the events are the same
- Supports all JFR features (that I can think of)
- Supports modifying events

It's the foundation of [jfr-redact](https://github.com/parttimenerd/jfr-redact)

### Maven

```xml
<dependency>
    <groupId>me.bechberger</groupId>
    <artifactId>basic-jfr-processor</artifactId>
    <version>0.1.2</version>
</dependency>
```

### Usage

```java
// Create a modifier that drops events
JFREventModifier modifier = new JFREventModifier() {
    @Override
    public boolean shouldRemoveEvent(RecordedEvent event) {
        return event.getEventType().getName().equals("example.UserLogin");
    }
};

// Process the recording
JFRProcessor processor = new JFRProcessor(modifier, inputFile);
try (FileOutputStream out = new FileOutputStream(outputFile.toFile())) {
    processor.process(out).close();
}
```

See the [SimpleProcessorExample.java](src/main/java/me/bechberger/jfr/examples/SimpleProcessorExample.java) for a complete working example.

## Testing

```bash
# Run all tests
mvn test
```

## Deployment

Use the included Python script to automate version bumps and releases:

```bash
# Bump minor version, run tests, build
./release.py

./release.py --patch

# Full release: bump, test, build, deploy, commit, tag, push
./release.py --deploy --push

# Dry run to see what would happen
./release.py --dry-run
```

Support, Feedback, Contributing
-------------------------------
This project is open to feature requests/suggestions, bug reports etc.
via [GitHub](https://github.com/parttimenerd/basic-jfr-processor/issues) issues.
Contribution and feedback are encouraged and always welcome.


License
-------
MIT, Copyright 2026 SAP SE or an SAP affiliate company, Johannes Bechberger and contributors