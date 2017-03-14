package services

import java.time.LocalDate
import javax.inject.Inject

import models.Quote
import play.api.Configuration
import play.api.libs.json.{ JsError, JsSuccess, Json }
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

trait QuotesService {

  def tickerExists(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Boolean]

  def getQuotes(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Seq[Quote]]

}

class YahooQuotesService @Inject()(
  configuration: Configuration,
  val ws: WSClient,
  implicit val ec: ExecutionContext
) extends QuotesService with YahooWebService {

  override val url = configuration.getString("historicalQuotes.yahoo.url").get
  override val collection = configuration.getString("historicalQuotes.yahoo.quotes.collection").get
  override val store = configuration.getString("historicalQuotes.yahoo.env").get

  implicit val yahooQuoteReads = Json.reads[YahooQuote]

  override def tickerExists(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Boolean] = {
    val statement =
      s"""
         |select * from $collection
         |where symbol = '$ticker' and startDate = '${format(startDate)}' and endDate = '${format(endDate)}'
         |limit 1
       """.stripMargin

    makeRequest(statement) map { r =>
      val count = (r.json \ "query" \ "count").as[Int]
      count > 0
    }
  }

  override def getQuotes(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Seq[Quote]] = {
    val select =
      s"""
         |select * from $collection
         |where symbol = '$ticker' and startDate = '${format(startDate)}' and endDate = '${format(endDate)}'
       """.stripMargin

    val yahooQuotes = makeRequest(select) map { r =>
      val v = (r.json \ "query" \ "results" \ "quote").validate[Seq[YahooQuote]]

      val x = v match {
        case s: JsSuccess[Seq[YahooQuote]] => s.get
        case e: JsError => throw new Exception(s"parse exception: ${JsError.toJson(e).toString}")
      }

      x
    }

    yahooQuotes map (_ map toQuote)
  }

  private def toQuote(yahooQuote: YahooQuote): Quote = {
    Quote(
      date = yahooQuote.Date,
      open = yahooQuote.Open,
      close = yahooQuote.Close
    )
  }

}

private[services] case class YahooQuote(
  Symbol: String,
  Date: String,
  Open: String,
  High: String,
  Low: String,
  Close: String,
  Volume: String,
  Adj_Close: String
)