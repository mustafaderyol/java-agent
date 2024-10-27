# Java Agent

A Java agent is a component that runs on the Java platform and is used to modify or monitor the behavior of Java applications. Java agents are typically programs that run on the JVM (Java Virtual Machine) and come into play during the class loading phase of the application.

Key Features:

- Class Modification: Java agents have the ability to change the bytecode of classes as they are loaded. This can be used to alter the behavior of the application at runtime.
- Monitoring: They can be used for performance monitoring, debugging, or analyzing application behavior.
- Profiling: Java agents can collect information about the application's resource usage or performance.
- Instrumentation: Java agents provide the capability to change or monitor application code on the JVM using the java.lang.instrument package.

**Byte Buddy**: A powerful library for creating and modifying dynamic Java classes. Its user-friendly API allows for easy bytecode manipulation.

**Javassist**: A library that enables easy modification of Java bytecode. It offers a simple API for dynamically changing classes.

**ASM**: A library used for low-level bytecode manipulation. It is highly performant but may require more complexity.

**CGLIB**: A library for creating class-based proxies and method interception. It is commonly used alongside the Spring Framework.

**AspectJ**: A library used for AOP (Aspect-Oriented Programming). It supports injectable aspects at the method level.

**Spring AOP**: The AOP module of the Spring Framework, which provides capabilities for method redirection and proxy creation.

**Dagger**: Primarily used for dependency injection, but it can also be utilized for bytecode manipulation in some cases.

### Byte Buddy - Example Project
In this project, the execution time of each method has been calculated and logged. The logs are written to a file at specified intervals. Enums, abstract classes, and interfaces are not logged. Additionally, annotations specified in the config file are ignored.

Run Script:
```
java -javaagent:/agent.jar=configFilePath=./config-file.json -jar your-application.jar
```
Example config-file.json -> https://github.com/mustafaderyol/java-agent/blob/main/src/main/resources/config-file.json