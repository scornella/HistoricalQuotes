package services

import javax.inject.{ Inject, Singleton }

import anorm.SqlParser._
import anorm._
import models.{ CombinedQuote, Dividend, Quote }
import play.api.Logger
import play.api.db.Database

import scala.concurrent.{ ExecutionContext, Future }

trait QuotesCache {

  def containsQuote(ticker: String): Future[Boolean]

  def retrieveQuote(
    ticker: String,
    limit: Option[Int],
    skip: Option[Int],
    sortField: Option[String],
    sortDirection: Option[String]
  ): Future[Seq[CombinedQuote]]

  def insertQuotes(ticker: String, quotes: Seq[Quote]): Future[Unit]

  def insertDividends(ticker: String, dividends: Seq[Dividend]): Future[Unit]
}

@Singleton
class H2QuotesCache @Inject()(db: Database, implicit val ec: ExecutionContext) extends QuotesCache {
  private val logger: Logger = Logger(this.getClass)

  private val quoteParser = get[String]("date") ~
    get[String]("open") ~
    get[String]("close") ~
    get[Boolean]("is_dividend_day") map {
    case date ~ open ~ close ~ dividend =>
      CombinedQuote(date, open, close, dividend)
  }

  override def insertQuotes(ticker: String, quotes: Seq[Quote]): Future[Unit] = {
    Future {
      quotes foreach (q => insertQuote(ticker, q))
    }
  }

  private def insertQuote(ticker: String, quote: Quote): Unit = {
    db.withConnection { implicit c =>
      SQL(
        s"""
           |insert into quotes(ticker, date, open, close)
           |values('$ticker', '${quote.date}', '${quote.open}', '${quote.close}')
        """.stripMargin
      ).execute()
    }
  }

  override def insertDividends(ticker: String, dividends: Seq[Dividend]): Future[Unit] = {
    Future {
      dividends foreach (d => insertDividend(ticker, d))
    }
  }

  private def insertDividend(ticker: String, dividend: Dividend): Unit = {
    db.withConnection { implicit c =>
      SQL(
        s"""
           |insert into dividends(ticker, date, dividend)
           |values('$ticker', '${dividend.date}', '${dividend.dividend}')
        """.stripMargin
      ).execute()
    }
  }

  override def containsQuote(ticker: String): Future[Boolean] = {
    Future {
      db.withConnection { implicit c =>
        val exists = SQL(
          s"""
             |select count(1)
             |from quotes
             |where ticker = '$ticker'
        """.stripMargin
        ).as(scalar[Int].single)

        exists > 1
      }
    }
  }

  override def retrieveQuote(
    ticker: String,
    limit: Option[Int],
    skip: Option[Int],
    sortField: Option[String],
    sortDirection: Option[String]
  ): Future[Seq[CombinedQuote]] = {
    Future {
      db.withConnection { implicit c =>

        val sort = for {
          field <- sortField
          direction <- sortDirection
        } yield s"order by $field $direction"

        val optional = Seq(
          sort,
          limit.map(l => s"limit $l"),
          skip.map(s => s"offset $s")
        ).flatten.mkString(" ")

        SQL(
          s"""
             |select quotes.date, quotes.open, quotes.close,
             |case when dividends.dividend is null then false else true end as is_dividend_day
             |from quotes
             |left join dividends
             |on quotes.ticker = dividends.ticker
             |and quotes.date = dividends.date
             |where quotes.ticker = '$ticker'
             |$optional
        """.stripMargin
        ).as(quoteParser.*)
      }
    }
  }

  private def createQuoteTable(): Unit = {
    db.withConnection { implicit c =>
      SQL(
        """
          |drop table if exists quotes;
          |create table quotes (
          |id int primary key auto_increment,
          |ticker varchar(20),
          |date varchar(10),
          |open varchar(10),
          |close varchar(10)
          )
        """.stripMargin
      ).execute()
    }
  }

  private def createDividendTable(): Unit = {
    db.withConnection { implicit c =>
      SQL(
        """
          |drop table if exists dividends;
          |create table dividends (
          |id int primary key auto_increment,
          |ticker varchar(20),
          |date varchar(10),
          |dividend varchar(10)
          )
        """.stripMargin
      ).execute()
    }
  }

  createQuoteTable()
  createDividendTable()
}