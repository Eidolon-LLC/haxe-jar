import java.net.URL
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{GZIPInputStream, ZipEntry, ZipInputStream}

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream}
import org.apache.commons.io.{FileUtils, IOUtils}

import scala.util.matching.Regex

object HaxeJar {
  def main(args: Array[String]): Unit = {
    require(args.length == 2, "Need haxe version, and output directory")
    val haxeVer: String = args(0)
    val jarDir: String = args(1)

    val haxe = new HaxeJar(haxeVer, Paths.get(jarDir))
    haxe.prepareDirs()
    haxe.download()
  }
}

class HaxeJar(haxeVer: String, jarDir: Path) {
  val DownloadDir = Paths.get("target/downloads")
  val Downloads = Vector(
    Download(s"https://github.com/HaxeFoundation/haxe/releases/download/$haxeVer/haxe-$haxeVer-linux64.tar.gz", """haxe[^/]+/haxe$""".r, "bin/linux64", extractLibrary = true),
    Download(s"https://github.com/HaxeFoundation/haxe/releases/download/$haxeVer/haxe-$haxeVer-osx.tar.gz", """haxe[^/]+/haxe$""".r, "bin/osx"),
    Download(s"https://github.com/HaxeFoundation/haxe/releases/download/$haxeVer/haxe-$haxeVer-win.zip", """haxe[^/]+/(?:haxe\.exe|[^/]+\.dll)$""".r, "bin/win"),
    Download(s"https://github.com/HaxeFoundation/haxe/releases/download/$haxeVer/haxe-$haxeVer-win64.zip", """haxe[^/]+/(?:haxe\.exe|[^/]+\.dll)$""".r, "bin/win64"),
  )
  val MainDownload = Downloads(0)

  /** Include only these files from std library */
  val IncludeStdR = """[^/]+\.hx|(haxe|js|neko|sys)/.*""".r

  val BaseStdR = """haxe[^/]+/std/(.*)$""".r

  def prepareDirs(): Unit = {
    if (Files.isDirectory(jarDir)) FileUtils.deleteDirectory(jarDir.toFile)
    Files.createDirectories(jarDir)
    Files.createDirectories(DownloadDir)
  }

  case class Download(url: String,
                      binR: Regex,
                      binExtractTo: String,
                      extractLibrary: Boolean = false) {
    val downloadName = getNameFrom(url)
    val downloadToPath = DownloadDir.resolve(downloadName)

    private def getNameFrom(fullName: String): String = fullName.substring(fullName.lastIndexOf('/') + 1)

    def downloadOne(): Unit = {
      if (Files.exists(downloadToPath)) {
        println("Reading downloaded file " + downloadToPath)
      } else {
        println("Downloading from " + url)
        Files.write(downloadToPath, IOUtils.toByteArray(new URL(url).openStream()))
        println(" ok")
      }
    }


    def extract(): Unit = {
      if (downloadName.endsWith(".tar.gz")) extractTarGz()
      else if (downloadName.endsWith(".zip")) extractZip()
      else throw new RuntimeException("Cannot extract " + downloadName)
    }


    def extractTarGz(): Unit = {
      val tarInput = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(downloadToPath)))

      def saveFile(entry: TarArchiveEntry, saveAs: String): Unit = {
        val bytes = IOUtils.toByteArray(tarInput, entry.getSize)
        val path = jarDir.resolve(saveAs)
        Files.createDirectories(path.getParent)
        Files.write(path, bytes)
        if ((entry.getMode & 0x40) != 0) { // 0o100 - execute flag
          Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"))
        }
      }

      println(s"Unpacking from archive $downloadName")
      var libFileCount = 0
      var binFileCount = 0
      var entry: TarArchiveEntry = tarInput.getNextTarEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          entry.getName match {
            // Save std library
            case BaseStdR(subName) if extractLibrary =>
              if (IncludeStdR.pattern.matcher(subName).matches()) {
                var name = subName
                // Workaround for IDEA Scala `sys` bug https://youtrack.jetbrains.com/issue/SCL-12839
                if (name.startsWith("sys/")) name = "sys_" + name.substring(3)

                saveFile(entry, name)
                libFileCount += 1
              }

            // Save binaries
            case `binR`() =>
              saveFile(entry, binExtractTo + "/" + getNameFrom(entry.getName))
              binFileCount += 1

            case _ => // ignore
          }
        }
        entry = tarInput.getNextTarEntry
      }
      tarInput.close()

      println(s"Library files packaged: $libFileCount, binaries packages: $binFileCount")
    }


    def extractZip(): Unit = {
      println(s"Unpacking from archive $downloadName")

      var binFileCount = 0
      val zis = new ZipInputStream(Files.newInputStream(downloadToPath))

      def saveFile(entry: ZipEntry, saveAs: String): Unit = {
        val bytes = IOUtils.toByteArray(zis)
        val path = jarDir.resolve(saveAs)
        Files.createDirectories(path.getParent)
        Files.write(path, bytes)
      }

      try {
        var entry: ZipEntry = zis.getNextEntry
        while (entry != null) {
          if (!entry.isDirectory) {
            entry.getName match {
              case `binR`() =>
                saveFile(entry, binExtractTo + "/" + getNameFrom(entry.getName))
                binFileCount += 1

              case _ => // ignore
            }
          }
          entry = zis.getNextEntry
        }
        zis.closeEntry()
      } finally {
        zis.close()
      }

      println(s"Binaries packages: $binFileCount")
    }
  }


  def download(): Unit = {
    Downloads.par.foreach(_.downloadOne())
    Downloads.foreach(_.extract())
  }
}
