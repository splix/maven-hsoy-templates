Maven Plugin for HAML Closures
==============================

A Maven 3.0 plugin for compiling HAML Closures into JS

Usage
-----

```xml
<plugin>
    <groupId>com.the6hours</groupId>
    <artifactId>maven-haml-closures</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>compile</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <inputFiles>
            <directory>${basedir}/src/main/closures</directory>  <!-- input files -->
            <includes>
                <include>**/*.hsoy</include>
            </includes>
        </inputFiles>
        <outputFile>${basedir}/src/main/webapp/js/templates.js</outputFile> <!-- output file -->
    </configuration>
</plugin>
```

License
-------

Licensed under the Apache License, Version 2.0