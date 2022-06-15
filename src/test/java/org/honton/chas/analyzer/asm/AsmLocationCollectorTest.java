package org.honton.chas.analyzer.asm;

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
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import org.apache.maven.plugin.logging.Log;
import org.honton.chas.analyzer.api.LocationCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AsmLocationCollectorTest {
  @Mock Log log;
  private Path path;

  @BeforeEach
  void setUp() throws IOException {
    path = Files.createTempFile("test", ".jar");
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path))) {
      addZipEntry(out, "a/b/c.class", "class a.b.c");
      addZipEntry(out, "x/y/z.class", "class x.y.z");
    }
  }

  @AfterEach
  void cleanup() throws IOException {
    if (path != null) {
      Files.deleteIfExists(path);
    }
  }

  @Test
  void testAnalyzeWithJar() {
    Set<String> expectedClasses = new HashSet<>();
    expectedClasses.add("a.b.c");
    expectedClasses.add("x.y.z");

    AsmLocationCollector analyzer = new AsmLocationCollector();
    Set<String> actualClasses = analyzer.list(path, log);

    Assertions.assertEquals(actualClasses, expectedClasses);
  }

  @Test
  void testAnalyzeBadJar() throws IOException {
    // to reproduce MDEP-143
    // corrupt the jar file by altering its contents
    byte[] ba = Files.readAllBytes(path);
    ba[50] = 1;
    Files.write(path, ba);
    LocationCollector analyzer = new AsmLocationCollector();
    analyzer.list(path, log);

    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(log).warn(messageCaptor.capture());
    Assertions.assertTrue(messageCaptor.getValue().startsWith("Could not list "));
  }

  private void addZipEntry(JarOutputStream out, String fileName, String content)
      throws IOException {
    out.putNextEntry(new ZipEntry(fileName));
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    out.write(bytes, 0, bytes.length);
  }
}
