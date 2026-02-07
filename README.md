# gui

gui is a standalone graphical utility that displays Java source codes of ".class" files. You can browse the
reconstructed source code with the gui for instant access to methods and fields.

![](https://raw.githubusercontent.com/java-decompiler/gui/master/src/website/img/gui.png)

## Features (v2026.2.2)

### Java 21 LTS Support
- Full support for Java 8 through Java 21 LTS
- Record patterns (JEP 440)
- Pattern matching for switch (JEP 441)
- Unnamed patterns and variables (JEP 443)
- String templates (JEP 430)
- Virtual threads (JEP 444)

### Vineflower Decompiler
- [Vineflower 1.11.0](https://github.com/Vineflower/vineflower) - successor to Quiltflower/Fernflower
- Modern decompilation with Java 21+ support
- Used for "Save All Sources" (Ctrl+Alt+S)

### Multi-Language Syntax Highlighting
- **55+ languages** supported via ANTLR grammars
- **118 grammar files** from official [grammars-v4](https://github.com/antlr/grammars-v4)
- Extensible via LanguageSyntaxProvider SPI

### Supported Languages

| Category | Languages |
|----------|-----------|
| **JVM** | Java (8/9/20/latest), Kotlin, Scala, Groovy, Clojure |
| **Web** | JavaScript, TypeScript, HTML, CSS3, LESS, SCSS, PHP |
| **Systems** | C, C++, C#, Rust, Go, Zig, Swift |
| **Scripting** | Python 3, Ruby, Perl, Lua, Shell |
| **Data** | JSON, JSON5, XML, YAML, TOML |
| **Query** | GraphQL, SQL (MySQL, PostgreSQL, SQLite, T-SQL, etc.) |
| **DevOps** | Terraform, Dockerfile, Makefile, Bicep |

## Architecture

See [docs/puml/](docs/puml/) for PlantUML diagrams:

- **[module-overview.puml](docs/puml/module-overview.puml)** - Module dependencies
- **[language-syntax-provider.puml](docs/puml/language-syntax-provider.puml)** - SPI architecture
- **[antlr-grammar-organization.puml](docs/puml/antlr-grammar-organization.puml)** - Grammar structure
- **[decompiler-flow.puml](docs/puml/decompiler-flow.puml)** - Decompilation flow
- **[supported-languages.puml](docs/puml/supported-languages.puml)** - Language support

### Module Structure

```
gui/
├── api/                    # Core SPI and ANTLR grammars
│   └── src/main/antlr4/    # 118 grammar files (55+ languages)
│       ├── java/{8,9,20,latest}/
│       ├── kotlin/spec/
│       ├── sql/{mysql,postgresql,sqlite,...}/
│       └── ...
├── services/               # Language providers & TokenMakers
│   └── src/main/java/
│       └── al/clk/gui/service/language/  # 30+ providers
└── app/                    # Main application
```

### Language Syntax Provider SPI

```java
// Implement to add new language support
public interface LanguageSyntaxProvider {
    String getLanguageId();
    String getDisplayName();
    String getSyntaxStyle();
    Collection<String> getFileExtensions();
    String getTokenMakerClassName();
    void parse(CharStream input, ParseTreeListener listener);
}
```

## Build

### Requirements
- Java 21 (LTS)
- Gradle 8.5+

### Build Commands

```bash
# Clone repository
git clone https://github.com/java-decompiler/gui.git
cd gui

# Build with default Java grammar (latest)
./gradlew build

# Build with specific Java version grammar
./gradlew build -PantlrLanguage=java -PantlrVersion=8
./gradlew build -PantlrLanguage=java -PantlrVersion=20
```

### Build Outputs

- `build/libs/gui-x.y.z.jar` - Main JAR
- `build/libs/gui-x.y.z-min.jar` - Minified JAR
- `build/distributions/gui-windows-x.y.z.zip` - Windows distribution
- `build/distributions/gui-osx-x.y.z.tar` - macOS distribution
- `build/distributions/gui-x.y.z.deb` - Debian package
- `build/distributions/gui-x.y.z.rpm` - RPM package

## Usage

### Launch

- Double-click on `gui-x.y.z.jar`
- Windows: Double-click `gui.exe`
- macOS: Double-click `gui.app`
- Command line: `java -jar gui-x.y.z.jar`

### Basic Usage

- Open file: `File > Open File...` or drag & drop
- Save sources: `Ctrl+Alt+S` (uses Vineflower for JARs)
- Search: `Ctrl+F`
- Preferences: `Ctrl+Shift+P`

### Extend

```bash
# Generate IDE projects
./gradlew idea    # IntelliJ IDEA
./gradlew eclipse # Eclipse

# Launch with extensions
java -cp gui.jar:extension.jar al.clk.gui.App
```

## Version History

### v2026.2.2 (Current)
- Reorganized ANTLR grammars by language/version
- Added 118 grammar files for 55+ languages
- Multi-language syntax highlighting via SPI
- PlantUML architecture documentation

### v2024.1.0
- Java 21 LTS support
- Vineflower 1.11.0 decompiler
- ANTLR 4.13.1 parser
- GraalVM native image support
- Gradle 8.5

### v2022.3.28 (Original Fork)
- QuiltFlower 1.7.0 integration
- ANTLR-based Java parsing
- Maven naming conventions

## Forked From

- [gui](https://github.com/java-decompiler/gui) - Original project
- [Vineflower](https://github.com/Vineflower/vineflower) - Decompiler
- [grammars-v4](https://github.com/antlr/grammars-v4) - ANTLR grammars

## License

Released under the [GNU GPL v3](LICENSE).
