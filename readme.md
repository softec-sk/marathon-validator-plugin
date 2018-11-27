# Marathon validator plugin

Extensible Marathon plugin for additional tasks validation. Leveraging Scala `ScriptEngined` for customizable validations via [Accord](http://wix.github.io/accord/). 

```json
{
  "plugins": {
    "validations": {
      "plugin": "mesosphere.marathon.plugin.validation.RunSpecValidator",
      "implementation": "sk.softec.dcos.validations.ScriptableValidator",
      "configuration": {
        "validationsScriptPath": "/opt/softec/marathon-custom-validations.scala"
      }
    }
  }
} 
```

Example validations are in [Validations.scala](src/test/scala/sk/softec/dcos/validations/example/Validations.scala).