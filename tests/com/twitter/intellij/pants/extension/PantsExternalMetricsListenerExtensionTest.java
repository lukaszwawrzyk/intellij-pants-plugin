// Copyright 2016 Pants project contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package com.twitter.intellij.pants.extension;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.containers.ContainerUtil;
import com.twitter.intellij.pants.metrics.PantsExternalMetricsListener;
import com.twitter.intellij.pants.metrics.PantsExternalMetricsListenerManager;
import com.twitter.intellij.pants.testFramework.OSSPantsIntegrationTest;
import junit.framework.AssertionFailedError;
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestRunConfiguration;

import java.io.File;

public class PantsExternalMetricsListenerExtensionTest extends OSSPantsIntegrationTest {

  private PantsExternalMetricsListener.TestRunnerType lastRun;

  public class TestMetricsListener implements PantsExternalMetricsListener {

    @Override
    public void logIsIncrementalImport(boolean isIncremental) {

    }

    @Override
    public void logIsPantsNoopCompile(boolean isNoop) throws Throwable {

    }

    @Override
    public void logIsGUIImport(boolean isGUI) {

    }

    @Override
    public void logTestRunner(TestRunnerType runner) {
      lastRun = runner;
    }

    @Override
    public void logDurationBeforePantsCompile(long milliSeconds) throws Throwable {

    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Register `TestMetricsListener` as one of the extension points of PantsExternalMetricsListener
    Extensions.getRootArea().getExtensionPoint(PantsExternalMetricsListener.EP_NAME).registerExtension(new TestMetricsListener());
  }

  @Override
  public void tearDown() throws Exception {
    // Git reset .cache/pants dir
    cmd("git", "reset", "--hard");
    // Only the files under examples are going to be modified.
    // Hence issue `git clean -fdx` under examples, so pants does not
    // have to bootstrap again.
    File exampleDir = new File(getProjectFolder(), "examples");
    cmd(exampleDir, "git", "clean", "-fdx");
    cmd("rm", "-rf", "dist");
    super.tearDown();
  }

  public void testJUnitRunner() throws Throwable {
    doImport("testprojects/tests/java/org/pantsbuild/testproject/annotation");

    JUnitConfiguration runConfiguration = generateJUnitConfiguration(
      "testprojects_tests_java_org_pantsbuild_testproject_annotation_annotation",
      "org.pantsbuild.testproject.annotation.AnnotationTest",
      null
    );

    assertPantsCompileExecutesAndSucceeds(pantsCompileProject());
    assertSuccessfulTest(runConfiguration);
    assertEquals(PantsExternalMetricsListener.TestRunnerType.JUNIT_RUNNER, lastRun);
  }

  public void testScalaRunnerMetrics() {
    doImport("examples/tests/scala/org/pantsbuild/example/hello");
    assertPantsCompileExecutesAndSucceeds(pantsCompileProject());
    ScalaTestRunConfiguration runConfiguration = generateScalaRunConfiguration(
      "examples_tests_scala_org_pantsbuild_example_hello_welcome_welcome",
      "org.pantsbuild.example.hello.welcome.WelSpec",
      null
    );
    try {
      runWithConfiguration(runConfiguration);
    }
    catch (AssertionFailedError ignored) {

    }
    assertEquals(PantsExternalMetricsListener.TestRunnerType.SCALA_RUNNER, lastRun);
  }

