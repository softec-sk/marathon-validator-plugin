organization := "sk.softec.dcos"

name := "marathon-security-validator"

resolvers += "Mesosphere Public Repo" at "http://downloads.mesosphere.io/maven"

scalacOptions += "-target:jvm-1.8"

scalaVersion := "2.12.4"

version := "1.6.535"

libraryDependencies ++= Seq(
  "mesosphere.marathon" %% "plugin-interface" % version.value % Provided,
  "org.slf4j" % "slf4j-api" % Version.slf4j % Provided
)

assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)
