import java.net.URL
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path, Paths}
import java.util.zip.GZIPInputStream

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream}
import org.apache.commons.io.{FileUtils, IOUtils}

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
  val DownloadUrl: String = s"https://github.com/HaxeFoundation/haxe/releases/download/$haxeVer/haxe-$haxeVer-linux64.tar.gz"

  val DownloadName = DownloadUrl.substring(DownloadUrl.lastIndexOf('/') + 1)
  val DownloadDir = Paths.get("target/downloads")
  val DownloadToPath = DownloadDir.resolve(DownloadName)

  /** Include only these files from std library */
  val IncludeStdR = """[^/]+\.hx|(haxe|js|neko|sys)/.*""".r

  val ResultBinHaxe = "bin/linux64/haxe"

  val BaseStdR = """haxe[^/]+/std/(.*)$""".r
  val HaxeNameR = """haxe[^/]+/haxe$""".r

  def prepareDirs(): Unit = {
    if (Files.isDirectory(jarDir)) FileUtils.deleteDirectory(jarDir.toFile)
    Files.createDirectories(jarDir)
    Files.createDirectories(DownloadDir)
  }

  def download(): Unit = {
    if (Files.exists(DownloadToPath)) {
      println("Reading downloaded file " + DownloadToPath)
    } else {
      print("Downloading from " + DownloadUrl)
      Files.write(DownloadToPath, IOUtils.toByteArray(new URL(DownloadUrl).openStream()))
      println(" ok")
    }
    val tarInput = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(DownloadToPath)))

    def saveFile(entry: TarArchiveEntry, saveAs: String): Unit = {
      val bytes = IOUtils.toByteArray(tarInput, entry.getSize)
      val path = jarDir.resolve(saveAs)
      Files.createDirectories(path.getParent)
      Files.write(path, bytes)
      if ((entry.getMode & 0x40) != 0) { // 0o100 - execute flag
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"))
      }
    }

    println("Unpacking from archive")
    var libFileCount = 0
    var entry: TarArchiveEntry = tarInput.getNextTarEntry
    while (entry != null) {
      if (!entry.isDirectory) {
        entry.getName match {
          // Save std library
          case BaseStdR(subName) =>
            if (IncludeStdR.pattern.matcher(subName).matches()) {
              var name = subName
              // Workaround for IDEA Scala `sys` bug https://youtrack.jetbrains.com/issue/SCL-12839
              if (name.startsWith("sys/")) name = "sys_" + name.substring(3)

              saveFile(entry, name)
              libFileCount += 1
            }

          // Save binaries
          case HaxeNameR() =>
            saveFile(entry, ResultBinHaxe)

          case _ => // ignore
        }
      }
      entry = tarInput.getNextTarEntry
    }
    tarInput.close()

    println("Library files packaged: " + libFileCount)
  }
}
