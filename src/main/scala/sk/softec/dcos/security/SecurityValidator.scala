package sk.softec.dcos.security

import com.wix.accord.{Failure, Result, RuleViolation, Success}
import mesosphere.marathon.plugin.plugin.PluginConfiguration
import mesosphere.marathon.plugin.validation.RunSpecValidator
import mesosphere.marathon.plugin.{ApplicationSpec, PodSpec, RunSpec}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, Json, Reads}

class SecurityValidator extends RunSpecValidator with PluginConfiguration {

  import SecurityValidator.logger

  private var configuration: SecurityValidatorConfiguration = _

  override def initialize(marathonInfo: Map[String, Any], configuration: JsObject): Unit =
    this.configuration = configuration.as[SecurityValidatorConfiguration]

  override def apply(runSpec: RunSpec): Result = runSpec match {
    case app: ApplicationSpec =>
      validate(app)
    case _: PodSpec =>
      Success
  }

  private def validate(app: ApplicationSpec) = {
    val errors = validateUser(app) ++ validateMounts(app) ++ validateLabels(app)

    if (errors.isEmpty) Success else Failure(errors.toSet)
  }

  private def validateUser(app: ApplicationSpec) = {
    val maybeUser = app.user
    val maybeContainer = fieldValue[Option[AnyRef]](app, "container")
    val maybeImage = maybeContainer.flatMap[String] { container =>
      try {
        Some(fieldValue(container, "image"))
      } catch {
        case _: NoSuchFieldException => None
      }
    }

    logger.info("Validating app {} running as {} with image {}", app.id, maybeUser, maybeImage)

    // TODO: rozumne zadefinovany default ako zistit ci defaltne bezi pod rootom?
    if (!configuration.allowRootWithoutImage && (maybeUser.contains("root") || maybeUser.isEmpty) && maybeImage.isEmpty) {
      Seq(RuleViolation(app.user, "Application without container image cannot run as root."))
    } else {
      Nil
    }
  }

  private def validateMounts(app: ApplicationSpec) = {
    logger.info("Validating app {} volume mounts {} and volumes {}", app.id, app.volumeMounts, app.volumes)

    app.volumes.flatMap { volume =>
      if (volume.getClass.getSimpleName.contains("Host")) {
        val hostPath = fieldValue[String](volume, "hostPath")

        if (configuration.allowedHostMounts.contains(hostPath)) {
          None
        } else {
          Some(RuleViolation(volume, s"$hostPath cannot be mounted. Allowed host paths are ${configuration.allowedHostMounts.mkString(", ")}."))
        }
      } else {
        None
      }
    }
  }

  private def validateLabels(app: ApplicationSpec) = {
    logger.info("Validating app {} with labels {}", Seq(app.id, app.labels.keySet): _*)

    configuration.requiredLabels.flatMap { requiredLabel =>
      app.labels.get(requiredLabel) match {
        case Some(value) if value.nonEmpty => None
        case _                             => Some(RuleViolation(app.labels, s"Application must have non empty label $requiredLabel."))
      }
    }
  }

  private def fieldValue[T](obj: AnyRef, fieldName: String) = {
    val field = obj.getClass.getDeclaredField(fieldName)
    field.setAccessible(true)
    field.get(obj).asInstanceOf[T]
  }
}

object SecurityValidator {
  private val logger = LoggerFactory.getLogger(classOf[SecurityValidator])
}

case class SecurityValidatorConfiguration(
  allowRootWithoutImage: Boolean,
  allowedHostMounts: Seq[String],
  requiredLabels: Seq[String]
)

object SecurityValidatorConfiguration {
  implicit val reads: Reads[SecurityValidatorConfiguration] = Json.reads[SecurityValidatorConfiguration]
}
