import com.typesafe.tools.mima.core._
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import sbtcrossproject.CrossType

val Scala212 = "2.12.19"
val Scala213 = "2.13.15"

ThisBuild / crossScalaVersions := Seq(Scala212, Scala213)
ThisBuild / scalaVersion := Scala212

ThisBuild / githubWorkflowPublishTargetBranches := Seq()

ThisBuild / githubWorkflowBuildPreamble +=
  WorkflowStep.Run(
    List("sudo apt install clang libunwind-dev libgc-dev libre2-dev"),
    name = Some("Setup scala native dependencies"),
 )

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(
    List("test", "test:doc", "mimaReportBinaryIssues"),
    name = Some("Run main build")
  ),

  WorkflowStep.Sbt(
    List("coreNative/test", "examplesNative/test"),
    name = Some("Run native build"),
  )
)

ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches :=
  Seq(
    RefPredicate.Equals(Ref.Branch("master")),
    RefPredicate.StartsWith(Ref.Tag("v"))
  )

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    name = Some("Publish artifacts"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )
)

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))

val scalatestVersion = "3.2.19"

lazy val nativeCommonSettings = Def.settings(
  scalaVersion := Scala212,
  crossScalaVersions := Seq(Scala212, Scala213),
  //nativeLinkStubs := true
)

lazy val commonSettings = Seq(
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
  organization := "io.github.leviysoft",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-language:higherKinds",
    "-language:implicitConversions"
  ),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v >= 13 =>
        Seq("-Ymacro-annotations")
      case _ =>
        Nil
    }
  },
  Compile / doc / scalacOptions ~= { _ filterNot { o => o == "-Ywarn-unused-import" || o == "-Xfatal-warnings" } },
  Compile / console / scalacOptions ~= { _ filterNot { o => o == "-Ywarn-unused-import" || o == "-Xfatal-warnings" } },
  Test / console / scalacOptions := (Compile / console / scalacOptions).value,
  resolvers ++= Seq(
    Resolver.sonatypeOssRepos("releases"),
    Resolver.sonatypeOssRepos("snapshots")
  ).flatten,
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v <= 12 =>
        Seq(
          compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
        )
      case _ =>
        // if scala 2.13.0-M4 or later, macro annotations merged into scala-reflect
        // https://github.com/scala/scala/pull/6606
        Nil
    }
  },
  licenses += ("Three-clause BSD-style", url("https://github.com/leviysoft/simulacrum/blob/master/LICENSE")),
  homepage := Some(url("https://github.com/leviysoft/simulacrum")),
  developers := List(
    Developer(
      "mpilquist",
      "Michael Pilquist",
      "-",
      url("http://github.com/mpilquist")
    ),
    Developer(
      "danslapman",
      "Daniil Smirnov",
      "danslapman@gmail.com",
      url("https://github.com/danslapman")
    )
  ),
  Test / compile / wartremoverErrors ++= Seq(
    Wart.ExplicitImplicitTypes,
    Wart.ImplicitConversion)
)

lazy val root = project.in(file("."))
  .settings(commonSettings: _*)
  .settings(crossScalaVersions := Seq(Scala212))
  .settings(noPublishSettings: _*)
  .aggregate(coreJVM, examplesJVM, coreJS, examplesJS)

ThisBuild / mimaFailOnNoPrevious := false

def previousVersion(scalaVersion: String, currentVersion: String): Option[String] = {
  if (scalaVersion == "2.13.0")
    None
  else {
    val Version = """(\d+)\.(\d+)\.(\d+).*""".r
    val Version(x, y, z) = currentVersion
    if (z == "0") None
    else Some(s"$x.$y.${z.toInt - 1}")
  }
}

lazy val core = crossProject(JSPlatform, JVMPlatform, NativePlatform).crossType(CrossType.Pure)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "simulacrum",
    Test / scalacOptions += "-Yno-imports"
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
      "org.scalatest" %%% "scalatest" % scalatestVersion % "test"
    )
  )
  .nativeSettings(
    nativeCommonSettings
  )
  .platformsSettings(JSPlatform, NativePlatform)(
    Test / unmanagedSources / excludeFilter := "jvm.scala"
  )
  .jvmSettings(
    mimaPreviousArtifacts := previousVersion(scalaVersion.value, version.value).map { pv =>
      organization.value % ("simulacrum" + "_" + scalaBinaryVersion.value) % pv
    }.toSet
  )

lazy val coreJVM = core.jvm
lazy val coreJS = core.js
lazy val coreNative = core.native

lazy val examples = crossProject(JSPlatform, JVMPlatform, NativePlatform).crossType(CrossType.Pure)
  .dependsOn(core % "provided")
  .settings(commonSettings: _*)
  .settings(moduleName := "simulacrum-examples")
  .settings(
    libraryDependencies += "org.scalatest" %%% "scalatest" % scalatestVersion % "test"
  )
  .settings(noPublishSettings: _*)
  .platformsSettings(JVMPlatform, JSPlatform)(
    libraryDependencies += "com.chuusai" %%% "shapeless" % "2.3.7" % "test"
  )
  .nativeSettings(
    nativeCommonSettings
  )

lazy val examplesJVM = examples.jvm
lazy val examplesJS = examples.js
lazy val examplesNative = examples.native

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  publish / skip := true
)
