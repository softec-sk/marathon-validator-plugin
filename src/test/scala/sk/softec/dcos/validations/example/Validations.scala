package sk.softec.dcos.validations.example

/**
 * Example validations
 */
object Validations {
  import com.wix.accord.ViolationBuilder._
  import com.wix.accord._
  import com.wix.accord.dsl._
  import mesosphere.marathon.plugin.{PathId, PodSpec, RunSpec}
  import mesosphere.marathon.state.AppDefinition
  import mesosphere.marathon.state.Container
  import mesosphere.marathon.state.EnvVarSecretRef
  import mesosphere.marathon.state.HostVolume
  import mesosphere.marathon.state.Secret
  import mesosphere.marathon.state.SecretVolume
  import mesosphere.marathon.state.Volume
  import mesosphere.marathon.state.VolumeWithMount

  import scala.util.matching.Regex

  val allowedHostMountsRO = Seq("/etc/localtime", "/etc/ssl/certs", "/etc/timezone")

  case class UserValidator(maybeContainer: Option[Container])
      extends BaseValidator[Option[String]](
        {
          case Some("root") if maybeContainer.exists(_.isInstanceOf[Container.Mesos]) => false
          case _                                                                      => true
        },
        _ -> "Application without container image cannot run as root"
      )

  case class PrivilegedContainerValidator(appId: PathId)
      extends BaseValidator[Container](
        {
          case docker: Container.Docker => !docker.privileged || appId.path.head == "layer1"
          case _                        => true
        },
        _ -> "Docker containers cannot run in privileged mode"
      )

  case object ForcePullImageEnabled
      extends BaseValidator[Container](
        {
          case docker: Container.Docker           => docker.forcePullImage
          case mesosDocker: Container.MesosDocker => mesosDocker.forcePullImage
          case _                                  => true
        },
        _ -> "All containers must have forcePullImage enabled"
      )

  val allowedDockerParameters = Seq("label", "log-driver", "ulimit", "user")

  case object DockerContainerParameters
      extends BaseValidator[Container](
        {
          case docker: Container.Docker => docker.parameters.forall(parameter => allowedDockerParameters.contains(parameter.key))
          case _                        => true
        },
        _ -> s"Only ${allowedDockerParameters.mkString(", ")} docker parameters are allowed"
      )

  case class ContainerHostVolumeValidator(appId: PathId)
      extends BaseValidator[VolumeWithMount[Volume]](
        {
          case VolumeWithMount(HostVolume(_, hostPath), mount) =>
            // layer1 has complete access, because of the exporters
            lazy val isLayer1 = appId.path.head == "layer1"
            // layer 2 is restricted only to its storage
            lazy val isLayer2 = appId.path.head == "layer2" && hostPath.startsWith("/mnt/layer2/")
            lazy val isAllowedMountRW = hostPath.startsWith(s"/mnt/data/${appId.path.head}/")
            lazy val isAllowedMountRO = mount.readOnly && allowedHostMountsRO.exists(hostPath.startsWith)
            // all paths without / are relative to sandbox and valid as persistent mounts (so no need to check for persistent volume presence)
            lazy val isPersistentMount = !hostPath.contains("/")

            isLayer1 || isLayer2 || isAllowedMountRW || isAllowedMountRO || isPersistentMount
          case _ =>
            true
        },
        _ -> s"Allowed host paths for RW = /mnt/data/${appId.path.head}/, RO = ${allowedHostMountsRO.mkString(", ")}"
      )

  object EmailRegexp {
    val softec: Regex = "[a-zA-Z.]+@softec\\.(sk|cz)(,[a-zA-Z.]+@softec\\.(sk|cz))*".r
  }

  case object MaintainerValidator
      extends BaseValidator[Map[String, String]](
        labels => labels.get("MAINTAINER").exists(label => EmailRegexp.softec.pattern.matcher(label).matches()),
        _ -> s"Application must have label MAINTAINER with comma separated email addresses of task maintainers"
      )

  def beRelativeOrStartsWithGroup(appId: PathId): Validator[String] =
    new BaseValidator[String](
      source => !source.startsWith("/") || source.startsWith(s"/${appId.path.head}/"),
      _ -> s"Path must start with '/${appId.path.head}/'"
    )

  def secretVolumeRefValidator(appId: PathId, secretRef: String): Validator[Secret] = validator[Secret] { secret =>
    secret.source as s"secrets/$secretRef/source" must matchRegexFully("(?:/?(?!\\.)[a-z0-9_.-]+)+")
    secret.source as s"secrets/$secretRef/source" must beRelativeOrStartsWithGroup(appId)
  }

  def containerValidator(appId: PathId): Validator[Container] =
    validator[Container] { container =>
      container as "docker/parameters" is valid(DockerContainerParameters)
      container as "docker/forcePullImage" is valid(ForcePullImageEnabled)
      container as "docker/privileged" is valid(PrivilegedContainerValidator(appId))
      container.volumes.each is valid(ContainerHostVolumeValidator(appId))
    }

  case object SecretVolumeValidator extends Validator[AppDefinition] {
    override def apply(app: AppDefinition): Result =
      app.container
        .map(
          _.volumes
            .collect {
              case VolumeWithMount(SecretVolume(_, secretRef), _) =>
                validate(app.secrets(secretRef))(secretVolumeRefValidator(app.id, secretRef))
            }
            .fold(Success)(_ and _)
        )
        .getOrElse(Success)
  }

  def envVarSecretValidator(appId: PathId, secretRef: String): Validator[Secret] =
    validator[Secret] { secret =>
      secret.source as s"secrets/$secretRef/source" must matchRegexFully("(?:/?(?!\\.)[a-z0-9_.-]+)+@[a-z0-9.-]+")
      secret.source as s"secrets/$secretRef/source" must beRelativeOrStartsWithGroup(appId)
    }

  case object SecretEnvVarValueValidator extends Validator[AppDefinition] {
    override def apply(app: AppDefinition): Result =
      app.env.values
        .collect {
          case EnvVarSecretRef(secretRef) => validate(app.secrets(secretRef))(envVarSecretValidator(app.id, secretRef))
        }
        .fold(Success)(_ and _)
  }

  val appValidator: Validator[AppDefinition] = validator[AppDefinition] { app =>
    app is valid(SecretEnvVarValueValidator)
    app is valid(SecretVolumeValidator)
    app.container.each is valid(containerValidator(app.id))
    app.labels is valid(MaintainerValidator)
    app.user is valid(UserValidator(app.container))
  }

  new Validator[RunSpec] {
    override def apply(runSpec: RunSpec): Result = runSpec match {
      case app: AppDefinition =>
        appValidator(app)
      case pod: PodSpec =>
        Failure(Set(RuleViolation(pod, "Running pods is not allowed")))
    }
  }
}
