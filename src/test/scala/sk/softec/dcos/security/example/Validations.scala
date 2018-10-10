package sk.softec.dcos.security.example

/**
 * Example validations
 */
object Validations {
  import com.wix.accord.ViolationBuilder._
  import com.wix.accord._
  import com.wix.accord.dsl._
  import mesosphere.marathon.plugin.{PodSpec, RunSpec}
  import mesosphere.marathon.state.{AppDefinition, Container, HostVolume, Volume}

  val allowedHostMounts = Seq("/mnt/data", "/home")
  val requiredLabels = Seq("MAINTAINER")

  case class UserValidator(maybeContainer: Option[Container]) extends Validator[Option[String]] {
    override def apply(maybeUser: Option[String]): Result = maybeUser match {
      case Some("root") if maybeContainer.exists(_.isInstanceOf[Container.Mesos]) =>
        maybeUser -> "Application without container image cannot run as root."
      case _ =>
        Success
    }
  }

  case object VolumeValidator
      extends BaseValidator[Volume](
        {
          case HostVolume(_, hostPath) if allowedHostMounts.contains(hostPath) => true
          case _                                                               => false
        },
        _ -> s"Allowed host paths are ${allowedHostMounts.mkString(", ")}."
      )

  case object RequiredLabelsValidator
      extends BaseValidator[Map[String, String]](
        labels =>
          requiredLabels.forall { requiredLabel =>
            labels.contains(requiredLabel) && labels(requiredLabel).nonEmpty
        },
        _ -> s"Application must have non empty labels ${requiredLabels.mkString(", ")}"
      )

  val appValidator = validator[AppDefinition] { app =>
    app.user is valid(UserValidator(app.container))
    app.volumes.each is valid(VolumeValidator)
    app.labels is valid(RequiredLabelsValidator)
  }

  new Validator[RunSpec] {
    override def apply(runSpec: RunSpec): Result = runSpec match {
      case app: AppDefinition =>
        appValidator(app)
      case _: PodSpec =>
        Success
    }
  }
}
