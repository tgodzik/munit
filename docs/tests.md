---
id: tests
title: Declaring tests
---

MUnit provides several ways to declare different kinds of tests.

## Declare basic test

Use `test()` to declare a basic test case that passes as long as the test body
does not crash with an exception.

```scala mdoc:invisible
object Tests extends munit.FunSuite
import Tests._
```

```scala mdoc
test("basic") {

}
```

## Declare async test

Async tests are declared the same way as basic tests, except their test bodies
return a value that can be converted into `Future[T]`.

```scala mdoc:silent
import scala.concurrent.Future
implicit val ec = scala.concurrent.ExecutionContext.global
test("async") {
  Future {
    println("Hello Internet!")
  }
}
```

MUnit has special handling for `scala.concurrent.Future[T]` since it is
available in the standard library. Override `munitValueTransforms` to add custom
handling for other asynchronous types.

For example, imagine that you have a `LazyFuture[T]` data type that is a lazy
future.

```scala mdoc
import scala.concurrent.ExecutionContext
case class LazyFuture[+T](run: () => Future[T])
object LazyFuture {
  def apply[T](thunk: => T)(implicit ec: ExecutionContext): LazyFuture[T] =
    LazyFuture(() => Future(thunk))
}

test("buggy-task") {
  LazyFuture {
    Thread.sleep(10)
    // WARNING: test will pass because `LazyFuture.run()` was never called
    throw new RuntimeException("BOOM!")
  }
}
```

The `LazyFuture` class doesn't evaluate the body until the `run()` method is
invoked. Override `munitValueTransforms` to make sure that `LazyFuture.run()`
gets called.

```scala mdoc
import scala.concurrent.ExecutionContext.Implicits.global
class TaskSuite extends munit.FunSuite {
  override def munitValueTransforms = super.munitValueTransforms ++ List(
    new ValueTransform("LazyFuture", {
      case LazyFuture(run) => run()
    })
  )
  implicit val ec = ExecutionContext.global
  test("ok-task") {
    LazyFuture {
      Thread.sleep(5000)
      // Test will fail because `LazyFuture.run()` is automatically called
      throw new RuntimeException("BOOM!")
    }
  }
}
```

## Customize test timeouts

> This feature is only available for the JVM and Scala.js. It's not available
> for Scala Native.

```scala mdoc:passthrough
println(s"The default timeout for async tests is $munitTimeout.")
println(s"Tests that exceed this timeout fail with an error message.")
```

```
==> X munit.TimeoutSuite.slow  0.106s java.util.concurrent.TimeoutException: test timed out after 100 milliseconds
```

Override `munitTimeout` to customize the timeout for how long tests should
await.

```scala mdoc
import scala.concurrent.duration.Duration
class CustomTimeoutSuite extends munit.FunSuite {
  // await one second instead of default
  override val munitTimeout = Duration(1, "s")
  test("slow-async") {
    Future {
      Thread.sleep(5000)
      // Test times out before `println()` is evaluated.
      println("pass")
    }
  }
}
```

> Note that `munitTimeout` is only respected for async tests in the stable
> version of MUnit (v0.x series). The setting is ignored by normal
> non-async tests. However, starting with MUnit v1.0 (latest milestone release:
> @VERSION@), the timeout applies to all tests including non-async tests.

## Customize value printers

MUnit uses its own `Printer`s to convert any value into a diff-ready string representation.
The resulting string is the actual value being compared, and is also used to generate the clues in case of a failure.

The default printing behaviour can be overridden for a given type by defining a custom `Printer` and overriding `printer`.

Override `printer` to customize the comparison of two values :

```scala mdoc
import java.time.Instant
import munit.FunSuite
import munit.diff.Printer

class CompareDatesOnlyTest extends FunSuite {
  override val printer = Printer.apply {
    // take only the date part of the Instant
    case instant: Instant => instant.toString.takeWhile(_ != 'T')
  }

  test("dates only") {
    val expected = Instant.parse("2022-02-15T18:35:24.00Z")
    val actual = Instant.parse("2022-02-15T18:36:01.00Z")
    assertEquals(actual, expected) // true
  }
}
```

or to customize the printed clue in case of a failure :

```scala mdoc
import munit.FunSuite
import munit.diff.Printer

class CustomListOfCharPrinterTest extends FunSuite {
  override val printer = Printer.apply {
    case l: List[Char] => l.mkString
  }

  test("lists of chars") {
    val expected = List('h', 'e', 'l', 'l', 'o')
    val actual = List('h', 'e', 'l', 'l', '0')
    assertEquals(actual, expected)
  }
}
```

will yield

```
=> Obtained
hell0
=> Diff (- obtained, + expected)
-hell0
+hello
```

instead of the default

```
...
=> Obtained
List(
  'h',
  'e',
  'l',
  'l',
  '0'
)
=> Diff (- obtained, + expected)
   'l',
-  '0'
+  'o'
...
```

## Customizing the diff output

