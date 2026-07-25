ThisBuild / scalaVersion := "2.13.16"
ThisBuild / version      := "0.1.0"
ThisBuild / organization := "risc.framework"

val chiselVersion = "7.0.0"

ThisBuild / scalacOptions ++= Seq(
  "-language:reflectiveCalls",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Xcheckinit",
  "-Ymacro-annotations"
)

lazy val arch = (project in file("."))
  .settings(
    name := "arch",
    // chisel/vopts
    libraryDependencies ++= Seq(
      "risc.framework"    %% "vutils" % "0.1.0",
      "risc.framework"    %% "vamba"  % "0.1.0",
      "risc.framework"    %% "vcache" % "0.1.0",
      "org.chipsalliance" %% "chisel" % chiselVersion
    ),
    addCompilerPlugin(
      "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
    ),
  )
