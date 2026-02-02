# ANTLR Grammar Library

This directory contains ANTLR 4 grammars from the official [grammars-v4](https://github.com/antlr/grammars-v4) repository, organized by language and version.

## Directory Structure

```
antlr4/
├── {language}/
│   └── {version}/
│       └── *.g4
```

## Available Grammars

### JVM Languages
| Language | Directory | Files |
|----------|-----------|-------|
| Java 8 | `java/8/` | Java8Lexer.g4, Java8Parser.g4 |
| Java 9 | `java/9/` | Java9Lexer.g4, Java9Parser.g4 |
| Java 20 | `java/20/` | Java20Lexer.g4, Java20Parser.g4 |
| Java Latest | `java/latest/` | JavaLexer.g4, JavaParser.g4 |
| Kotlin | `kotlin/spec/` | KotlinLexer.g4, KotlinParser.g4, UnicodeClasses.g4 |
| Scala | `scala/scala/` | Scala.g4 |
| Clojure | `clojure/clj/` | Clojure.g4 |

### Web Languages
| Language | Directory | Files |
|----------|-----------|-------|
| JavaScript | `javascript/es/` | JavaScriptLexer.g4, JavaScriptParser.g4 |
| TypeScript | `typescript/ts/` | TypeScriptLexer.g4, TypeScriptParser.g4 |
| HTML | `html/html/` | HTMLLexer.g4, HTMLParser.g4 |
| CSS3 | `css/css3/` | css3Lexer.g4, css3Parser.g4 |
| LESS | `less/less/` | LessLexer.g4, LessParser.g4 |
| SCSS | `scss/scss/` | ScssLexer.g4, ScssParser.g4 |
| PHP | `php/php/` | PhpLexer.g4, PhpParser.g4 |

### Systems Languages
| Language | Directory | Files |
|----------|-----------|-------|
| C | `c/c11/` | CLexer.g4, CParser.g4 |
| C++ (14) | `cpp/14/` | CPP14Lexer.g4, CPP14Parser.g4 |
| C# | `csharp/cs/` | CSharpLexer.g4, CSharpParser.g4, CSharpPreprocessorParser.g4 |
| Rust | `rust/rust/` | RustLexer.g4, RustParser.g4 |
| Go | `go/go/` | GoLexer.g4, GoParser.g4 |
| Zig | `zig/zig/` | ZigLexer.g4, ZigParser.g4 |
| V | `v/vlang/` | V.g4 |

### Scripting Languages
| Language | Directory | Files |
|----------|-----------|-------|
| Python 3 | `python/3/` | Python3Lexer.g4, Python3Parser.g4 |
| Lua | `lua/lua/` | LuaLexer.g4, LuaParser.g4 |
| AWK | `awk/awk/` | awk.g4 |

### Functional Languages
| Language | Directory | Files |
|----------|-----------|-------|
| Haskell | `haskell/hs/` | HaskellLexer.g4, HaskellParser.g4 |
| Erlang | `erlang/erl/` | Erlang.g4 |
| Elixir | `elixir/ex/` | ElixirLexer.g4, ElixirParser.g4 |
| Lisp | `lisp/lisp/` | lisp.g4 |

### Data Formats
| Format | Directory | Files |
|--------|-----------|-------|
| JSON | `json/json/` | JSON.g4 |
| JSON5 | `json5/json5/` | JSON5.g4 |
| XML | `xml/xml/` | XMLLexer.g4, XMLParser.g4 |
| TOML | `toml/toml/` | TomlLexer.g4, TomlParser.g4 |

### Query Languages
| Language | Directory | Files |
|----------|-----------|-------|
| GraphQL | `graphql/spec/` | GraphQL.g4 |
| SPARQL | `sparql/sparql/` | SparqlLexer.g4, SparqlParser.g4 |
| Cypher | `cypher/cypher/` | CypherLexer.g4, CypherParser.g4 |
| XPath 1.0 | `xpath/1/` | xpath.g4 |
| XPath 3.1 | `xpath/31/` | XPath31Lexer.g4, XPath31Parser.g4 |

### SQL Dialects
| Dialect | Directory | Files |
|---------|-----------|-------|
| PL/SQL (Oracle) | `sql/plsql/` | PlSqlLexer.g4, PlSqlParser.g4 |
| MySQL | `sql/mysql/` | MySQLLexer.g4, MySQLParser.g4 |
| PostgreSQL | `sql/postgresql/` | PostgreSQLLexer.g4, PostgreSQLParser.g4 |
| SQLite | `sql/sqlite/` | SQLiteLexer.g4, SQLiteParser.g4 |
| T-SQL (SQL Server) | `sql/tsql/` | TSqlLexer.g4, TSqlParser.g4 |
| Hive v4 | `sql/hive/v4/` | HiveLexer.g4, HiveParser.g4 |
| ClickHouse | `sql/clickhouse/` | ClickHouseLexer.g4, ClickHouseParser.g4 |
| Snowflake | `sql/snowflake/` | SnowflakeLexer.g4, SnowflakeParser.g4 |

### Protocol/Schema Languages
| Language | Directory | Files |
|----------|-----------|-------|
| Protobuf 2 | `protobuf/2/` | Protobuf2.g4 |
| Protobuf 3 | `protobuf/3/` | Protobuf3.g4 |
| Thrift | `thrift/thrift/` | Thrift.g4 |
| WebIDL | `webidl/idl/` | WebIDL.g4 |

### Hardware Description
| Language | Directory | Files |
|----------|-----------|-------|
| Verilog | `verilog/verilog/` | VerilogLexer.g4, VerilogParser.g4 |
| VHDL | `vhdl/vhdl/` | vhdl.g4 |
| GLSL | `glsl/glsl/` | GLSLLexer.g4, GLSLParser.g4 |

### Infrastructure/DevOps
| Language | Directory | Files |
|----------|-----------|-------|
| Terraform | `terraform/tf/` | terraform.g4 |
| Bicep | `bicep/bicep/` | Bicep.g4 |

### Other Languages
| Language | Directory | Files |
|----------|-----------|-------|
| Swift 5 | `swift/swift/` | Swift5Lexer.g4, Swift5Parser.g4 |
| Dart 2 | `dart/dart/` | Dart2Lexer.g4, Dart2Parser.g4 |
| Objective-C | `objc/objc/` | ObjectiveCLexer.g4, ObjectiveCParser.g4 |
| Pascal | `pascal/pascal/` | pascal.g4 |
| Ada 2012 | `ada/2012/` | AdaLexer.g4, AdaParser.g4 |
| Fortran 77 | `fortran/77/` | Fortran77Lexer.g4, Fortran77Parser.g4 |
| Fortran 90 | `fortran/90/` | Fortran90Lexer.g4, Fortran90Parser.g4 |
| COBOL 85 | `cobol/cbl/` | Cobol85.g4 |
| MATLAB | `matlab/m/` | matlab.g4 |
| Smalltalk | `smalltalk/st/` | Smalltalk.g4 |
| Solidity | `solidity/sol/` | SolidityLexer.g4, SolidityParser.g4 |
| Apex | `apex/apex/` | apex.g4 |
| WebAssembly Text | `wat/wasm/` | WatLexer.g4, WatParser.g4 |
| LLVM IR | `llvm/ir/` | LLVMIR.g4 |

## Usage

### Gradle Configuration

Configure the grammar to use via gradle properties:

```bash
# Use Java 20 grammar
./gradlew build -PantlrLanguage=java -PantlrVersion=20

# Use Kotlin grammar
./gradlew build -PantlrLanguage=kotlin -PantlrVersion=spec

# Use default (Java latest)
./gradlew build
```

### Adding New Grammars

1. Create the directory structure: `antlr4/{language}/{version}/`
2. Download grammar files from [grammars-v4](https://github.com/antlr/grammars-v4)
3. Update this README

## License

All grammars are sourced from the [ANTLR grammars-v4](https://github.com/antlr/grammars-v4) repository.
Please refer to the original repository for licensing information for each grammar.
