import sbt._
import sbt.Keys._
import sbtassembly.Plugin._
import AssemblyKeys._

object InnoxyzdocBuild extends Build {

  lazy val innoxyzdoc = Project(
    id = "innodoc",
    base = file("."),
    settings = Project.defaultSettings ++
	assemblySettings++
	Seq(
      name := "InnoXYZDoc",
      organization := "com.innoxyz",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.10.1",
      unmanagedJars in Compile += Attributed.blank(file(System.getenv("JAVA_HOME"))/"lib"/"tools.jar")
      // add other settings here
    )
  )
}
