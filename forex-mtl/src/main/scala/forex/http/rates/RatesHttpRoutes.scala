package forex.http
package rates

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.toTraverseOps
import cats.syntax.flatMap._
import forex.programs.RatesProgram
import forex.programs.rates.{errors, Protocol => RatesProgramProtocol}
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class RatesHttpRoutes[F[_] : Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {

  import Converters._, QueryParams._, Protocol._

  private[http] val prefixPath = "/rates"
  private[http] val prefixPathV2 = "/v2/rates/"

  private val httpRoutesV2: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? PairQueryParams(pairs) =>
      pairs.fold(err => BadRequest(err.map(_.message).toList.mkString(",")),
        _.sequence.fold(badPair => BadRequest(s"Query param [from_to=$badPair] is invalid or not supported."),
          pairs => {
            val request = RatesProgramProtocol.GetRatesRequests(pairs.map(p => RatesProgramProtocol.GetRatesRequest(p.from, p.to)))
            EitherT(rates.get(request))
              .map(rates => Ok(rates.asGetApiResponse))
              .leftMap(error => error.`type` match {
                case errors.Permission => Forbidden(error.toString)
                case errors.InvalidInput => BadRequest(error.toString)
                case errors.Infrastructure => ServiceUnavailable(error.toString)
                case _ => InternalServerError(error.toString)
              }).merge.flatten
          }))
  }

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? FromQueryParam(from) +& ToQueryParam(to) =>
      (for {
        validFrom <- EitherT.fromEither(from)
          .leftMap(invalidCurrency=> BadRequest(s"Query param [from=$invalidCurrency] currency is empty, invalid or not supported."))
        validTo <- EitherT.fromEither(to)
          .leftMap(invalidCurrency=> BadRequest(s"Query param [to=$invalidCurrency] currency is empty, invalid or not supported."))

        result <- EitherT(rates.get(RatesProgramProtocol.GetRatesRequests(validFrom, validTo)))
          .map(rates => Ok(rates.headOption.map(_.asGetApiResponse)))
          .leftMap(error => error.`type` match {
            case errors.Permission => Forbidden(error.toString)
            case errors.InvalidInput => BadRequest(error.toString)
            case errors.Infrastructure => ServiceUnavailable(error.toString)
            case _ => InternalServerError(error.toString)
          })

      } yield result).merge.flatten
    case GET -> Root :? FromQueryParam(_) =>
      BadRequest(s"Query param [to] is missing.")
    case GET -> Root :? ToQueryParam(_) =>
      BadRequest(s"Query param [from] is missing.")
    case GET -> Root =>
      BadRequest(s"Query params [from, to] are missing.")
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes,
    prefixPathV2 -> httpRoutesV2,
  )

}
