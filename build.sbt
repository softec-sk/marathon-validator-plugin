organization := "sk.softec.dcos"

name := "marathon-validator-plugin"

// Marathon 1.10.36 artifacts published in our repository
resolvers += "nexus releases" at "http://repo.softec.sk/repository/public/" withAllowInsecureProtocol true
resolvers += "Mesosphere Public Repo" at "https://downloads.mesosphere.io/maven"

scalacOptions += "-target:jvm-1.8"

scalaVersion := "2.13.3"

version := "1.10.36"

libraryDependencies ++= Seq(
  "mesosphere.marathon" %% "marathon" % version.value % Provided exclude("javax.ws.rs", "javax.ws.rs-api"),
  "mesosphere.marathon" %% "plugin-interface" % version.value % Provided,
  "org.slf4j" % "slf4j-api" % Version.slf4j % Provided,
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % "3.0.9" % Test
)

// assemblyOptions.includeScala cannot be used, it will exclude scala-compiler
assemblyExcludedJars in assembly := {
  (fullClasspath in assembly).value.filter { dependency =>
    Seq("scala-library", "scala-reflect", "scala-xml").exists(dependency.data.getName.startsWith)
  }
}
