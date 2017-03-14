package controllers

import java.time.LocalDate
import javax.inject.Inject

import models.CombinedQuote
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import play.api.{ Configuration, Logger }
import services.{ QuotesCache, QuotesCacheManager }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.matching.Regex

class QuotesController @Inject()(
  configuration: Configuration,
  cacheManager: QuotesCacheManager,
  cache: QuotesCache,
  implicit val ec: ExecutionContext
) extends Controller {

  private val logger: Logger = Logger(this.getClass)

  private val START_DATE = LocalDate.parse(configuration.getString("historicalQuotes.getQuotes.startDate").get)
  private val END_DATE = LocalDate.parse(configuration.getString("historicalQuotes.getQuotes.endDate").get)

  private val DEFAULT_SORT_FIELD = "date"
  private val DEFAULT_SORT_DIRECTION = "asc"

  private val COME_BACK_LATER = configuration.getString("historicalQuotes.getQuotes.noCacheMessage").get
  private val NO_SUCH_TICKER = configuration.getString("historicalQuotes.getQuotes.noSuchSymbolMessage").get
  private val UNEXPECTED = configuration.getString("historicalQuotes.getQuotes.unexpectedError").get

  import RequestImplicits._
  import ResponseImplicits._

  def getQuote = Action.async(BodyParsers.parse.json) { implicit request =>
    //try and parse request body
    val jsResult = request.body.validate[QuoteRequest]

    jsResult match {
      case s: JsSuccess[QuoteRequest] =>
        val quoteRequest = s.get

        //check the cache to see if quotes already present
        cache.containsQuote(quoteRequest.ticker) flatMap { hasQuote =>
          if (hasQuote) {
            //fetch the quotes and return
            fetchQuotes(quoteRequest) map (response => Ok(Json.toJson(response)))
          }
          else {
            //ask the cache manager to cache
            cacheQuotes(quoteRequest) map { symbolExists =>
              if (symbolExists) {
                //tell the user to come back later
                Ok(errorAsJson(COME_BACK_LATER))
              }
              else {
                logger.debug(s"${quoteRequest.ticker} does not exist")
                //that ticker symbol isnt valid
                Ok(errorAsJson(NO_SUCH_TICKER))
              }
            }
          }
        } recover {
          case e: Throwable =>
            logger.error(s"Error responding to web request", e)
            InternalServerError(errorAsJson(UNEXPECTED))
        }

      case e: JsError =>
        logger.error(s"Error parsing request JSON, ${JsError.toJson(e)}")
        Future.successful(BadRequest(errorAsJson(s"Error parsing JSON: ${JsError.toJson(e)}")))
    }
  }

  private def cacheQuotes(quoteRequest: QuoteRequest): Future[Boolean] = {
    logger.debug(s"Quotes for ${quoteRequest.ticker} are not cached")

    cacheManager.cacheTicker(quoteRequest.ticker, START_DATE, END_DATE)
  }

  private def fetchQuotes(quoteRequest: QuoteRequest): Future[QuoteResponse] = {
    logger.debug(s"Quotes for ${quoteRequest.ticker} are already cached")

    val (sortField, sortDirection) = sortFromRequest(quoteRequest)

    cache.retrieveQuote(
      quoteRequest.ticker,
      quoteRequest.limit,
      quoteRequest.skip,
      Some(sortField),
      Some(sortDirection)
    ) map { quotes =>

      QuoteResponse(quotes = quotes, error = None)
    }
  }

  private def errorAsJson(error: String): JsValue = {
    val response = QuoteResponse(quotes = Seq.empty[CombinedQuote], error = Some(error))
    Json.toJson(response)
  }

  private def sortFromRequest(quoteRequest: QuoteRequest): (String, String) = {
    //try and parse a sort field and order or fall back to default
    quoteRequest.order flatMap { order =>
      order.split("\\.") match {
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
  val SORT_PATTERN = new Regex("^(date|open|close|is_dividend_day){1}\\.(asc|desc){1}$")

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

