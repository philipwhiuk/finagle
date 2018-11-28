package com.twitter.finagle.http

import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class DefaultHeaderMapTest extends AbstractHeaderMapTest with GeneratorDrivenPropertyChecks {

  final def newHeaderMap(headers: (String, String)*): HeaderMap = DefaultHeaderMap(headers: _*)

  def genNonEmptyString: Gen[String] =
    Gen.nonEmptyListOf(Gen.choose('a', 'z')).map(s => new String(s.toArray))

  def genValidFoldedHeader: Gen[(String, String)] =
    for {
      k <- genNonEmptyString
      v1 <- genNonEmptyString
      folds <- Gen.nonEmptyListOf(Gen.oneOf("\r\n ", "\r\n\t", "\n ", "\n\t"))
      v2 <- genNonEmptyString
    } yield (k, v1 + folds.mkString(v2))

  def genValidHeader: Gen[(String, String)] =
    for {
      k <- genNonEmptyString
      v <- genNonEmptyString
    } yield (k, v)

  def genInvalidHeaderName: Gen[(String, String)] =
    for {
      (k, v) <- genValidHeader
      c <- Gen.oneOf(Seq[Char]('\t', '\n', '\f', '\r', ' ', ',', ':', ';', '=', 0x0b))
    } yield (k + c, v)

  def genNonAsciiHeaderName: Gen[(String, String)] =
    for {
      (k, v) <- genValidHeader
      c <- Gen.choose[Char](128, Char.MaxValue)
    } yield (k + c, v)

  def genInvalidHeaderValue: Gen[(String, String)] =
    for {
      (k, v) <- genValidHeader
      c <- Gen.oneOf(Seq[Char]('\f', 0x0b))
    } yield (k, v + c)

  def genInvalidClrfHeaderValue: Gen[(String, String)] =
    for {
      (k, v) <- genValidHeader
      c <- Gen.oneOf("\rx", "\nx", "\r", "\n")
    } yield (k, v + c)

  test("apply()") {
    assert(DefaultHeaderMap().isEmpty)
  }

  test("validates header names & values (success)") {
    forAll(genValidHeader) {
      case (k, v) =>
        assert(DefaultHeaderMap(k -> v).get(k).contains(v))
    }
  }

  test("validates header names & values with obs-folds (success)") {
    forAll(genValidFoldedHeader) {
      case (k, v) =>
        val value = DefaultHeaderMap(k -> v).apply(k)
        assert(value == DefaultHeaderMap.ObsFoldRegex.replaceAllIn(v, " "))
        assert(v.contains("\n"))
        assert(!value.contains("\n"))
    }
  }

  test("validates header names (failure)") {
    forAll(genInvalidHeaderName) { h =>
      val e = intercept[IllegalArgumentException](DefaultHeaderMap(h))
      assert(e.getMessage.contains("prohibited characters"))
    }

    forAll(genNonAsciiHeaderName) { h =>
      val e = intercept[IllegalArgumentException](DefaultHeaderMap(h))
      assert(e.getMessage.contains("non-ASCII characters"))
    }
  }

  test("validates header values (failure)") {
    forAll(genInvalidHeaderValue) { h =>
      val e = intercept[IllegalArgumentException](DefaultHeaderMap(h))
      assert(e.getMessage.contains("prohibited character"))
    }

    forAll(genInvalidClrfHeaderValue) { h =>
      intercept[IllegalArgumentException](DefaultHeaderMap(h))
    }
  }

  test("does not validate header names or values with addUnsafe") {
    val headerMap = newHeaderMap()

    forAll(genInvalidHeaderName) { h =>
      headerMap.addUnsafe(h._1, h._2)
    }

    forAll(genInvalidHeaderValue) { h =>
      headerMap.addUnsafe(h._1, h._2)
    }
  }

  test("does not validate header names or values with setUnsafe") {
    val headerMap = newHeaderMap()

    forAll(genInvalidHeaderName) { h =>
      headerMap.setUnsafe(h._1, h._2)
    }

    forAll(genInvalidHeaderValue) { h =>
      headerMap.setUnsafe(h._1, h._2)
    }
  }

  test("getOrNull acts as get().orNull") {
    forAll(genValidHeader) {
      case (k, v) =>
        val h = DefaultHeaderMap(k -> v)
        assert(h.getOrNull(k) == h.get(k).orNull)
    }

    val empty = DefaultHeaderMap()
    assert(empty.getOrNull("foo") == empty.get("foo").orNull)
  }
}
