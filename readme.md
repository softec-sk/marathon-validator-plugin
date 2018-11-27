# Marathon security validation plugin

Extensible Marathon plugin for additional tasks security validation. Leveraging Scala `ScriptEngined` for customizable validations via [Accord](http://wix.github.io/accord/). 

```json
{
  "plugins": {
    "security-validation": {
      "plugin": "mesosphere.marathon.plugin.validation.RunSpecValidator",
      "implementation": "sk.softec.dcos.security.SecurityValidator",
      "configuration": {
        "validationsScriptPath": "/opt/softec/marathon-security-validations.scala"
      }
    }
  }
} 
```

Example validations are in [Validations.scala](src/test/scala/sk/softec/dcos/security/example/Validations.scala).