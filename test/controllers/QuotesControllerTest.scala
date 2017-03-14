package controllers

import java.time.LocalDate

import com.typesafe.config.ConfigFactory
import models.CombinedQuote
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.test.Helpers
import services.{ QuotesCache, QuotesCacheManager }

import scala.concurrent.Future

class QuotesControllerTest extends PlaySpec
  with Results
  with MockitoSugar
  with BeforeAndAfterEach
  with ScalaFutures {

  val app = Helpers.fakeApplication()
  implicit val mat = app.getWrappedApplication.materializer

  implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global

  var config: Configuration = Configuration(ConfigFactory.load())
  var manager: QuotesCacheManager = null
  var cache: QuotesCache = null
  var controller: QuotesController = null

  override def beforeEach: Unit = {
    manager = mock[QuotesCacheManager]

    when(manager.cacheTicker(anyString(), any[LocalDate](), any[LocalDate]()))
      .thenReturn(Future.successful(true))

    cache = mock[QuotesCache]

    when(cache.containsQuote(anyString()))
      .thenReturn(Future.successful(true))

    when(cache.retrieveQuote(
      anyString(),
      any[Option[Int]](),
      any[Option[Int]](),
      any[Option[String]](),
      any[Option[String]]())
    )
      .thenReturn(Future.successful(Seq.empty[CombinedQuote]))

    controller = new QuotesController(config, manager, cache, executionContext)
  }

  "getQuotes" must {

    "respond with a 'try again later' message if ticker not cached" in {
      when(cache.containsQuote(anyString()))
        .thenReturn(Future.successful(false))

      val json = Json.parse(
        """
          |{
          |  "ticker": "xyz"
          |}
        """.stripMargin
      )
      val req = FakeRequest("POST", "/get-quotes").withJsonBody(json)
      val result = contentAsJson(call(controller.getQuote, req))

      val message = (result \ "error").as[String]
      message must equal(config.getString("historicalQuotes.getQuotes.noCacheMessage").get)
    }

    "respond with an 'invalid symbol' message if ticker not valid" in {
      when(cache.containsQuote(anyString()))
        .thenReturn(Future.successful(false))
      when(manager.cacheTicker(anyString(), any[LocalDate](), any[LocalDate]()))
        .thenReturn(Future.successful(false))

      val json = Json.parse(
        """
          |{
          |  "ticker": "xyz"
          |}
        """.stripMargin
      )
      val req = FakeRequest("POST", "/get-quotes").withJsonBody(json)
      val result = contentAsJson(call(controller.getQuote, req))

      val message = (result \ "error").as[String]
      message must equal(config.getString("historicalQuotes.getQuotes.noSuchSymbolMessage").get)
    }

    "respond with an 'unexpected error' message if something goes wrong" in {
      when(cache.containsQuote(anyString()))
        .thenReturn(Future.failed(new Exception("blah")))

      val json = Json.parse(
        """
          |{
          |  "ticker": "xyz"
          |}
        """.stripMargin
      )
      val req = FakeRequest("POST", "/get-quotes").withJsonBody(json)
      val result = contentAsJson(call(controller.getQuote, req))

      val message = (result \ "error").as[String]
      message must equal(config.getString("historicalQuotes.getQuotes.unexpectedError").get)
    }

    "respond with a 'json parse' error if the request body is invalid" in {
      val json = Json.parse(
        """
          |{
          |  "unknown": "xyz"
          |}
        """.stripMargin
      )
      val req = FakeRequest("POST", "/get-quotes").withJsonBody(json)
      val result = contentAsJson(call(controller.getQuote, req))

      val message = (result \ "error").as[String]
      message must startWith("Error parsing JSON")
    }

    "respond with quotes if everything is good" in {
      val quote = CombinedQuote("2011-01-01", "10", "15", false)

      when(cache.retrieveQuote(
        anyString(),
        any[Option[Int]](),
        any[Option[Int]](),
        any[Option[String]](),
        any[Option[String]]())
      )
        .thenReturn(Future.successful(Seq(quote)))

      val json = Json.parse(
        """
          |{
          |  "ticker": "xyz"
          |}
        """.stripMargin
      )
      val req = FakeRequest("POST", "/get-quotes").withJsonBody(json)
      val result = contentAsJson(call(controller.getQuote, req))

      (result \\ "date").head.as[String] must be(quote.date)
      (result \\ "open").head.as[String] must be(quote.open)
      (result \\ "close").head.as[String] must be(quote.close)
      (result \\ "is_dividend_day").head.as[Boolean] must be(quote.isDividend)
    }

  }

}
