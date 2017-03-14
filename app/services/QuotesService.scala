package services

import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import models.Quote
import play.api.data.format
import play.api.libs.json.{ JsError, JsSuccess, Json }
import play.api.libs.ws.{ WSClient, WSRequest }

import scala.concurrent.{ ExecutionContext, Future }

trait QuotesService {

  def tickerExists(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Boolean]

  def getQuotes(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Seq[Quote]]

}

class YahooQuotes @Inject()(ws: WSClient, implicit val ec: ExecutionContext) extends QuotesService {

  val url = "https://query.yahooapis.com/v1/public/yql"
  val collection = "yahoo.finance.historicaldata"

  implicit val yahooQuoteReads = Json.reads[YahooQuote]

  override def tickerExists(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Boolean] = {
    val fst = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val fed = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

    val statement = s"select * from $collection where symbol = '$ticker' and startDate = '$fst' and endDate = '$fed' limit 1"

    val queryString = Seq(
      "q" -> statement,
      "env" -> "store://datatables.org/alltableswithkeys",
      "format" -> "json"
    )

    val request = ws.url(url)
      .withHeaders("Accept" -> "application/json")
      .withQueryString(queryString: _*)
      .get()

    request map { r =>
      val count = (r.json \ "query" \ "count").as[Int]
      count > 0
    }
  }

  override def getQuotes(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Seq[Quote]] = {
    val fst = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val fed = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

    val select = s"select * from $collection " +
      s"where symbol = '$ticker' " +
      s"and startDate = '$fst' " +
      s"and endDate = '$fed'"

    val queryString = Seq(
      "q" -> select,
      "env" -> "store://datatables.org/alltableswithkeys",
      "format" -> "json"
    )

    val request = ws.url(url)
      .withHeaders("Accept" -> "application/json")
      .withQueryString(queryString: _*)
      .get()

    val yahooQuotes = request map { r =>
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

case class YahooQuote(
  Symbol: String,
  Date: String,
  Open: String,
  High: String,
  Low: String,
  Close: String,
  Volume: String,
  Adj_Close: String
)