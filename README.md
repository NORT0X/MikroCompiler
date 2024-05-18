# MikroCompiler

Welcome to the Mikro Java Compiler project! This compiler is designed to compile a language similar to Java, called Mikro Java. It is implemented in Java and utilizes Ant for building and testing. This project uses various tools and libraries such as JFlex, CUP, and Log4J to perform lexical analysis, parsing, and logging.

### Features
This project uses:

- Lexical analysis using JFlex
- Parsing using CUP
- Semantic analysis and code generation

Some specific features for this language that this compiler implements are:

- Namespaces
- Variables
- Functions
- Classes
- Static fields and statement blocks
- Single inheritance
- If Statements
- For loops
- And more...

### Prerequisites
- Java Development Kit (JDK) 1.8 or higher
- Apache Ant installed

### Project Structure
- **src/**: All source files and source files that are generated using JFlex and CUP
- **spec/**: Specification files for lexer and parser
- **lib/**: Libraries required for the project
- **test/**: MikroJava test programs with input.txt

### How to Run
It's quite easy to build and run the project. All you have to do is to open a terminal and run these commands.

First, you have to build the lexer and run the JFlex tool:

```
ant lexerGen
```

After that, you can generate parser with CUP and build JAR file:

```
ant create-jar
```

Now you can run MJCompiler.jar with a specific test like this:

```
java -jar MJCompiler.jar test303
```

Compiler will compile test303.mj into program.obj. To run this program, all you have to do is:

```
ant runObj
```
