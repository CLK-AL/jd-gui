# JD-GUI Version Information

## Language & Runtime Versions

| Component | Version | Notes |
|-----------|---------|-------|
| **Java** | 21 (LTS) | Long-term support release with virtual threads, pattern matching |
| **Kotlin** | 1.9.22 | Latest stable Kotlin for JVM |
| **Gradle** | 8.5 | Modern build system with Java 21 toolchain support |
| **GraalVM** | 21 | Native image support for ahead-of-time compilation |

## Java 21 Features Supported

- **JEP 440**: Record Patterns (finalized)
- **JEP 441**: Pattern Matching for switch (finalized)
- **JEP 443**: Unnamed Patterns and Variables (preview)
- **JEP 430**: String Templates (preview)
- **JEP 431**: Sequenced Collections
- **JEP 439**: Generational ZGC
- **JEP 444**: Virtual Threads (finalized)

## Key Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| **ANTLR** | 4.13.1 | Parser generator for Java syntax |
| **ASM** | 9.6 | Java bytecode manipulation (Java 21 compatible) |
| **JD-Core** | 1.1.3 | Java decompiler engine |
| **RSyntaxTextArea** | 3.4.0 | Syntax highlighting text editor |
| **Vineflower** | 1.11.0 | Modern Java 21+ decompiler (successor to Quiltflower) |
| **JUnit** | 5.10.1 | Testing framework |
| **ProGuard** | 7.4.2 | JAR minification |

## Build Tool Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| **Gradle Kotlin DSL** | Built-in | Kotlin build scripts |
| **Launch4j** | 3.0.5 | Windows executable wrapper |
| **Nebula OS Package** | 11.6.0 | DEB/RPM package generation |
| **GraalVM Native Build Tools** | 0.9.28 | Native image builds |
| **Foojay Toolchain Resolver** | 0.7.0 | Automatic JDK provisioning |

## ANTLR Java Grammar Support

The ANTLR grammar files support parsing Java code from Java 8 through Java 21:

- Java 8: Lambdas, method references, default methods
- Java 9: Modules, private interface methods
- Java 10: Local variable type inference (var)
- Java 11: Local var in lambdas
- Java 14: Switch expressions
- Java 15: Text blocks
- Java 16: Records, instanceof pattern matching
- Java 17: Sealed classes, pattern matching for switch (preview)
- Java 21: Record patterns, pattern matching for switch (finalized), unnamed patterns

## GraalVM Native Image

JD-GUI can be compiled to native executables using GraalVM Native Image:

```bash
# Build native image
native-image \
  --no-fallback \
  --enable-preview \
  -H:ConfigurationFileDirectories=src/graalvm/native-image \
  -jar build/libs/jd-gui-2024.1.0.jar \
  -o jd-gui-native
```

### Platform-Specific Builds

- **Linux**: Full support with GTK integration
- **macOS**: Full support with Cocoa integration
- **Windows**: Full support with Windows native look and feel

## CI/CD Pipeline

GitHub Actions workflow provides:

1. **Build & Test**: Java 17 and 21 on Linux, macOS, Windows
2. **Native Image**: GraalVM builds for all platforms
3. **Package**: DEB, RPM, and minified JAR artifacts
4. **Code Quality**: Static analysis and code checks
5. **Release**: Automatic release creation on tags

## Minimum Requirements

- **JRE**: Java 21 or later
- **Memory**: 256 MB RAM minimum
- **Disk**: 50 MB free space
- **OS**: Windows 10+, macOS 11+, Ubuntu 20.04+ (or equivalent Linux)