  public void testJUnitRunnerError() throws Throwable {

    class ErrorMetricsListener implements PantsExternalMetricsListener {

      public boolean called = false;

      @Override
      public void logIsIncrementalImport(boolean isIncremental) throws Throwable {

      }

      @Override
      public void logIsPantsNoopCompile(boolean isNoop) throws Throwable {

      }

      @Override
      public void logIsGUIImport(boolean isGUI) throws Throwable {

      }

      @Override
      public void logTestRunner(TestRunnerType runner) throws Throwable {
        called = true;
        throw new Exception("metrics error");
      }

      @Override
      public void logDurationBeforePantsCompile(long milliSeconds) throws Throwable {

      }
    }

    ErrorMetricsListener errorListenerExtension = new ErrorMetricsListener();
    Extensions.getRootArea().getExtensionPoint(PantsExternalMetricsListener.EP_NAME).registerExtension(errorListenerExtension);

    // Make sure the exception will not cause the main thread to fail.
    PantsExternalMetricsListenerManager.getInstance().logTestRunner(PantsExternalMetricsListener.TestRunnerType.PANTS_RUNNER);
    assertTrue(String.format("%s was not called.", ErrorMetricsListener.class), errorListenerExtension.called);
  }

  public void testNoopMetrics() throws Throwable {
    class NoopMetricsListener implements PantsExternalMetricsListener {

      private boolean lastWasNoop;

      @Override
      public void logIsIncrementalImport(boolean isIncremental) throws Throwable {

      }

      @Override
      public void logIsPantsNoopCompile(boolean isNoop) throws Throwable {
        lastWasNoop = isNoop;
      }

      @Override
      public void logIsGUIImport(boolean isGUI) throws Throwable {

      }

      @Override
      public void logTestRunner(TestRunnerType runner) throws Throwable {
      }

      @Override
      public void logDurationBeforePantsCompile(long milliSeconds) throws Throwable {

      }
    }

    NoopMetricsListener listener = new NoopMetricsListener();
    Extensions.getRootArea().getExtensionPoint(PantsExternalMetricsListener.EP_NAME).registerExtension(listener);

    doImport("examples/tests/scala/org/pantsbuild/example/hello/welcome");
    // The first compile has to execute.
    assertPantsCompileExecutesAndSucceeds(pantsCompileProject());
    assertFalse("Last compile should not be noop, it was.", listener.lastWasNoop);

    // Second compile without any change should be lastWasNoop.
    assertPantsCompileNoop(pantsCompileProject());
    assertTrue("Last compile should be noop, but was not.", listener.lastWasNoop);
  }

  public void testDuration() throws Throwable {
    class DurationMetricsTestListener implements PantsExternalMetricsListener {
      long duration = -1;

      @Override
      public void logIsIncrementalImport(boolean isIncremental) throws Throwable {

      }

      @Override
      public void logIsPantsNoopCompile(boolean isNoop) throws Throwable {
      }

      @Override
      public void logIsGUIImport(boolean isGUI) throws Throwable {

      }

      @Override
      public void logTestRunner(TestRunnerType runner) throws Throwable {
      }

      @Override
      public void logDurationBeforePantsCompile(long milliSeconds) throws Throwable {
        duration = milliSeconds;
      }
    }

    DurationMetricsTestListener listener = new DurationMetricsTestListener();
    Extensions.getRootArea().getExtensionPoint(PantsExternalMetricsListener.EP_NAME).registerExtension(listener);

    doImport("examples/tests/scala/org/pantsbuild/example/hello/welcome");
    // The first compile has to execute.
    assertPantsCompileExecutesAndSucceeds(pantsCompileProject());

    for (long sleepMilliseconds : ContainerUtil.newArrayList(500, 1000)) {
      // Modify a file in project so PantsCompile will be triggered.
      modify("org.pantsbuild.example.hello.greet.Greeting");
      Thread.sleep(sleepMilliseconds);
      // Second compile with modified project should execute.
      assertPantsCompileExecutesAndSucceeds(pantsCompileProject());
      assertTrue("Recorded duration between last file edit and PantsCompile invocation should be refreshed, but it is not", listener.duration >= sleepMilliseconds);
      // Record the current duration.
      long dataPoint = listener.duration;
      // Run compile again which should be noop, and make sure the the duration is not updated.
      assertPantsCompileNoop(pantsCompileProject());
      assertEquals("Noop compile should leave recorded duration unchanged, but it is not the case", dataPoint, listener.duration);
    }
  }
}