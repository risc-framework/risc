ThisBuild / scalaVersion := "2.13.16"
ThisBuild / version      := "0.1.0"
ThisBuild / organization := "risc.framework"

val chiselVersion = "7.0.0"
ThisBuild / resolvers += Resolver.file("local-ivy", file(Path.userHome + "/.ivy2/local"))(
  Resolver.ivyStylePatterns
)

ThisBuild / scalacOptions ++= Seq(
  "-language:reflectiveCalls",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Xcheckinit",
  "-Ymacro-annotations"
)

lazy val arch = (project in file("arch"))
  .settings(
    name := "arch",
    // chisel/vopts
    libraryDependencies ++= Seq(
      "risc.framework"    %% "vutils" % "0.1.0",
      "risc.framework"    %% "vamba"  % "0.1.0",
      "risc.framework"    %% "vcache" % "0.1.0",
      "org.chipsalliance" %% "chisel" % chiselVersion
    ),
    Compile / unmanagedSourceDirectories += baseDirectory.value,
    addCompilerPlugin(
      "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
    ),
  )
