name := "haxe-jar"
organization := "com.github.citrum"

version := "3.4.2"

scalaVersion := "2.12.3"

crossPaths := false // Turn off scala versions

unmanagedSourceDirectories in Compile := Seq(baseDirectory.value / "build")

libraryDependencies += "org.apache.commons" % "commons-compress" % "1.14" % "provided"
libraryDependencies += "commons-io" % "commons-io" % "2.5" % "provided"


lazy val haxeJarDirectory = taskKey[File]("Directory for building jar")
haxeJarDirectory := target.value / "jar"

def runScala(classPath: Seq[File], className: String, arguments: Seq[String] = Nil) {
  val ret: Int = new Fork("java", Some(className)).apply(
    ForkOptions(
      runJVMOptions = Seq("-cp", classPath.mkString(":")),
      outputStrategy = Some(StdoutOutput)),
    arguments)
  if (ret != 0) sys.error("Execution " + className + " ends with error")
}

lazy val haxeJar = taskKey[Seq[File]]("haxe-jar")
haxeJar := {
  (compile in Compile).value
  val baseDir = baseDirectory.value
  val classPath = Seq(baseDir, (classDirectory in Runtime).value) ++
    (dependencyClasspath in Compile).value.files

  val outDir = (resourceManaged in Compile).value

  runScala(classPath, "HaxeJar", Seq(version.value, outDir.toString))

  outDir.***.filter(_.isFile).get
}

resourceGenerators in Compile += haxeJar.taskValue
mappings in (Compile, packageBin) ~= (_.filterNot(_._1.getName.endsWith(".class")))

// Disable javadoc, source generation
publishArtifact in (Compile, packageDoc) := false
publishArtifact in (Compile, packageSrc) := false

artifactClassifier := Some("haxe")


// Deploy settings
startYear := Some(2017)
homepage := Some(url("https://github.com/citrum/haxe-jar"))
licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
bintrayVcsUrl := Some("https://github.com/citrum/haxe-jar")
bintrayOrganization := Some("citrum")
