package forex

import scala.concurrent.ExecutionContext
import cats.effect._
import com.google.common.cache.CacheBuilder
import forex.config._
import forex.domain.Rate
import forex.programs.rates.errors.Error
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.blaze.BlazeServerBuilder
import scalacache._
import guava._

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    new Application[IO].stream(executionContext).compile.drain.as(ExitCode.Success)

}

class Application[F[_]: ConcurrentEffect: Timer] {
  def stream(ec: ExecutionContext): Stream[F, Unit] = {
    for {
      config <- Config.stream("app")
      httpClient <- BlazeClientBuilder[F](ec).stream
      underlyingCache = CacheBuilder.newBuilder()
        .maximumSize(Integer.MAX_VALUE)
        .build[String, Entry[Either[Error, List[Rate]]]]
      cache = GuavaCache(underlyingCache)
      module = new Module[F](httpClient, config, java.time.Clock.systemUTC(), cache)
      _ <- BlazeServerBuilder[F](ec)
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()
  }

}
