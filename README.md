# dependency-check-maven-plugin

Strictly check that dependencies were properly declared in a maven project. The project target
classes are examined at the byte code level to determine which classes are required to compile the
project. From the class dependencies, the jar level dependencies are found. This plugin detects
"declared but unused" and "used but undeclared" dependencies.

## Goals

There are two goals: [main](https://chonton.github.io/dependency-check-maven-plugin/main-mojo.html) checks the main 
target classes; and [test](https://chonton.github.io/dependency-check-maven-plugin/local-mojo.html) checks the test
target classes.

Mojo details at [plugin info](https://chonton.github.io/dependency-check-maven-plugin/plugin-info.html)

## Parameters

The followings parameters can be set with a maven property **dependency-check.**_<parameter_name\>_. e.g. skip 
parameter can be set from command line -D dependency-check.skip=true

| Parameter           | Default | Description                                         |
|---------------------|---------|-----------------------------------------------------|
| fail                | true    | Fail build when incorrect declarations found        |
| skip                | false   | Skip execution of plugin                            |

The following parameters are sets of dependencies which are to be ignored in various ways. Each of
these parameters are filters.

| Parameter                        | Description                                         |
|----------------------------------|-----------------------------------------------------|
| ignoreDependencies               | Ignore incorrect declarations of these dependencies |
| ignoreUnusedDeclaredDependencies | Ignore dependencies if they are declared but unused |
| ignoreUsedUndeclaredDependencies | Ignore dependencies if they are used but undeclared |

## Filter Syntax

Dependency filters have multiple segments: `[groupId]:[artifactId]:[type]:[version]`. Each filter
segment is optional and supports full and partial `*` wildcards. An empty pattern segment is treated
as an implicit wildcard.

## Requirements

- Maven 3.5 or later
- Java 11 or later

## Typical Use

```xml

<build>
  <plugins>

    <plugin>
      <groupId>org.honton.chas</groupId>
      <artifactId>dependency-check-maven-plugin</artifactId>
      <version>1.0.0</version>
      <executions>
        <execution>
          <id>check-main-dependencies</id>
          <goals>
            <goal>main</goal>
            <goal>remote</goal>
          </goals>
          <configuration>
            <ignoreUnusedDeclaredDependencies>
              <dependency>org.slf4j:slf4j-api</dependency>
            </ignoredUnusedDeclaredDependencies>
          </configuration>
        </execution>
      </executions>
    </plugin>

  </plugins>
</build>
```
