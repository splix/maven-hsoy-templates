Maven Plugin for HAML Closures
==============================

A Maven 3.0 plugin for compiling HAML Closures into JS

Latest versions: `0.1` for stable version, or `0.2-SNAPSHOT` for development

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

Also you need to add 'The 6 Hours' Maven repository:

```xml
<pluginRepositories>
    <pluginRepository>
        <id>the6hours-release</id>
        <url>http://maven.the6hours.com/release</url>
        <releases><enabled>true</enabled></releases>
        <snapshots><enabled>false</enabled></snapshots>
    </pluginRepository>
</pluginRepositories>
```

Snapshot repo is located at `http://maven.the6hours.com/snapshot`

License
-------

Licensed under the Apache License, Version 2.0