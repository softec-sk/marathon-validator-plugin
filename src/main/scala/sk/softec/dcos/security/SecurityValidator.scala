package sk.softec.dcos.security

import com.wix.accord.{Result, Validator}
import javax.script.ScriptEngineManager
import mesosphere.marathon.plugin.RunSpec
import mesosphere.marathon.plugin.plugin.PluginConfiguration
import mesosphere.marathon.plugin.validation.RunSpecValidator
import play.api.libs.json.JsObject

import scala.io.Source

class SecurityValidator extends RunSpecValidator with PluginConfiguration {

  private var validator: Validator[RunSpec] = _

  private val scriptEngineManager = new ScriptEngineManager(getClass.getClassLoader)
  private val scriptEngine = scriptEngineManager.getEngineByName("scala")

  override def initialize(marathonInfo: Map[String, Any], configuration: JsObject): Unit = {
    val source = Source.fromFile((configuration \ "validationsScriptPath").as[String])
    try {
      validator = scriptEngine.eval(source.mkString).asInstanceOf[Validator[RunSpec]]
    } finally {
      source.close()
    }
  }

  override def apply(runSpec: RunSpec): Result = validator(runSpec)
}
