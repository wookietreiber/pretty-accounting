enablePlugins(BuildInfoPlugin)
enablePlugins(GitVersioning)

name := "pretty-accounting"

scalaVersion := "2.12.2"

crossScalaVersions := Seq("2.11.11", "2.12.2")

buildInfoKeys := Seq[BuildInfoKey](name, version)

buildInfoPackage := "grid"

libraryDependencies ++= Seq (
  "com.itextpdf"             %  "itextpdf"      % "5.5.11",
  "com.github.wookietreiber" %% "scala-chart"   % "0.5.1",
  "com.github.nscala-time"   %% "nscala-time"   % "2.16.0",
  "co.fs2"                   %% "fs2-core"      % "0.9.7",
  "co.fs2"                   %% "fs2-io"        % "0.9.7",
  "co.fs2"                   %% "fs2-cats"      % "0.3.0",
  "com.github.scopt"         %% "scopt"         % "3.6.0",
  "org.specs2"               %% "specs2-core"   % "3.9.4" % "test"
)

initialCommands in Compile += """
  import cats._
  import cats.data._
  import cats.implicits._
  import fs2._
  import fs2.interop.cats._
  import scala.math._
  import com.github.nscala_time.time.Imports._
  import scalax.chart._
  import scalax.chart.api._
"""

initialCommands in (Compile, consoleQuick) := (initialCommands in Compile).value

initialCommands in Compile in console += """
  import grid._
  import grid.Accounting._
"""

fork in run := true

// -------------------------------------------------------------------------------------------------
// scaladoc
// -------------------------------------------------------------------------------------------------

scalacOptions in (Compile, doc) += "-groups"

// -------------------------------------------------------------------------------------------------
// scalastyle integration
// -------------------------------------------------------------------------------------------------

scalastyleConfig := file(".scalastyle-config.xml")

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.autoImport.scalastyle.in(Compile).toTask("").value

(compile in Compile) := ((compile in Compile) dependsOn compileScalastyle).value

// -------------------------------------------------------------------------------------------------
// scripts / install
// -------------------------------------------------------------------------------------------------

val prefix = settingKey[String]("Installation prefix.")

val scriptsDir = settingKey[File]("Target path to scripts.")

val scripts = taskKey[Unit]("Creates the scripts.")

val install = taskKey[Unit]("Install to prefix.")

prefix := sys.env.getOrElse("PREFIX", "/usr/local")

scriptsDir := target.value / "scripts"

scripts := {
  val dir = scriptsDir.value
  if (!dir.exists) dir.mkdir()

  val p = prefix.value
  val n = name.value

  def script(clazz: String) =
    s"""|#!/bin/sh
        |java -cp "${p}/share/${n}/${n}.jar" '$clazz' "$$@"
        |""".stripMargin

  (discoveredMainClasses in Compile).value foreach { clazz =>
    val app = clazz.drop(clazz.lastIndexOf(".") + 1).replaceAll("\\$minus", "-")
    val s = dir / app
    IO.write(s, script(clazz))
    s.setExecutable(true)
  }
}

scripts := (scripts dependsOn assembly).value

install := {
  import java.nio.file.{Files, StandardCopyOption}

  val s = (scriptsDir.value * "*").get
  val j = assembly.value
  val p = file(prefix.value)

  val bindir = p / "bin"
  if (!bindir.exists) bindir.mkdirs()

  for (i <- s) {
    val source = i.toPath
    val target = (bindir / s"""pa-${i.name}""").toPath
    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
  }

  IO.copy(Seq(j -> p / "share" / name.value / (name.value + ".jar")), overwrite = true)
}

install := (install dependsOn scripts).value
