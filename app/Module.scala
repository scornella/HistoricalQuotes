import com.google.inject.AbstractModule
import services._

class Module extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[QuotesService]).to(classOf[YahooQuotesService])
    bind(classOf[DividendsService]).to(classOf[YahooDividendsService])
    bind(classOf[QuotesCacheManager]).to(classOf[QuotesServiceCacheManager])
    bind(classOf[QuotesCache]).to(classOf[H2QuotesCache]).asEagerSingleton()
  }
}