When a test fails, Munit shows a rich unified diff, to easily spot the difference
between the test result and the expected value.

However, identifying where the reported discrepancy actually is can sometimes be
difficult, especially with values found in multiple locations.

To customize what's being displayed, one can define an `implicit` instance of the
`munit.diff.DiffOptions` class which contains the following methods:
- `withContextSize(Int)`: number of lines of context to show around the
  modified lines (default: 1)
- `withShowLines(Boolean)`: shows the usual unified diff patch preamble
  `@@ -aa,bb +cc,dd @@` (default: false)
- `withObtainedAsStripMargin(Boolean)`: displays the value obtained as
  triple-quoted string with margins (default: false)

```scala mdoc
import munit.FunSuite

class CustomContextSizeTest extends FunSuite {
  private implicit val diffOptions = munit.diff.DiffOptions.withContextSize(10)

  test("contextSize") {
    val a = List("a", "a", "a", "a", "a", "a", "a", "a", "a")
    val b = List("a", "a", "a", "a", "b", "a", "a", "a", "a")
    assertEquals(a,b)
  }
}
```

will include

```
=> Diff (- expected, + obtained)
 List(
   "a",
   "a",
   "a",
   "a",
-  "b",
   "a",
   "a",
   "a",
+  "a",
   "a"
 )
```

while the default output is more compact:

```
=> Diff (- expected, + obtained)
   "a",
-  "b",
   "a",
   "a",
+  "a",
   "a"
```

## Run tests in parallel

MUnit does not support running individual test cases in parallel. However, sbt
automatically parallelizes the execution of multiple test suites. To disable
parallel test suite execution in sbt, add the following setting to `build.sbt`.

```sh
Test / parallelExecution := false
```

In case you do not run your tests in parallel, you can also disable buffered
logging, which is on by default to prevent test results of multiple suites from
appearing interleaved. Switching buffering off would give you immediate feedback
on the console while a suite is running.

```sh
Test / testOptions += Tests.Argument(TestFrameworks.MUnit, "-b")
```

To learn more about sbt test execution, see
<https://www.scala-sbt.org/1.x/docs/Testing.html>.

## Declare tests inside a helper function

Avoid duplication between test cases by extracting the shared parts into a
reusable method.

```scala mdoc
def check[T](
  name: String,
  original: List[T],
  expected: Option[T]
)(implicit loc: munit.Location): Unit = {
  test(name) {
    val obtained = original.headOption
    assertEquals(obtained, expected)
  }
}

check("basic", List(1, 2), Some(1))
check("empty", List(), Some(1))
check("null", List(null, 2), Some(null))
```

When declaring tests in a helper function, it's useful to pass around an
`implicit loc: munit.Location` parameter in order to show relevant source
locations when a test fails.

