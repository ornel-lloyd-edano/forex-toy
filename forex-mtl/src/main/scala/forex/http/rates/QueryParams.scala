package forex.http.rates

//import cats.data._
//import cats.implicits._
import cats.implicits.catsSyntaxEitherId
import forex.domain.Currency
import org.http4s.dsl.impl.OptionalMultiQueryParamDecoderMatcher
import forex.domain.Rate.Pair
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher

object QueryParams {

  private[http] implicit val currencyQueryParam: QueryParamDecoder[Either[String, Currency]] =
    QueryParamDecoder[String].map(currencyStr=> Currency.fromString(currencyStr).toRight(currencyStr))

  object FromQueryParam extends QueryParamDecoderMatcher[Either[String, Currency]]("from")
  object ToQueryParam extends QueryParamDecoderMatcher[Either[String, Currency]]("to")

  /*implicit lazy val stringListQueryParamDecoder: QueryParamDecoder[List[String]] =
    new QueryParamDecoder[List[String]] {
      def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, List[String]] = {
        value.value.split(""";""").toList.map(_.validNel[ParseFailure]).sequence
      }
    }*/

  private[http] implicit val currencyPairQueryParam: QueryParamDecoder[Either[String, Pair]] = {
      QueryParamDecoder[String].map(currencyPairStr => {
        (currencyPairStr.size == 7, currencyPairStr.take(3), currencyPairStr.drop(4)) match {
          case (true, from, to) =>
            Currency.fromString(from).flatMap(from =>
              Currency.fromString(to).map(to => Pair(from, to).asRight)
            ).getOrElse(currencyPairStr.asLeft)
          case _ => currencyPairStr.asLeft
        }
      })
  }

  object PairQueryParams extends OptionalMultiQueryParamDecoderMatcher[Either[String, Pair]]("from_to")
}
