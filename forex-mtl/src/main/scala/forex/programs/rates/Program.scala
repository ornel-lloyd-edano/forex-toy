package forex.programs.rates

import cats.Applicative
import cats.data.EitherT
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxEitherId, catsSyntaxOptionId}
import errors._
import forex.config.ApplicationConfig
import forex.domain._
import forex.services.RatesService
import scalacache.Cache

import scala.concurrent.duration.DurationInt

class Program[F[_]: Applicative](
    ratesService: RatesService[F], cache: Cache[Either[Error,List[Rate]]], config: ApplicationConfig
) extends Algebra[F] {

  override def get(request: Protocol.GetRatesRequests): F[Error Either List[Rate]] = {
    import scalacache.modes.sync._

    def get() = cache.get(request.requests.toSet) match {
      case Some(cachedResult) => cachedResult.pure[F]
      case None =>
        EitherT(ratesService.get(request.requests.map(r=>Rate.Pair(r.from, r.to)):_*))
          .map { rates=>
            cache.put(request)(rates.asRight[Error], Rate.maxAgeBeforeStale.some)
            rates
          }.leftMap { error=>
          val err = toProgramError(error)
          err match {
            case err if err.`type` == errors.Deserialization =>
              cache.put(request)(err.asLeft[List[Rate]], 24.hours.some)
            case err if err.`type` == errors.Permission =>
              cache.put(config.oneFrameService.authToken)(err.asLeft[List[Rate]], 8.hours.some)
            case err if err.`type` == errors.Infrastructure =>
              val externalResource = config.oneFrameService.getDomain
              cache.put(externalResource)(err.asLeft[List[Rate]], 15.minutes.some)
            case err if err.`type` == errors.StaleData =>
              val externalResource = config.oneFrameService.getDomain
              cache.put(externalResource)(err.asLeft[List[Rate]], 5.seconds.some)
            case err =>
              cache.put(request)(err.asLeft[List[Rate]], 24.hours.some)
          }
          err
        }.value
    }

    cache.get(config.oneFrameService.authToken) match {
      case Some(Left(error)) => error.asLeft[List[Rate]].pure[F]
      case _ => get()
    }
  }

}

object Program {

  def apply[F[_]: Applicative](
      ratesService: RatesService[F], cache: Cache[Either[Error,List[Rate]]], config: ApplicationConfig
  ): Algebra[F] = new Program[F](ratesService, cache, config)

}
