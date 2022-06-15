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

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugin.logging.Log;

/**
 * Gets the set of classes referenced by a library given either a jar file or an exploded
 * directory.
 */
public interface DependencyAnalyzer {

  /**
   * Analyze a set of classes at a location. Find all class names that are referenced by the classes
   * at location.
   *
   * @param location the Jar or directory to analyze
   * @param log The logger for any processing messages
   * @return A map of classes available in the File to the set of classes required by that class
   */
  Map<String, Set<String>> analyze(Path location, Log log);
}
