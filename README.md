# jPDL 3.2 Designer

A standalone desktop application for creating, editing, and maintaining jPDL 3.2 process definitions.

## Motivation

The original jPDL Designer tooling relied on aging Eclipse plugins that no longer work reliably in modern development environments. This application fills that gap: a self-contained Java/Swing tool that runs anywhere Java 21 is available, with no IDE required.

The goal is to provide a straightforward editor for jPDL 3.2 processes, fully compatible with the XML format consumed by jBPM 3.2, and designed to make routine workflow maintenance tasks as simple as possible.

## Features

- Visual drag-and-drop editor for nodes and transitions.
- Swimlane support with drag-to-reassign and inline editing.
- Syntax-highlighted XML preview with line numbers and find (Ctrl+F).
- Open and save `.xml` and `.jpdl` files.
- Node properties panel with support for task assignment, decision expressions, and action handlers.
- Event editor for node-level jBPM events and action bindings.
- Seam / CDI bean browser with classpath scanning and search.
- EL expression autocomplete driven by scanned beans and methods.
- Auto layout and fit-to-window commands.
- English and Brazilian Portuguese UI.

![Application screenshot](/printscreen.png)

## Requirements

- Java 21
- Maven 3.6+

## Build

Run the tests:

```bash
mvn test
```

Package the executable fat JAR:

```bash
mvn package
```

Run the application:

```bash
java -jar target/jdpl-designer.jar
```

## Author

Gabriel Nascimento dos Santos — gnascimento.info@gmail.com

## Disclaimer

This project is an independent open source tool for working with jPDL 3.2 process definitions.
It is not affiliated with, endorsed by, or developed for any specific organization, system, or institution.

All development was carried out independently and is intended for general-purpose use with jBPM 3.2 compatible workflows.

## License

Distributed under the MIT License. See [LICENSE](LICENSE) for details.
