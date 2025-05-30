package munit.internal.junitinterface

import scala.collection.mutable

import org.junit.runner.Description
import org.junit.runner.notification
import org.junit.runner.notification.RunNotifier

class MUnitRunNotifier(reporter: JUnitReporter) extends RunNotifier {
  var startedTimestamp = 0L
  val isReported: mutable.Set[Description] = mutable.Set.empty[Description]
  override def fireTestSuiteStarted(description: Description): Unit =
    reporter.reportTestSuiteStarted()
  override def fireTestStarted(description: Description): Unit = {
    startedTimestamp = System.nanoTime()
    reporter.reportTestStarted(description.getMethodName)
  }
  private def elapsedNanos(): Long = System.nanoTime() - startedTimestamp
  override def fireTestIgnored(description: Description): Unit = {
    isReported += description
    val pendingSuffixes = {
      val annotations = description.getAnnotations
      val isPending = annotations.collect { case munit.Pending => "PENDING" }
        .distinct
      val pendingComments = annotations
        .collect { case tag: munit.PendingComment => tag.value }
      isPending ++ pendingComments
    }
    reporter
      .reportTestIgnored(description.getMethodName, pendingSuffixes.mkString(" "))
  }
  override def fireTestAssumptionFailed(failure: notification.Failure): Unit = {
    isReported += failure.description
    reporter
      .reportAssumptionViolation(failure.description.getMethodName, failure.ex)
  }
  override def fireTestFailure(failure: notification.Failure): Unit = {
    val methodName = failure.description.getMethodName
    isReported += failure.description
    reporter.reportTestFailed(methodName, failure.ex, elapsedNanos())
  }
  override def fireTestFinished(description: Description): Unit = {
    val methodName = description.getMethodName
    if (!isReported(description)) reporter
      .reportTestPassed(methodName, elapsedNanos())
  }
  override def fireTestSuiteFinished(description: Description): Unit = {}
}
