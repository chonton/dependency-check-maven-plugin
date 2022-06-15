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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.honton.chas.analyzer.asm.visitors.CollectorClassFileVisitor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests <code>ClassFileVisitorUtils</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see ClassFileVisitorUtils
 */
class ClassFileVisitorUtilsTest {

  @TempDir Path tempDir;

  CollectorClassFileVisitor visitor = new CollectorClassFileVisitor();

  void accept(Path path) {
    ClassFileVisitorUtils.accept(path, cn -> visitor, this::handler);
  }

  private void handler(String msg, IOException e) {
    Assertions.fail(msg);
  }

  @Test
  void testAcceptJar() throws IOException {
    Path file = tempDir.resolve("test.jar");

    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
      addZipEntry(out, "a/b/c.class", "class a.b.c");
      addZipEntry(out, "x/y/z.class", "class x.y.z");
    }

    accept(file);

    Assertions.assertEquals(Set.of("a.b.c", "x.y.z"), visitor.getClasses());
  }

  @Test
  void testAcceptJarWithNonClassEntry() throws IOException {
    Path file = tempDir.resolve("test.jar");

    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
      addZipEntry(out, "a/b/c.jpg", "jpeg a.b.c");
    }

    accept(file);

    Assertions.assertEquals(Set.of(), visitor.getClasses());
  }

  @Test
  void testAcceptDir() throws IOException {
    Path abDir = Files.createDirectories(tempDir.resolve(Path.of("a", "b")));
    writeToFile(abDir, "c.class", "class a.b.c");

    Path xyDir = Files.createDirectories(tempDir.resolve(Path.of("x", "y")));
    writeToFile(xyDir, "z.class", "class x.y.z");

    accept(tempDir);

    Assertions.assertEquals(Set.of("a.b.c", "x.y.z"), visitor.getClasses());
  }

  @Test
  void testAcceptDirWithNonClassFile() throws IOException {
    Path abDir = Files.createDirectories(tempDir.resolve(Path.of("a", "b")));
    writeToFile(abDir, "c.jpg", "jpeg a.b.c");

    accept(tempDir);

    Assertions.assertEquals(Set.of(), visitor.getClasses());
  }

  private void writeToFile(Path parent, String file, String data) throws IOException {
    Files.write(parent.resolve(file), data.getBytes(StandardCharsets.UTF_8));
  }

  private void addZipEntry(JarOutputStream out, String fileName, String content)
      throws IOException {
    out.putNextEntry(new ZipEntry(fileName));
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    out.write(bytes, 0, bytes.length);
  }
}
