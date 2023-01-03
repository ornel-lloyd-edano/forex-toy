package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    oneFrameService: HttpConfig
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration,
    authToken: Option[String]
) {
  def getDomain:String = s"$host:$port"
}