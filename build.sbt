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
    name                      := "arch",
    // chisel/vopts
    libraryDependencies ++= Seq(
      "risc.framework"    %% "vutils" % "0.1.0",
      "risc.framework"    %% "vamba"  % "0.1.0",
      "risc.framework"    %% "vcache" % "0.1.0",
      "dashygo097"        %% "math"   % "0.1.0",
      "org.chipsalliance" %% "chisel" % chiselVersion
    ),
    Compile / unmanagedSourceDirectories += baseDirectory.value,
    addCompilerPlugin(
      "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
    ),
    // protobuf
    Compile / PB.protoSources := Seq(baseDirectory.value / ".." / "protos"),
    Compile / PB.targets      := Seq(
      scalapb.gen(flatPackage = true) -> (Compile / sourceManaged).value / "scalapb"
    ),
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime"      % scalapb.compiler.Version.scalapbVersion % "protobuf",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "com.thesamet.scalapb" %% "scalapb-json4s"       % "0.12.1"
    )
  )
