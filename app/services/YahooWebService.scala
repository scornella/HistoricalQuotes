package services

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.Future

trait YahooWebService {
  protected val url: String
  protected val collection: String
  protected val store: String
  protected val ws: WSClient

  protected def toQueryString(query: String): Seq[(String, String)] = {
    Seq(
      "q" -> query,
      "env" -> store,
      "format" -> "json"
    )
  }

  protected def makeRequest(query: String): Future[WSResponse] = {
    ws.url(url)
      .withHeaders("Accept" -> "application/json")
      .withQueryString(toQueryString(query): _*)
      .get()
  }

  protected def format(date: LocalDate): String = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

}
