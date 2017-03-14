package services

import java.time.LocalDate

import models.{ Dividend, Quote }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec

import scala.concurrent.Future

class QuotesCacheManagerTest extends PlaySpec with MockitoSugar with ScalaFutures with BeforeAndAfterEach {
  val executionContext = scala.concurrent.ExecutionContext.Implicits.global

  var quotesService: QuotesService = null
  var quotesCache: QuotesCache = null
  var dividendsService: DividendsService = null
  var cacheManager: QuotesCacheManager = null

  override def beforeEach: Unit = {
    quotesService = mock[QuotesService]
    quotesCache = mock[QuotesCache]
    dividendsService = mock[DividendsService]

    cacheManager = new QuotesServiceCacheManager(quotesCache, quotesService, dividendsService, executionContext)

    when(quotesService.tickerExists(anyString(), any[LocalDate](), any[LocalDate]()))
      .thenReturn(Future.successful(true))
    when(quotesService.getQuotes(anyString(), any[LocalDate](), any[LocalDate]()))
      .thenReturn(Future.successful(Seq.empty[Quote]))
    when(quotesCache.insertQuotes(anyString(), any[Seq[Quote]]()))
      .thenReturn(Future.successful({}))
    when(dividendsService.getDividends(anyString(), any[LocalDate](), any[LocalDate]()))
      .thenReturn(Future.successful(Seq.empty[Dividend]))
    when(quotesCache.insertDividends(anyString(), any[Seq[Dividend]]))
      .thenReturn(Future.successful({}))
  }

  "cacheTicker" should {

    "check if the ticker exists" in {
      val result = cacheManager.cacheTicker("xyz", LocalDate.now(), LocalDate.now()).futureValue

      verify(quotesService).tickerExists(anyString(), any[LocalDate](), any[LocalDate]())

      result must be(true)
    }

    "not load data if ticker invalid" in {
      when(quotesService.tickerExists(anyString(), any[LocalDate](), any[LocalDate]()))
        .thenReturn(Future.successful(false))

      val result = cacheManager.cacheTicker("xyz", LocalDate.now(), LocalDate.now()).futureValue

      verify(quotesService, never()).getQuotes(anyString(), any[LocalDate](), any[LocalDate]())
      verify(dividendsService, never()).getDividends(anyString(), any[LocalDate](), any[LocalDate]())

      result must be(false)
    }

    "load both quotes and dividends if ticker valid" in {
      val result = cacheManager.cacheTicker("xyz", LocalDate.now(), LocalDate.now()).futureValue

      verify(quotesService, times(1)).getQuotes(anyString(), any[LocalDate](), any[LocalDate]())
      verify(quotesCache, times(1)).insertQuotes(anyString(), any[Seq[Quote]]())

      verify(dividendsService, times(1)).getDividends(anyString(), any[LocalDate](), any[LocalDate]())
      verify(quotesCache, times(1)).insertDividends(anyString(), any[Seq[Dividend]]())

      result must be(true)
    }
  }
}
