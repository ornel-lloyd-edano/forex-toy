package forex.services.rates.interpreters

import forex.domain.Rate
import forex.services.rates.errors.Error

trait Algebra[F[_]] {
  def get(pair: Rate.Pair*): F[Error Either List[Rate]]
}
