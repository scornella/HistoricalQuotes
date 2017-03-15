package services

import java.time.LocalDate
import javax.inject.Inject

import models.Dividend
import play.api.Configuration
import play.api.libs.json.{ JsError, JsSuccess, Json }
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

trait DividendsService {

  def getDividends(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Seq[Dividend]]

}

class YahooDividendsService @Inject()(
  configuration: Configuration,
  val ws: WSClient,
  implicit val ec: ExecutionContext
) extends DividendsService with YahooWebService {

  //I prefer the option-less interface of typesafe config
  private val config = configuration.underlying

  override val url = config.getString("historicalQuotes.yahoo.url")
  override val collection = config.getString("historicalQuotes.yahoo.dividends.collection")
  override val store = config.getString("historicalQuotes.yahoo.env")

  implicit val yahooDividendReads = Json.reads[YahooDividend]

  override def getDividends(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Seq[Dividend]] = {
    val select =
      s"""
         |select * from $collection
         |where symbol = '$ticker' and startDate = '${format(startDate)}' and endDate = '${format(endDate)}'
       """.stripMargin

    val yahooDividends = makeRequest(select) map { r =>
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

private[services] case class YahooDividend(
  Symbol: String,
  Date: String,
  Dividends: String
)