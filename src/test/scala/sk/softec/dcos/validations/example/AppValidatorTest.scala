package sk.softec.dcos.validations.example

import com.wix.accord.{Success, Validator}
import mesosphere.marathon.plugin.{PodSpec, RunSpec}
import mesosphere.marathon.state.AppDefinition

trait AppValidatorTest {
  protected def create(appValidator: Validator[AppDefinition]): Validator[RunSpec] = {
    case app: AppDefinition =>
      appValidator(app)
    case _: PodSpec =>
      Success
  }
}
