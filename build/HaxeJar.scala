import java.net.URL
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
  val IncludeStdR = """[^/]+\.hx|(haxe|js)/.*""".r

  val ResultBinHaxe = "bin/linux64/haxe"

  val BaseName = s"haxe-$haxeVer/"
  val BaseStdName = BaseName + "std/"
  val HaxeName = BaseName + "haxe"

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
    }

    println("Unpacking from archive")
    var entry: TarArchiveEntry = tarInput.getNextTarEntry
    while (entry != null) {
      if (!entry.isDirectory) {
        entry.getName match {
          // Save std library
          case n if n.startsWith(BaseStdName) =>
            val subName = entry.getName.substring(BaseStdName.length)
            if (IncludeStdR.pattern.matcher(subName).matches()) {
              saveFile(entry, subName)
            }

          // Save binaries
          case HaxeName =>
            saveFile(entry, ResultBinHaxe)

          case _ => // ignore
        }
      }
      entry = tarInput.getNextTarEntry
    }
    tarInput.close()
  }
}
