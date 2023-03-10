package forex.programs.rates

import forex.domain.Rate
import errors._

trait Algebra[F[_]] {
  def get(request: Protocol.GetRatesRequests): F[Error Either List[Rate]]
}
