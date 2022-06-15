package org.honton.chas.analyzer.asm.visitors;

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
import java.io.InputStream;
import java.util.Set;
import org.honton.chas.analyzer.asm.visitors.testcases.ArrayCases;
import org.honton.chas.analyzer.asm.visitors.testcases.InnerClassCase;
import org.honton.chas.analyzer.asm.visitors.testcases.MethodHandleCases;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ResultCollectorTest {
  Set<String> getDependencies(Class<?> inspectClass) throws IOException {
    String className = inspectClass.getName();
    String path = '/' + className.replace('.', '/') + ".class";
    DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
    try (InputStream is = inspectClass.getResourceAsStream(path)) {
      visitor.visitClass(className, is);
    }
    return visitor.getDependencies();
  }

  @Test
  void testArrayCases() throws IOException {
    Set<String> dependencies = getDependencies(ArrayCases.class);
    Assertions.assertFalse(dependencies.contains("[I"));
    Assertions.assertTrue(dependencies.contains("java.lang.annotation.Annotation"));
    Assertions.assertTrue(dependencies.contains("java.lang.reflect.Constructor"));
    dependencies.forEach(d -> Assertions.assertFalse(d.startsWith("[")));
  }

  @Test
  void testNoMethodHandle() throws IOException {
    Set<String> dependencies = getDependencies(MethodHandleCases.class);
    dependencies.forEach(d -> Assertions.assertFalse(d.startsWith("(")));
  }

  @Test
  void testInnerClassAsContainer() throws IOException {
    Set<String> dependencies = getDependencies(InnerClassCase.class);
    dependencies.forEach(d -> Assertions.assertTrue(d.indexOf('$')<0));
    Assertions.assertTrue(dependencies.contains("java.lang.System"));
  }
}
