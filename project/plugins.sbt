val scalaNativeVersion =
  Option(System.getenv("SCALANATIVE_VERSION")).getOrElse("0.5.5")

addSbtPlugin("com.github.sbt" % "sbt-github-actions" % "0.24.0")
addSbtPlugin("com.github.sbt" % "sbt-ci-release"    % "1.6.1")
addSbtPlugin("org.scala-js" % "sbt-scalajs"  % "1.16.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "3.2.1")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % scalaNativeVersion)
