package models

case class Quote(
  date: String,
  open: String,
  close: String
)

case class CombinedQuote(
  date: String,
  open: String,
  close: String,
  isDividend: Boolean
)