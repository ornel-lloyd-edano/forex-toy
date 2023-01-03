package forex.programs.rates

import cats.implicits.catsSyntaxOptionId
import forex.services.rates.errors.Error.{OneFrameCurrencyPairNotFound, OneFrameForbiddenAccess, OneFrameLookupFailed, OneFrameProxyUnsupportedCurrency, OneFrameStaleRates}
import forex.services.rates.errors.{Error => RatesServiceError}

object errors {

  sealed trait ErrorType
  case object Permission extends ErrorType
  case object Deserialization extends ErrorType
  case object InvalidInput extends ErrorType
  case object StaleData extends ErrorType
  case object Infrastructure extends ErrorType
  case object Unexpected extends ErrorType

  sealed trait Error {
    val `type`: ErrorType
    val msg: String
    val reason: Option[String]
  }
  object Error {
    final case class RateLookupFailed(`type`: ErrorType, msg: String, reason: Option[String] = None) extends Error {
      override def toString: String = s"$msg.${reason.map(reason=> s" Reason: $reason").getOrElse("")}"
    }
  }

  def toProgramError(error: RatesServiceError): Error = (error match {
    case OneFrameForbiddenAccess => Error.RateLookupFailed(Permission, "Permission Denied")
    case _: OneFrameProxyUnsupportedCurrency => Error.RateLookupFailed(Deserialization, "Deserialization Error")
    case _: OneFrameCurrencyPairNotFound => Error.RateLookupFailed(InvalidInput, "Invalid Currency")
    case _: OneFrameStaleRates => Error.RateLookupFailed(StaleData, "Stale Rate")
    case _: OneFrameLookupFailed => Error.RateLookupFailed(Unexpected, "Unexpected Error")
  }).copy(reason = error.msg.some)
}
