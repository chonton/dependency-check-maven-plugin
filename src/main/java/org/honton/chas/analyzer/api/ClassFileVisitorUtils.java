package org.honton.chas.analyzer.api;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import lombok.experimental.UtilityClass;
import org.codehaus.plexus.util.DirectoryScanner;
import org.honton.chas.analyzer.spi.ClassFileVisitor;

/**
 * Utility to visit classes in a library given either as a jar file or an exploded directory.
 */
@UtilityClass
public final class ClassFileVisitorUtils {

  private static final String DOT_CLASS = ".class";

  /**
   * Analyze all classes in a directory or jar.
   *
   * @param path The directory or jar to scan for classes.
   * @param visitorFactory The factory for visitors
   * @param handler the method which is invoked upon any IOException; the String parameter is the
   *     className, if known
   */
  public  void accept(
      Path path,
      Function<String, ClassFileVisitor> visitorFactory,
      BiConsumer<String, IOException> handler) {
    if (Files.isDirectory(path)) {
      acceptDirectory(path, visitorFactory, handler);
    } else if (Files.isReadable(path)) {
      acceptJar(path, visitorFactory, handler);
    } else {
      throw new IllegalArgumentException(
          "Location " + path + " is not a directory and not a readable jar");
    }
  }

  private  void acceptJar(
      Path jar,
      Function<String, ClassFileVisitor> visitorFactory,
      BiConsumer<String, IOException> handler) {
    try {
      acceptJar(Files.newInputStream(jar), visitorFactory);
    } catch (IOException ioException) {
      handler.accept(jar.toString(), ioException);
    }
  }

  private  void acceptJar(InputStream is, Function<String, ClassFileVisitor> visitorFactory)
      throws IOException {
    try (JarInputStream in = new JarInputStream(is)) {
      JarEntry entry;
      while ((entry = in.getNextJarEntry()) != null) {
        String path = entry.getName();
        // ignore files like package-info.class and module-info.class
        if (path.endsWith(DOT_CLASS) && path.indexOf('-') == -1) {
          String className = pathToClassName(path);
          visitorFactory.apply(className).visitClass(className, in);
        }
      }
    }
  }

  private  void acceptDirectory(
      Path directory,
      Function<String, ClassFileVisitor> visitorFactory,
      BiConsumer<String, IOException> handler) {
    DirectoryScanner scanner = new DirectoryScanner();

    scanner.setBasedir(directory.toFile());
    scanner.setIncludes(new String[] {"**/*.class"});

    scanner.scan();

    String[] paths = scanner.getIncludedFiles();
    for (String path : paths) {
      String className = pathToClassName(path.replace(File.separatorChar, '/'));
      acceptClassInDirectory(directory, path, className, visitorFactory.apply(className), handler);
    }
  }

  private  void acceptClassInDirectory(
      Path directory,
      String path,
      String className,
      ClassFileVisitor visitor,
      BiConsumer<String, IOException> handler) {
    Path classLocation = directory.resolve(path);

    try (InputStream in = Files.newInputStream(classLocation)) {
      visitor.visitClass(className, in);
    } catch (IOException ioException) {
      handler.accept(path, ioException);
    }
  }

  private  String pathToClassName(String path) {
    return path.substring(0, path.length() - DOT_CLASS.length()).replace('/', '.');
  }
}
