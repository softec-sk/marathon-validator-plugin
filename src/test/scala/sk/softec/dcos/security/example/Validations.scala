package sk.softec.dcos.security.example

/**
 * Example validations
 */
object Validations {
  import com.wix.accord.ViolationBuilder._
  import com.wix.accord._
  import com.wix.accord.dsl._
  import mesosphere.marathon.plugin.{PathId, PodSpec, RunSpec}
  import mesosphere.marathon.state.{AppDefinition, Container, HostVolume, Volume, VolumeWithMount}

  import scala.util.matching.Regex

  val allowedHostMountsRW = Seq("/mnt/data/")
  val allowedHostMountsRO = Seq("/etc/localtime", "/etc/ssl/certs", "/etc/timezone")

  case class UserValidator(maybeContainer: Option[Container])
      extends BaseValidator[Option[String]](
        {
          case Some("root") if maybeContainer.exists(_.isInstanceOf[Container.Mesos]) => false
          case _                                                                      => true
        },
        _ -> "Application without container image cannot run as root."
      )

  case class PrivilegedContainerValidator(appId: PathId)
      extends BaseValidator[Container](
        {
          case docker: Container.Docker => !docker.privileged || appId.path.head == "layer1"
          case _                        => true
        },
        _ -> "Docker containers cannot run in privileged mode."
      )

  case object ForcePullImageEnabled
      extends BaseValidator[Container](
        {
          case docker: Container.Docker           => docker.forcePullImage
          case mesosAppC: Container.MesosAppC     => mesosAppC.forcePullImage
          case mesosDocker: Container.MesosDocker => mesosDocker.forcePullImage
          case _                                  => true
        },
        _ -> "All containers must have forcePullImage enabled."
      )

  val allowedDockerParameters = Seq("label", "log-driver", "ulimit", "user")

  case object DockerContainerParameters
      extends BaseValidator[Container](
        {
          case docker: Container.Docker => docker.parameters.forall(parameter => allowedDockerParameters.contains(parameter.key))
          case _                        => true
        },
        _ -> s"Only ${allowedDockerParameters.mkString(", ")} docker parameters are allowed."
      )

  case class ContainerVolumeValidator(appId: PathId)
      extends BaseValidator[VolumeWithMount[Volume]](
        {
          case VolumeWithMount(HostVolume(_, hostPath), mount) =>
            // layer1 has complete access, because of the exporters
            lazy val isLayer1 = appId.path.head == "layer1"
            // layer 2 is restricted only to its storage
            lazy val isLayer2 = appId.path.head == "layer2" && hostPath.startsWith("/mnt/layer2/")
            lazy val isAllowedMountRW = allowedHostMountsRW.exists(hostPath.startsWith)
            lazy val isAllowedMountRO = mount.readOnly && allowedHostMountsRO.exists(hostPath.startsWith)
            // all paths without / are relative to sandbox and valid as persistent mounts (so no need to check for persistent volume presence)
            lazy val isPersistentMount = !hostPath.contains("/")

            isLayer1 || isLayer2 || isAllowedMountRW || isAllowedMountRO || isPersistentMount
          case _ =>
            true
        },
        _ -> s"Allowed host paths for RW = ${allowedHostMountsRW.mkString(", ")}, RO = ${allowedHostMountsRO.mkString(", ")}."
      )

  object EmailRegexp {
    val softec: Regex = "[a-zA-Z.]+@softec\\.(sk|cz)(,[a-zA-Z.]+@softec\\.(sk|cz))*".r
  }

  case object MaintainerValidator
      extends BaseValidator[Map[String, String]](
        labels => labels.get("MAINTAINER").exists(label => EmailRegexp.softec.pattern.matcher(label).matches()),
        _ -> s"Application must have label MAINTAINER with comma separated email addresses of task maintainers."
      )

  def containerValidator(appId: PathId): Validator[Container] =
    validator[Container] { container =>
      container is valid(PrivilegedContainerValidator(appId))
      container is valid(ForcePullImageEnabled)
      container is valid(DockerContainerParameters)
      container.volumes.each is valid(ContainerVolumeValidator(appId))
    }

  val appValidator: Validator[AppDefinition] = validator[AppDefinition] { app =>
    app.user is valid(UserValidator(app.container))
    app.container.each is valid(containerValidator(app.id))
    app.labels is valid(MaintainerValidator)
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
