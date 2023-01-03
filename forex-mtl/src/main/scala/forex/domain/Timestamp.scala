package forex.domain

import java.time.{Clock, Duration, Instant, OffsetDateTime}

case class Timestamp(value: OffsetDateTime) extends AnyVal {
  def isOld(implicit clock: Clock): Boolean = Duration.between(value.toInstant, Instant.now(clock)).toMinutes > 5
}

object Timestamp {
  def now(implicit clock: Clock): Timestamp =
    Timestamp(OffsetDateTime.now(clock))

}