![Screen shot of console output with implicit Location parameter](https://i.imgur.com/v7Vv5Rk.png)

**Screenshot above**: test failure with implicit `Location` parameter, observe
that the highlighted line points to the failing test case.

![Screen shot of console output without implicit Location parameter](https://i.imgur.com/yXpA9dp.png)

**Screenshot above** test failure without implicit `Location` parameter, observe
that the highlighted line points to `assertEquals` line that is reused in all
test cases.

It's good practice to avoid slow operations when using test helpers. For
example, use by-name variables if the arguments to the helper method do stateful
stuff that can make the test fail.

```scala mdoc
// OK: `bytes` parameter is by-name so `readAllBytes` is evaluated in test body.
def checkByName(name: String, bytes: => Array[Byte]): Unit =
  test(name) { /* use bytes */ }
// Not OK: `bytes` parameter is eager so `readAllBytes` is evaluated in class constructor.
def checkEager(name: String, bytes: Array[Byte]): Unit =
  test(name) { /* use bytes */ }

import java.nio.file.{Files, Paths}
checkByName("file", Files.readAllBytes(Paths.get("build.sbt")))
checkEager("file", Files.readAllBytes(Paths.get("build.sbt")))
```

## Declare test that should always fail

Use `.fail` to mark a test case that is expected to fail.

```scala
  test("issue-456".fail) {
    // Reproduce reported bug
  }
```

A `.fail` test only succeeds if the test body fails. If the test body succeeds,
the test fails. This feature is helpful if you want to for example reproduce a
bug report but don't have a solution to fix the issue yet.

## Customize evaluation of tests with tags

Override `munitTestTransforms()` to extend the default behavior for how test
bodies are evaluated. For example, use this feature to implement a `Rerun(N)`
modifier to evaluate the body multiple times.

```scala mdoc
case class Rerun(count: Int) extends munit.Tag("Rerun")
class MyRerunSuite extends munit.FunSuite {
  override def munitTestTransforms = super.munitTestTransforms ++ List(
    new TestTransform("Rerun", { test =>
      val rerunCount = test.tags
        .collectFirst { case Rerun(n) => n }
        .getOrElse(1)
      if (rerunCount == 1) test
      else {
        test.withBody(() => {
          Future.sequence(1.to(rerunCount).map(_ => test.body()).toList)
        })
      }
    })
  )
  test("files".tag(Rerun(3))) {
    println("Hello") // will run 3 times
  }
  test("files") {
    // will run once, like normal
  }
}
```

The `munitTestTransforms()` method is similar to `munitValueTransforms()` but is
different in that you also have access information about the test in
`TestOptions` such as tags.

## Customize test name based on a dynamic condition

Override `munitNewTest` to customize how `Test` values are constructed. For
example, use this feature to add a custom suffix to test names based on dynamic
condition.

```scala mdoc
class ScalaVersionSuite extends munit.FunSuite {
  val scalaVersion = scala.util.Properties.versionNumberString
  override def munitTestTransforms = super.munitTestTransforms ++ List(
    new TestTransform("append Scala version", { test =>
      test.withName(test.name + "-" + scalaVersion)
    })
  )
  test("foo") {
    assert(!scalaVersion.startsWith("2.11"))
  }
}
```

When running `sbt +test` to test multiple Scala versions, the Scala version will
now be included in the test name making it quickly skim through the logs and
what Scala version caused the tests to fail.

```scala
==> X munit.ScalaVersionFrameworkSuite.foo-2.11.2
/path/to/ScalaVersionSuite.scala:11 assertion failed
10:  test("foo") {
11:    assert(!scalaVersion.startsWith("2.11"))
12:  }
    at munit.Assertions.fail(Assertions.scala:121)
+ munit.ScalaVersionFrameworkSuite.foo-2.12.10
+ munit.ScalaVersionFrameworkSuite.foo-2.13.1
```

## Tag pending tests

Use `.pending` to annotate a work-in-progress test case with known-incomplete coverage.
Any assertions in the pending case must pass, but will be reported as Ignored instead of Success.
Any failures will be reported as Failures (this differs from `.ignore` which skips the test case entirely).
This tag is useful for documenting:

- **Empty placeholders** that lack any assertions (unless tagged pending, these are reported as success).
- **Incomplete placeholders** with too few assertions (unless tagged pending, these are reported as success).
- **Accurate placeholders** whose stable assertions must pass (regressions are not ignored).
- **Searchability** of your codebase for known-incomplete test cases.
- **Cross-references** between your codebase and issue trackers.

You can (optionally) include a comment for your pending test case, such as a job ticket ID:

```scala
  // Empty placeholder, without logged comments:
  test("time travel".pending) {
    // Test case to be written yesterday
  }

  // Empty placeholder, with logged comments:
  test("time travel".pending("requirements from product owner")) {
    // Is this funded yet??
  }

  // Empty placeholder, tracked for action:
  test("time travel".pending("INTERN-101")) {
    // Test case to be written by an intern
  }

  // Incomplete (WIP) placeholder, tracked for action:
  test("time travel".pending("QA-404")) {
    assert(LocalDate.now.isAfter(yesterday))
    // QA team to provide specific examples for regression-test coverage
  }
```

If you want to mark a failed regression test as pending-until-fixed,
you combine `.ignore` before or after `.pending`, for example:

```scala
  test("this test worked yesterday".ignore.pending("platform investigation")) {
    assert(LocalDate.now.equals(yesterday))
  }
```

This allows pending comments, reasons, or cross-references to be logged for ignored tests.

## Tag flaky tests

Use `.flaky` to mark a test case that has a tendency to non-deterministically
fail for known or unknown reasons.

```scala
  test("requests".flaky) {
    // I/O heavy tests that sometimes fail
  }
```

By default, flaky tests fail like basic tests unless the `MUNIT_FLAKY_OK`
environment variable is set to `true`. Override `munitFlakyOK()` to customize
when it's OK for flaky tests to fail.

In practice, flaky tests have a tendency to creep into your codebase as the
complexity of your application grows. Flaky tests reduce developer's trust in
your codebase and negatively impacts the productivity of your team so it's
important that you have a strategy for dealing with flaky test failures when
they surface.

One possible strategy for dealing with flaky test failures is to mark a flaky
test with `.flaky` to keep the test case running but not fail the build when the
test case fails. MUnit registers flaky test failures as JUnit "assumption
violated" failures. In sbt, flaky test failures are marked as "skipped". Then
use historical
[JUnit XML reports](https://www.scala-sbt.org/1.x/docs/Testing.html#Test+Reports)
to keep track of how frequently flaky tests are failing and to get a better
understanding of when and why they are failing.

## Run logic before and after tests

See the [fixtures guide](fixtures.html) for instructions for running custom
logic before and after tests.

## Share configuration between test suites

Declare an abstract `BaseSuite` to share configuration between all test suites
in your project.

```scala mdoc
abstract class BaseSuite extends munit.FunSuite {
  override val munitTimeout = Duration(1, "min")
  override def munitTestTransforms = super.munitTestTransforms ++ List(???)
  // ...
}
class MyFirstSuite extends BaseSuite { /* ... */ }
class MySecondSuite extends BaseSuite { /* ... */ }
```
