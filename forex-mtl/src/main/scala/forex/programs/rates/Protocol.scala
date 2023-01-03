package forex.programs.rates

import forex.domain.Currency

object Protocol {

  final case class GetRatesRequest(
      from: Currency,
      to: Currency
  )

  final case class GetRatesRequests(
    requests: Seq[GetRatesRequest]
  )
  object GetRatesRequests {
    def apply(from: Currency, to: Currency): GetRatesRequests =
      new GetRatesRequests(Seq(GetRatesRequest(from, to)))
  }

}
