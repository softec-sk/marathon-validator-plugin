package sk.softec.dcos.security

import com.wix.accord._
import com.wix.accord.dsl._
import mesosphere.marathon.plugin.plugin.PluginConfiguration
import mesosphere.marathon.plugin.validation.RunSpecValidator
import mesosphere.marathon.plugin.{PodSpec, RunSpec}
import mesosphere.marathon.state.{AppDefinition, Container, HostVolume, Volume}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, Json, Reads}

class SecurityValidator extends RunSpecValidator with PluginConfiguration {

  private var configuration: SecurityValidatorConfiguration = _

  override def initialize(marathonInfo: Map[String, Any], configuration: JsObject): Unit =
    this.configuration = configuration.as[SecurityValidatorConfiguration]

  import ViolationBuilder._

  case class UserValidator(maybeContainer: Option[Container]) extends Validator[Option[String]] {
    override def apply(maybeUser: Option[String]): Result = maybeUser match {
      case Some("root") if !configuration.allowRootWithoutImage && maybeContainer.exists(_.isInstanceOf[Container.Mesos]) =>
        maybeUser -> "Application without container image cannot run as root."
      case _ =>
        Success
    }
  }

  case object VolumeValidator
    extends BaseValidator[Volume](
      volume => {
        SecurityValidator.logger.info("Validating volume {}", volume)
        volume match {
          case HostVolume(_, hostPath) if configuration.allowedHostMounts.contains(hostPath) => true
          case _                                                                             => false
        }
      },
      _ -> s"Allowed host paths are ${configuration.allowedHostMounts.mkString(", ")}."
    )

  case object RequiredLabelsValidator
    extends BaseValidator[Map[String, String]](
      labels =>
        configuration.requiredLabels.forall { requiredLabel =>
          labels.contains(requiredLabel) && labels(requiredLabel).nonEmpty
        },
      _ -> s"Application must have non empty labels ${configuration.requiredLabels.mkString(", ")}"
    )

  private val appValidator = validator[AppDefinition] { app =>
    app.user is valid(UserValidator(app.container))
    app.volumes.each is valid(VolumeValidator)
    app.labels is valid(RequiredLabelsValidator)
  }

  private val isValid = new Validator[RunSpec] {
    override def apply(v1: RunSpec): Result = v1 match {
      case app: AppDefinition =>
        appValidator(app)
      case _: PodSpec =>
        Success
    }
  }

  override def apply(runSpec: RunSpec): Result = {
    SecurityValidator.logger.info("Validating runSpec {}", runSpec.id)
    isValid(runSpec)
  }
}

object SecurityValidator {
  private val logger = LoggerFactory.getLogger(classOf[SecurityValidator])
}

case class SecurityValidatorConfiguration(
  allowRootWithoutImage: Boolean,
  allowedHostMounts: Set[String],
  requiredLabels: Set[String]
)

object SecurityValidatorConfiguration {
  implicit val reads: Reads[SecurityValidatorConfiguration] = Json.reads[SecurityValidatorConfiguration]
}
