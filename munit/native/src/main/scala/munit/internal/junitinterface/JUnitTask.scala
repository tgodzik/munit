/*
 * Adapted from https://github.com/scala-js/scala-js, see NOTICE.md.
 */

package munit.internal.junitinterface

import munit.internal.PlatformCompat
import org.junit.runner.notification.RunNotifier
import sbt.testing._
import scala.concurrent.ExecutionContext.Implicits.global

/* Implementation note: In JUnitTask we use Future[Try[Unit]] instead of simply
 * Future[Unit]. This is to prevent Scala's Future implementation to box/wrap
 * fatal errors (most importantly AssertionError) in ExecutionExceptions. We
 * need to prevent the wrapping in order to hide the fact that we use async
 * under the hood and stay consistent with JVM JUnit.
 */
final class JUnitTask(
    _taskDef: TaskDef,
    runSettings: RunSettings,
    classLoader: ClassLoader
) extends Task {

  override def taskDef(): TaskDef = _taskDef
  override def tags(): Array[String] = Array.empty

  def execute(
      eventHandler: EventHandler,
      loggers: Array[Logger]
  ): Array[Task] = {
    PlatformCompat.newRunner(taskDef(), classLoader) match {
      case None =>
      case Some(runner) =>
        runner.filter(runSettings.tags)
        val reporter =
          new JUnitReporter(eventHandler, loggers, runSettings, taskDef())
        val notifier: RunNotifier = new MUnitRunNotifier(reporter)
        runner.run(notifier)
    }
    Array()
  }

}
