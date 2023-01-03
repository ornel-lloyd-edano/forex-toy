package forex.services.rates.interpreters

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.{catsSyntaxApplicativeId, toFunctorOps}
import cats.syntax.either._
import forex.domain.{Currency, Rate}
import forex.services.rates.errors._
import forex.config.ApplicationConfig
import forex.services.rates.errors.Error._
import org.http4s._
import org.http4s.client.Client

import java.time.Clock


class OneFrameLiveClient [F[_]: Sync] (httpClient: Client[F], config: ApplicationConfig, clock: Clock) extends Algebra[F] {
  private val token = config.oneFrameService.authToken.getOrElse("")

  override def get(pairs: Rate.Pair*): F[Error Either List[Rate]] = {
    def getValidOneFrameUri(domain: String) = {
      Uri.pure[F]
        .map(_.fromString(domain)
          .fold(_=> OneFrameLookupFailed(s"Invalid uri configured for One Frame service").asLeft[Uri],
            oneFrameDomain=> {
              val currencyPairs = pairs.map(pair=> s"${pair.from}${pair.to}")
              oneFrameDomain.withPath("/rates")
                .withMultiValueQueryParams( Map("pair"-> currencyPairs)).asRight[Error]
          }))
    }

    def requestOneFrame(oneFrameUri: Uri) = {
      val req = Request[F](
        method = Method.GET,
        uri = oneFrameUri,
        headers = Headers.of(Header("token", token))
      )
      lazy val pairsStr = pairs.map(p=>s"[from=${p.from},to=${p.to}]")
      httpClient.run(req).use(response => response.as[OneFrameResponse])
        .map[Either[Error, List[Rate]]] {
          case error: OneFrameResponse.Error if error.contains("Forbidden") =>
            OneFrameForbiddenAccess.asLeft[List[Rate]]
          case error: OneFrameResponse.Error if error.contains("Invalid Currency") =>
            OneFrameCurrencyPairNotFound(pairsStr).asLeft[List[Rate]]
          case error: OneFrameResponse.Error =>
            OneFrameLookupFailed(s"${error.error}: ${pairsStr.mkString(", ")}").asLeft[List[Rate]]

          case rates: OneFrameResponse.Rates if rates.rates.exists(r=> Rate.isStale(r.timestamp, clock)) =>
            OneFrameStaleRates(pairsStr, Rate.maxAgeBeforeStale).asLeft[List[Rate]]

          case rates: OneFrameResponse.Rates =>
            val currencies = rates.rates.flatMap(r=> List(r.from, r.to))
            val unsupportedCurrencies = Rate.getUnsupportedCurrencies(currencies:_*)
            if (unsupportedCurrencies.isEmpty) {
              rates.rates.map(r=>
                Currency.fromString(r.from).flatMap { from=>
                  Currency.fromString(r.to).map { to=>
                    Rate(from, to, r.price, r.timestamp)
                  }
                }).flatten.asRight[Error]
            } else {
              OneFrameProxyUnsupportedCurrency(unsupportedCurrencies).asLeft[List[Rate]]
            }
        }
    }


    (for {
      uri <- EitherT(getValidOneFrameUri(config.oneFrameService.getDomain))
      result <- EitherT(requestOneFrame(uri))
    } yield result).value

  }

}

