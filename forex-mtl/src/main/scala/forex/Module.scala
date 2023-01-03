package forex

import cats.effect.{Concurrent, Timer}
import forex.config.ApplicationConfig
import forex.domain.Rate
import forex.http.rates.RatesHttpRoutes
import forex.services._
import forex.programs._
import forex.programs.rates.errors.Error
import org.http4s._
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.server.middleware.{AutoSlash, Timeout}
import scalacache.Cache

import java.time.Clock

class Module[F[_]: Concurrent: Timer](httpClient: Client[F], config: ApplicationConfig, clock: Clock, cache: Cache[Either[Error,List[Rate]]]) {

  private val ratesService: RatesService[F] = RatesServices.live[F](httpClient, config, clock)

  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService, cache, config)

  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.timeout)(http)
  }

  private val http: HttpRoutes[F] = ratesHttpRoutes

  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

}
