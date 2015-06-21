package com.learningobjects.sbt.xmlbeans

import org.apache.xmlbeans.impl.common.XmlErrorPrinter
import org.apache.xmlbeans.impl.tool.SchemaCompiler
import org.apache.xmlbeans.impl.tool.SchemaCompiler.Parameters

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

import scala.language.postfixOps

/**
 * Compile XSDs to JavaBeans. Do not use. Try out scalaxb instead.
 */
object SbtXmlBeansPlugin extends AutoPlugin {

  override def requires = JvmPlugin

  override def trigger = allRequirements

  object autoImport {
    val xmlbeans = TaskKey[Seq[File]]("xmlbeans", "Generate Java Bean source from xsd files.")
    //TODO: Enable use to define the which version of xmlbeans to run by using a forked process.
    //val xmlbeansLibs = SettingKey[Seq[ModuleID]]("xmlbeans-libs", "The apache xmlbeans module this project will use.")
    //val xmlbeansCmd = SettingKey[Boolean]("xmlbeans-cmd", "The command line arguments used to launch the xmlbeans compiler.")
    val xmlbeansDebug = SettingKey[Boolean]("xmlbeans-debug", "Enabled debug output.")
    val xmlbeansVerbose = SettingKey[Boolean]("xmlbeans-verbose", "Enabled verbose output.")
    val xmlbeansQuiet = SettingKey[Boolean]("xmlbeans-quiet", "Enabled quiet output.")
    val xmlbeansSourceVersion = SettingKey[String]("xmlbeans-source-version", "Java source level for generated xmlbean sources.")
  }

  import autoImport._

  val XmlBeans = config("xmlbeans").hide

  override lazy val projectSettings = Seq[Def.Setting[_]](
    ivyConfigurations += XmlBeans,
//    xmlbeansLibs := Seq(
//      "org.apache.xmlbeans" % "xmlbeans" % "2.4.0"
//    ),
    xmlbeansDebug := false,
    xmlbeansVerbose := false,
    xmlbeansQuiet := true,
    xmlbeansSourceVersion := "1.5"
    //libraryDependencies <++= (xmlbeansLibs)(_.map(_ % XmlBeans.name))
  ) ++ inConfig(Compile)(unscopedSettings)

  private def unscopedSettings = Seq[Def.Setting[_]](
    xmlbeans <<= (sourceDirectory, sourceManaged, classDirectory, internalDependencyClasspath,
      xmlbeansDebug, xmlbeansVerbose, xmlbeansQuiet, xmlbeansSourceVersion, streams) map {
      cachedGenerateSources
    },
    sourceGenerators <+= xmlbeans
  )

  def cachedGenerateSources(sourceDir: File, srcOut: File, classOut: File, classpath: Classpath, debug: Boolean, verbose: Boolean, quiet: Boolean, javaSource: String, streams: TaskStreams): Seq[File] = {
    FileFunction.cached(streams.cacheDirectory)(FilesInfo.lastModified, FilesInfo.exists) {
      (inputFiles, modifiedOutputFile) => generateSources(inputFiles, sourceDir, srcOut, classOut, classpath, debug, verbose, quiet,javaSource).toSet
    } {
      ((sourceDir ** "*.xsd") +++ (sourceDir ** "*.xsdconfig") +++ (sourceDir ** "*.java")).get.toSet
    }.toSeq //TODO: toSet/toSeq conversions are awkward.
  }

  def generateSources(sourceReport: ChangeReport[File], sourceDir: File, srcOut: File, classOut: File, classpath: Classpath, debug: Boolean, verbose: Boolean, quiet: Boolean, javaSource: String): Seq[File] = {
    sourceDir.mkdirs()
    srcOut.mkdirs()

    val params = new Parameters

    val xsds = (sourceDir / "xsd") ** "*.xsd"
    val xsdconfigs = (sourceDir / "xsdconfig") ** "*.xsdconfig"

    val javaInput = (sourceDir / "java") ** "*.java"

    val cp = (classpath map {
      _.data
    }).toArray

    val errorListener = new XmlErrorPrinter(verbose, classOut.toURI)

    params setXsdFiles xsds.get.toArray
    params setWsdlFiles Array.empty[File]
    params setJavaFiles javaInput.get.toArray
    params setConfigFiles xsdconfigs.get.toArray
    params setClasspath cp
    params setSrcDir srcOut
    params setClassesDir classOut
    params setJavaSource javaSource
    params setNojavac true
    params setDebug debug
    params setVerbose verbose
    params setQuiet quiet
    params setDownload false
    params setNoVDoc false
    params setNoUpa false
    params setNoPvr false
    params setNoExt false
    params setErrorListener errorListener
    params setIncrementalSrcGen true

    val success = SchemaCompiler.compile(params)
    if (success)
      srcOut ** "*.java" get
    else
      Seq.empty[File]
  }
}
