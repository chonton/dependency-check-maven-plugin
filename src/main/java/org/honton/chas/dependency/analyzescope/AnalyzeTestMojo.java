package org.honton.chas.dependency.analyzescope;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Analyzes the test dependencies of this project and determines which are: used and declared; used
 * and undeclared; unused and declared. This goal is intended to be used in the build lifecycle,
 * thus it assumes that the <code>test-compile</code> phase has been executed.
 *
 * @see AnalyzeMainMojo
 */
@Mojo(
    name = "test",
    defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES,
    requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true)
public class AnalyzeTestMojo extends AbstractAnalyzeScopeMojo {

  @Parameter(
      defaultValue = "${project.build.testOutputDirectory}",
      required = true,
      readonly = true)
  private File testClasses;

  private Artifact testArtifact;

  private static Artifact createTestArtifact(Artifact mainArtifact) {
    return new DefaultArtifact(
        mainArtifact.getGroupId(),
        mainArtifact.getArtifactId(),
        mainArtifact.getVersion(),
        "test",
        mainArtifact.getType(),
        "test",
        mainArtifact.getArtifactHandler());
  }

  @Override
  boolean skip() {
    if (skip) {
      getLog().info("Skipping dependency-check:test execution");
      return true;
    }
    if (testClasses == null || !testClasses.exists()) {
      getLog().info("No test classes directory");
      return true;
    }

    testArtifact = createTestArtifact(project.getArtifact());
    testArtifact.setFile(testClasses);
    return false;
  }

  @Override
  Artifact workingArtifact() {
    return testArtifact;
  }

  @Override
  Collection<Artifact> impliedArtifacts() {
    Artifact mainArtifact = project.getArtifact();
    File mainClasses= mainArtifact.getFile();
    if (mainClasses == null || !mainClasses.exists()) {
      getLog().info("No main classes directory");
      return Collections.singletonList(testArtifact);
    }
    return Arrays.asList(mainArtifact, testArtifact);
  }

  @Override
  Set<String> getDeclaredScopes() {
    return Collections.singleton(Artifact.SCOPE_TEST);
  }

  @Override
  Set<String> getClasspathScopes() {
    return Set.of(
        Artifact.SCOPE_COMPILE,
        Artifact.SCOPE_PROVIDED,
        Artifact.SCOPE_SYSTEM,
        Artifact.SCOPE_TEST);
  }
}
