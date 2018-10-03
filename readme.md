# Marathon security validation plugin

```json
{
  "plugins": {
    "security-validation": {
      "plugin": "mesosphere.marathon.plugin.validation.RunSpecValidator",
      "implementation": "sk.softec.dcos.security.SecurityValidator",
      "configuration": {
        "allowRootWithoutImage": false,
        "allowedHostMounts": ["/mnt/data", "/home"],
        "requiredLabels": ["MAINTAINER"]
      }
    }
  }
} 
```