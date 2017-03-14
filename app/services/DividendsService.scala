package services

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import models.{ Dividend, Quote }
import play.api.libs.json.{ JsError, JsSuccess, Json }
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

trait DividendsService {

  def getDividends(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Seq[Dividend]]

}

class YahooDividends @Inject()(ws: WSClient, implicit val ec: ExecutionContext) extends DividendsService {

  val url = "https://query.yahooapis.com/v1/public/yql"
  val collection = "yahoo.finance.dividendhistory"

  implicit val yahooDividendReads = Json.reads[YahooDividend]

  override def getDividends(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Seq[Dividend]] = {
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

    val yahooDividends = request map { r =>
      val v = (r.json \ "query" \ "results" \ "quote").validate[Seq[YahooDividend]]

      val x = v match {
        case s: JsSuccess[Seq[YahooDividend]] => s.get
        case e: JsError => throw new Exception(s"parse exception: ${JsError.toJson(e).toString}")
      }

      x
    }

    yahooDividends map (_ map toDividend)
  }

  private def toDividend(yd: YahooDividend): Dividend = {
    Dividend(
      ticker = yd.Symbol,
      date = yd.Date,
      dividend = yd.Dividends
    )
  }

}

case class YahooDividend(
  Symbol: String,
  Date: String,
  Dividends: String
)