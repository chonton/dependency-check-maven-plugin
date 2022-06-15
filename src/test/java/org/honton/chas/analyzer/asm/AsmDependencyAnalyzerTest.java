package org.honton.chas.analyzer.asm;

/*
 * Licensed to the Apache Software Foundation (ASF) under on
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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugin.logging.Log;
import org.honton.chas.analyzer.api.DependencyAnalyzer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class AsmDependencyAnalyzerTest {
  private final DependencyAnalyzer analyzer = new AsmDependencyAnalyzer();

  @Mock Log log;

  @Test
  void test() throws URISyntaxException {
    URL jarUrl = getClass().getResource("/org/objectweb/asm/ClassReader.class");
    Assertions.assertNotNull(jarUrl);
    String schemeSpecificPart = jarUrl.toURI().getSchemeSpecificPart();
    String jarLocation = schemeSpecificPart.substring(0, schemeSpecificPart.indexOf('!'));
    Path filePath = Paths.get(new URI(jarLocation));
    Map<String, Set<String>> result = analyzer.analyze(filePath, log);
    Assertions.assertFalse(result.isEmpty());
  }
}
