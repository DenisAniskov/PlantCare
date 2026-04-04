package com.example.plantcare.desktop

import java.io.File
import javax.imageio.ImageIO
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.ImageFormats

object IconGenerator {
    private val MIPMAP_PATHS = listOf(
        "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp",
        "app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp",
        "app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp",
        "app/src/main/res/mipmap-hdpi/ic_launcher_round.webp",
        "app/src/main/res/mipmap-mdpi/ic_launcher_round.webp"
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val cwd = File(System.getProperty("user.dir"))
        val bases = listOf(cwd, File(cwd, "..").canonicalFile, File(cwd, "../..").canonicalFile)
        val candidates = bases.flatMap { base -> MIPMAP_PATHS.map { File(base, it) } }
        val src = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Android launcher WEBP not found. Cwd: ${cwd.absolutePath}")
        // Ensure plugins are available
        ImageIO.scanForPlugins()
        val img = ImageIO.read(src) ?: throw IllegalStateException("Failed to decode ${src.name}")
        val projectRoot = src.absoluteFile.resolve("../../../../../../").canonicalFile
        val outDir = File(projectRoot, "desktop/src/main/resources").apply { mkdirs() }
        val outFile = File(outDir, "icon.ico")
        Imaging.writeImage(img, outFile, ImageFormats.ICO)
        println("Generated icon: ${outFile.absolutePath}")
    }
}
