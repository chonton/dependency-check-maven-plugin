package org.honton.chas.analyzer.spi;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.Getter;
import org.honton.chas.analyzer.asm.visitors.DependencyClassFileVisitor;

/** Factory for ClassFileVisitor */
public class ClassFileVisitorFactory implements Function<String, ClassFileVisitor> {
  @Getter
  private final Map<String, Set<String>> dependencies = new HashMap<>();

  /**
   * Create a ClassFileVisitor for the given className
   *
   * @param className the name of the class for which dependencies are being collected
   * @return the ClassFileVisitor acting as a dependency collector
   */
  @Override
  public ClassFileVisitor apply(String className) {
    int dollarIdx = className.indexOf('$');
    String stubName = dollarIdx < 0 ? className : className.substring(0, dollarIdx);
    return new DependencyClassFileVisitor(
        dependencies.computeIfAbsent(stubName, cn -> new HashSet<>()));
  }
}
