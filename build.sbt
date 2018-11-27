organization := "sk.softec.dcos"

name := "marathon-security-validator"

resolvers += "Mesosphere Public Repo" at "http://downloads.mesosphere.io/maven"

scalacOptions += "-target:jvm-1.8"

scalaVersion := "2.12.4"

version := "1.6.535"

libraryDependencies ++= Seq(
  "mesosphere.marathon" %% "marathon" % version.value % Provided exclude("javax.ws.rs", "javax.ws.rs-api"),
  "mesosphere.marathon" %% "plugin-interface" % version.value % Provided,
  "org.slf4j" % "slf4j-api" % Version.slf4j % Provided,
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

// assemblyOptions.includeScala cannot be used, it will exclude scala-compiler
assemblyExcludedJars in assembly := {
  (fullClasspath in assembly).value.filter { dependency =>
    Seq("scala-library", "scala-reflect", "scala-xml").exists(dependency.data.getName.startsWith)
  }
}
