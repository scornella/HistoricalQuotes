package controllers

import java.time.LocalDate
import javax.inject.Inject

import models.CombinedQuote
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import play.api.libs.functional.syntax._
import services.{ QuotesCache, QuotesCacheManager }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.matching.Regex

class QuotesController @Inject()(qs: QuotesCacheManager, qc: QuotesCache, implicit val ec: ExecutionContext) extends Controller {
  val logger: Logger = Logger(this.getClass)

  val startDate = LocalDate.parse("2011-01-01")
  val endDate = LocalDate.parse("2011-12-31")

  val DEFAULT_SORT_FIELD = "date"
  val DEFAULT_SORT_DIRECTION = "asc"

  val COME_BACK_LATER = "Sorry, that information is not currently available, please try again later."
  val NO_SUCH_TICKER = "Sorry, but it seems that the ticker symbol specified does not exist."

  def getQuote = Action.async(BodyParsers.parse.json) { implicit request =>
    import ResponseImplicits._
    import RequestImplicits._

    val quoteRequest = request.body.validate[QuoteRequest]

    quoteRequest match {
      case s: JsSuccess[QuoteRequest] =>

        val qr = s.get

        val hasQuoteF = qc.containsQuote(qr.ticker)

        hasQuoteF flatMap { hasQuote =>
          if (hasQuote) {
            logger.debug(s"Quotes for ${qr.ticker} are already cached")
            val (sortField, sortDirection) = sortFromRequest(qr)

            qc.retrieveQuote(qr.ticker, qr.limit, qr.skip, Some(sortField), Some(sortDirection)) map { quotes =>
              val response = QuoteResponse(quotes = quotes, error = None)
              Ok(Json.toJson(response))
            }
          }
          else {
            logger.debug(s"Quotes for ${qr.ticker} are not cached")
            val tickerExistsF = qs.cacheTicker(qr.ticker, startDate, endDate)

            tickerExistsF map { tickerExists =>
              if (tickerExists) {
                val response = QuoteResponse(quotes = Seq.empty[CombinedQuote], error = Some(COME_BACK_LATER))
                Ok(Json.toJson(response))
              }
              else {
                logger.debug(s"${qr.ticker} does not exist")
                val response = QuoteResponse(quotes = Seq.empty[CombinedQuote], error = Some(NO_SUCH_TICKER))
                Ok(Json.toJson(response))
              }
            }
          }
        } recover {
          case e: Throwable =>
            logger.error(s"Error Responding to web request", e)
            val response = QuoteResponse(quotes = Seq.empty[CombinedQuote], error = Some(e.getMessage))
            InternalServerError(Json.toJson(response))
        }

      case e: JsError =>
        logger.error(s"Error parsing request JSON, ${JsError.toJson(e)}")
        val response = QuoteResponse(quotes = Seq.empty[CombinedQuote], error = Some("parse error"))
        Future.successful(BadRequest(Json.toJson(response)))
    }
  }

  private def sortFromRequest(quoteRequest: QuoteRequest): (String, String) = {
    quoteRequest.order flatMap { order =>
      val tokens = order.split(".")

      tokens match {
        case Array(field, direction) =>
          Some((field, direction))
        case _ =>
          logger.warn(s"Unable to parse sort order $order")
          None
      }
    } getOrElse ((DEFAULT_SORT_FIELD, DEFAULT_SORT_DIRECTION))
  }

}

object RequestImplicits {
  val SORT_PATTERN = new Regex("^[date|open|close|is_dividend_day][.asc|.desc]$")

  implicit val quoteRequestReads: Reads[QuoteRequest] = (
    (JsPath \ "ticker").read[String] and
      (JsPath \ "limit").readNullable[Int] and
      (JsPath \ "order").readNullable[String](Reads.pattern(SORT_PATTERN)) and
      (JsPath \ "skip").readNullable[Int]
    ) (QuoteRequest.apply _)
}

object ResponseImplicits {
  implicit val quoteWrites: Writes[CombinedQuote] = (
    (JsPath \ "date").write[String] and
      (JsPath \ "open").write[String] and
      (JsPath \ "close").write[String] and
      (JsPath \ "is_dividend_day").write[Boolean]
    ) (unlift(CombinedQuote.unapply))

  implicit val quoteResponseWrites: Writes[QuoteResponse] = (
    (JsPath \ "quotes").write[Seq[CombinedQuote]] and
      (JsPath \ "error").writeNullable[String]
    ) (unlift(QuoteResponse.unapply))
}

case class QuoteRequest(
  ticker: String,
  limit: Option[Int],
  order: Option[String],
  skip: Option[Int]
)

case class QuoteResponse(
  quotes: Seq[CombinedQuote],
  error: Option[String]
)

