// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/lgpl-3.0.en.html

package http.client

import scala.StringContext
import scala.collection.immutable.List

import org.scalatest._
import org.scalatest.Matchers._

import scalaz._, Maybe.{ Empty, Just }

class UrlSpec extends FlatSpec {
  "Url" should "parse hosts" in {
    url"http://fommil.com" should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Empty()) =>
    }
  }

  it should "parse usernames and passwords" in {
    url"https://fommil@fommil.com" should matchPattern {
      case Url("https",
               Just("fommil"),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Empty()) =>
    }

    url"http://fommil:wobble@fommil.com" should matchPattern {
      case Url("http",
               Just("fommil:wobble"),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Empty()) =>
    }

    url"http://:wobble@fommil.com" should matchPattern {
      case Url("http",
               Just(":wobble"),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Empty()) =>
    }

    url"http://:@fommil.com" should matchPattern {
      case Url("http",
               Just(":"),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Empty()) =>
    }

    url"http://::@fommil.com" should matchPattern {
      case Url("http",
               Just("::"),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Empty()) =>
    }
  }

  it should "parse ports" in {
    url"http://fommil.com:80" should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Just(80),
               Empty(),
               Empty(),
               Empty()) =>
    }
  }

  it should "parse paths" in {

    url"http://fommil.com/" should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Empty(),
               Just("/"),
               Empty(),
               Empty()) =>
    }

    url"http://fommil.com//" should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Empty(),
               Just("//"),
               Empty(),
               Empty()) =>
    }

    url"http://fommil.com/wibble/" should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Empty(),
               Just("/wibble/"),
               Empty(),
               Empty()) =>
    }

    url"http://example.com/引き割り.html" should matchPattern {
      case Url("http",
               Empty(),
               "example.com",
               Empty(),
               Just("/引き割り.html"),
               Empty(),
               Empty()) =>
    }
  }

  it should "parse query" in {
    url"http://fommil.com?foo=bar&baz=gaz" should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Empty(),
               Empty(),
               Just("foo=bar&baz=gaz"),
               Empty()) =>
    }
  }

  it should "parse anchor" in {
    url"http://fommil.com#wibble" should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Just("wibble")) =>
    }
  }

  it should "correctly encode to ascii" in {
    // from https://en.wikipedia.org/wiki/URL
    // seems the JDK doesn't work correctly here
    // url"http://例子.卷筒纸".encoded shouldBe "http://xn--fsqu00a.xn--3lr804guic/"

    url"http://example.com/引き割り.html".encoded
      .shouldBe("http://example.com/%E5%BC%95%E3%81%8D%E5%89%B2%E3%82%8A.html")
  }

  it should "allow changing the query" in {
    url"http://fommil.com?wibble=wobble"
      .withQuery(
        Url.Query(
          List(
            ("blah", "bloo"),
            (" meh ", "#")
          )
        )
      )
      .encoded
      .shouldBe("http://fommil.com?blah=bloo&%20meh%20=%23")
  }

  it should "not allow invalid URLs to compile" in {
    assertDoesNotCompile(
      """url"blurg""""
    )
  }

  it should "possibly be replaced by refined" in {
    import java.lang.String
    import eu.timepit.refined.api.Refined
    import eu.timepit.refined.auto._
    import eu.timepit.refined.string._

    val uri1: String Refined Uri = "http://fommil.com"
    uri1.value.shouldBe("http://fommil.com")

    // I'd like this to fail... so need to create custom refinement
    val _: String Refined Uri = "fommil.com"
  }

}
