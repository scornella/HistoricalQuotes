package services

import java.time.LocalDate
import javax.inject.Inject

import play.api.Logger

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait QuotesCacheManager {

  def cacheTicker(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Boolean]

}

class QuotesServiceCacheManager @Inject() (
  quotesCache: QuotesCache,
  quotesService: QuotesService,
  dividendsService: DividendsService,
  implicit val ec: ExecutionContext
) extends QuotesCacheManager {

  val logger: Logger = Logger(this.getClass)

  override def cacheTicker(ticker: String, startDate: LocalDate, endDate: LocalDate): Future[Boolean] = {

    quotesService.tickerExists(ticker, startDate, endDate) map { exists =>

      if(exists){
        val quotesFuture = quotesService.getQuotes(ticker, startDate, endDate) flatMap { quotes =>
          quotesCache.insertQuotes(ticker, quotes)
        }

        quotesFuture onComplete {
          case Success(_) => logger.info(s"Successfully cached quotes for $ticker")
          case Failure(e) => logger.error(s"Failure while caching quotes for $ticker", e)
        }

        val dividendsFuture = dividendsService.getDividends(ticker, startDate, endDate) flatMap { dividends =>
          quotesCache.insertDividends(ticker, dividends)
        }

        dividendsFuture onComplete {
          case Success(_) => logger.info(s"Successfully cached dividends for $ticker")
          case Failure(e) => logger.error(s"Failure while caching dividends for $ticker", e)
        }
      }

      exists
    }
  }
}
