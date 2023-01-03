package forex

import forex.services.rates.interpreters.Algebra

package object services {
  type RatesService[F[_]] = Algebra[F]
  final val RatesServices = rates.Interpreters
}
