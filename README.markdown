Maven Plugin for Hsoy Templates
===============================

A Maven 3.0 plugin for compiling Hsoy Templates into Javascript and Java

Latest versions: `0.3` for stable version, or `0.4-SNAPSHOT` for development

Add to your project
-------------------

```xml
<plugin>
    <groupId>com.the6hours</groupId>
    <artifactId>maven-hsoy-templates</artifactId>
    <version>0.3</version>
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

Usage
-----

Hsoy Templates going to be compiled by default on each compilation
```
mvn compile
```

Or you can compile Hsoy Templates to JS w/o compiling whole project:
```
mvn hsoy-templates:compile
```

License
-------

Licensed under the Apache License, Version 2.0