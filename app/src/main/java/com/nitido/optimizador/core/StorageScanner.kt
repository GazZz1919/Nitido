package com.nitido.optimizador.core

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.Locale

/** Tipos de elementos que encuentra el análisis de almacenamiento. */
enum class Category(val title: String, val subtitle: String) {
    TEMP("Temporales y registros", "Archivos .tmp, .log y restos que dejan las apps"),
    THUMBNAILS("Miniaturas en caché", "Se regeneran solas al abrir la galería"),
    EMPTY_FOLDERS("Carpetas vacías", "Carpetas sin ningún contenido"),
    APKS("Instaladores APK", "Se marcan solos los de apps que ya tienes instaladas"),
    DUPLICATES("Archivos duplicados", "Copias idénticas: se conserva la más antigua de cada grupo"),
    LARGE("Archivos grandes", "Los archivos que más espacio ocupan"),
    OLD_DOWNLOADS("Descargas antiguas", "Archivos de Descargas sin modificar desde hace tiempo");

    /** Basura que se puede borrar sin revisar: se regenera o no contiene datos. */
    val isSafeJunk: Boolean get() = this == TEMP || this == THUMBNAILS || this == EMPTY_FOLDERS
}

data class ScanItem(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val category: Category,
    val preselect: Boolean = false,
    val note: String? = null,
    val group: Int = -1,
    val keep: Boolean = false,
    val isDir: Boolean = false,
)

data class ScanResult(
    val byCategory: Map<Category, List<ScanItem>>,
    val filesScanned: Int,
    val durationMs: Long,
) {
    fun items(category: Category): List<ScanItem> = byCategory[category].orEmpty()
    fun size(category: Category): Long = items(category).sumOf { it.size }
    val all: List<ScanItem> get() = Category.entries.flatMap { items(it) }
    val totalBytes: Long get() = all.sumOf { it.size }
    val isEmpty: Boolean get() = byCategory.values.all { it.isEmpty() }
    fun duplicateGroups(): Int = items(Category.DUPLICATES).map { it.group }.distinct().size

    /** Quita los elementos borrados; los grupos de duplicados con un solo archivo desaparecen. */
    fun without(paths: Set<String>): ScanResult {
        if (paths.isEmpty()) return this
        val updated = byCategory.mapValues { (category, list) ->
            val remaining = list.filterNot { it.path in paths }
            if (category == Category.DUPLICATES) {
                val counts = remaining.groupingBy { it.group }.eachCount()
                remaining.filter { (counts[it.group] ?: 0) > 1 }
            } else {
                remaining
            }
        }
        return copy(byCategory = updated)
    }
}

enum class ScanPhase(val label: String) {
    FILES("Escaneando archivos"),
    DUPLICATES("Buscando duplicados"),
}

data class ScanProgress(val files: Int, val phase: ScanPhase, val detail: String)

data class ScanConfig(
    val largeFileBytes: Long = 100_000_000L,
    val oldDownloadDays: Int = 90,
    val minDuplicateBytes: Long = 1_000_000L,
)

data class DeleteResult(
    val freedBytes: Long,
    val deleted: Int,
    val failed: Int,
    val deletedPaths: Set<String>,
)

/** Ruta legible de la carpeta que contiene un archivo. */
object StoragePaths {
    @Suppress("DEPRECATION")
    val root: String by lazy { Environment.getExternalStorageDirectory().absolutePath }

    fun folderOf(path: String): String {
        val parent = path.substringBeforeLast('/', "")
        return if (parent.startsWith(root)) {
            parent.removePrefix(root).trimStart('/').ifEmpty { "Almacenamiento interno" }
        } else {
            parent
        }
    }
}

/**
 * Analiza el almacenamiento compartido (lo que ves en el gestor de archivos).
 * Nunca entra en Android/data ni Android/obb, ni en carpetas ocultas, y nunca borra
 * nada sin que el usuario lo haya seleccionado (salvo la basura segura si él lo activa).
 */
class StorageScanner(private val context: Context) {

    private val rootDir: File
        get() = File(StoragePaths.root)

    private val protectedTopLevel = setOf(
        "android", "dcim", "pictures", "download", "documents", "music", "movies",
        "alarms", "notifications", "podcasts", "ringtones", "audiobooks", "recordings",
    )
    private val tempExtensions = setOf("tmp", "temp", "log", "dmp", "chk")
    private val tempNames = setOf("thumbs.db", ".ds_store", "desktop.ini")
    private val apkExtensions = setOf("apk", "apks", "xapk", "apkm")

    private data class Candidate(
        val path: String,
        val name: String,
        val size: Long,
        val modified: Long,
        val inDownloads: Boolean,
    )

    /** Análisis completo: basura, APK, duplicados, archivos grandes y descargas antiguas. */
    suspend fun scan(
        config: ScanConfig = ScanConfig(),
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val job = coroutineContext[Job]
        val root = rootDir
        val rootPath = root.absolutePath
        val androidDir = "$rootPath/Android"
        val downloadsPrefix = File(root, Environment.DIRECTORY_DOWNLOADS).absolutePath + "/"
        val oldCutoff = System.currentTimeMillis() - config.oldDownloadDays * 86_400_000L
        val pm = context.packageManager

        val temp = ArrayList<ScanItem>()
        val thumbs = ArrayList<ScanItem>()
        val empty = ArrayList<ScanItem>()
        val apks = ArrayList<ScanItem>()
        val candidates = ArrayList<Candidate>()
        val childCounts = ArrayDeque<IntArray>()
        var files = 0

        try {
            Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (job?.isActive == false) return FileVisitResult.TERMINATE
                    childCounts.lastOrNull()?.let { it[0]++ }
                    val path = dir.toString()
                    if (path != rootPath) {
                        val name = dir.fileName?.toString().orEmpty()
                        val hidden = name.startsWith(".") && !name.equals(".thumbnails", ignoreCase = true)
                        val privateAppData = path.equals("$androidDir/data", ignoreCase = true) ||
                            path.equals("$androidDir/obb", ignoreCase = true)
                        if (hidden || privateAppData) return FileVisitResult.SKIP_SUBTREE
                    }
                    childCounts.addLast(IntArray(1))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    childCounts.lastOrNull()?.let { it[0]++ }
                    if (!attrs.isRegularFile) return FileVisitResult.CONTINUE
                    files++
                    val path = file.toString()
                    val name = file.fileName?.toString().orEmpty()
                    val lower = name.lowercase(Locale.ROOT)
                    val ext = lower.substringAfterLast('.', "")
                    val size = attrs.size()
                    val modified = attrs.lastModifiedTime().toMillis()
                    val insideAndroid = path.startsWith("$androidDir/")

                    when {
                        lower == ".nomedia" -> Unit
                        insideAndroid -> {
                            // Android/media (p. ej. WhatsApp): solo se ofrece para revisar, nunca como basura.
                            if (!lower.startsWith(".") && size >= config.minDuplicateBytes) {
                                candidates += Candidate(path, name, size, modified, false)
                            }
                        }
                        path.contains("/.thumbnails/", ignoreCase = true) ->
                            thumbs += ScanItem(path, name, size, modified, Category.THUMBNAILS, preselect = true)
                        ext in tempExtensions || lower in tempNames || lower.startsWith("._") ->
                            temp += ScanItem(path, name, size, modified, Category.TEMP, preselect = true)
                        ext in apkExtensions ->
                            apks += apkItem(pm, path, name, size, modified, ext)
                        lower.startsWith(".") -> Unit // archivos ocultos: no se tocan
                        else -> {
                            val inDownloads = path.startsWith(downloadsPrefix)
                            if (size >= config.minDuplicateBytes || inDownloads) {
                                candidates += Candidate(path, name, size, modified, inDownloads)
                            }
                        }
                    }
                    if (files % 300 == 0) {
                        val folder = StoragePaths.folderOf(path)
                        onProgress(ScanProgress(files, ScanPhase.FILES, folder))
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    childCounts.lastOrNull()?.let { it[0]++ }
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    val count = if (childCounts.isEmpty()) 1 else childCounts.removeLast()[0]
                    val path = dir.toString()
                    if (exc == null && count == 0 && isRemovableEmptyDir(path, rootPath)) {
                        val modified = File(path).lastModified()
                        empty += ScanItem(
                            path = path,
                            name = dir.fileName?.toString().orEmpty(),
                            size = 0L,
                            modified = modified,
                            category = Category.EMPTY_FOLDERS,
                            // Las carpetas recién creadas pueden ser intencionadas: no se marcan.
                            preselect = modified < recentFolderCutoff(),
                            note = if (modified >= recentFolderCutoff()) "Creada hace poco" else null,
                            isDir = true,
                        )
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: IOException) {
            // Sin acceso a la raíz: se devuelve lo encontrado hasta ahora.
        }
        ensureActive()

        // --- Duplicados: mismo tamaño -> mismo hash parcial -> mismo hash completo ---
        onProgress(ScanProgress(files, ScanPhase.DUPLICATES, "Comparando archivos del mismo tamaño"))
        val duplicates = ArrayList<ScanItem>()
        val duplicatePaths = HashSet<String>()
        val sameSizeGroups = candidates
            .filter { it.size >= config.minDuplicateBytes }
            .groupBy { it.size }
            .values
            .filter { it.size > 1 }
        val toCheck = sameSizeGroups.sumOf { it.size }
        var checked = 0
        var groupId = 0
        for (group in sameSizeGroups) {
            ensureActive()
            val byPartial = group.groupBy { partialHash(it.path, it.size) }
            for ((partial, samePartial) in byPartial) {
                if (partial == null || samePartial.size < 2) continue
                val byFull = samePartial.groupBy { fullHash(it.path, job) }
                for ((full, same) in byFull) {
                    if (full == null || same.size < 2) continue
                    same.sortedBy { it.modified }.forEachIndexed { index, c ->
                        duplicates += ScanItem(
                            path = c.path,
                            name = c.name,
                            size = c.size,
                            modified = c.modified,
                            category = Category.DUPLICATES,
                            note = if (index == 0) "Se conserva (el más antiguo)" else "Copia",
                            group = groupId,
                            keep = index == 0,
                        )
                        duplicatePaths += c.path
                    }
                    groupId++
                }
            }
            checked += group.size
            onProgress(ScanProgress(files, ScanPhase.DUPLICATES, "$checked de $toCheck archivos comparados"))
        }

        // --- Archivos grandes y descargas antiguas (cada archivo aparece en una sola categoría) ---
        val large = candidates
            .filter { it.size >= config.largeFileBytes && it.path !in duplicatePaths }
            .sortedByDescending { it.size }
            .map { ScanItem(it.path, it.name, it.size, it.modified, Category.LARGE, note = Fmt.date(it.modified)) }
        val largePaths = large.mapTo(HashSet()) { it.path }
        val old = candidates
            .filter {
                it.inDownloads && it.modified in 1L until oldCutoff &&
                    it.path !in duplicatePaths && it.path !in largePaths
            }
            .sortedBy { it.modified }
            .map {
                ScanItem(it.path, it.name, it.size, it.modified, Category.OLD_DOWNLOADS, note = "Modificado: ${Fmt.date(it.modified)}")
            }

        ScanResult(
            byCategory = mapOf(
                Category.TEMP to temp.sortedByDescending { it.size },
                Category.THUMBNAILS to thumbs.sortedByDescending { it.size },
                Category.EMPTY_FOLDERS to empty.sortedBy { it.path },
                Category.APKS to apks.sortedByDescending { it.size },
                Category.DUPLICATES to duplicates,
                Category.LARGE to large,
                Category.OLD_DOWNLOADS to old,
            ),
            filesScanned = files,
            durationMs = System.currentTimeMillis() - started,
        )
    }

    /**
     * Búsqueda rápida de basura segura en lugares conocidos (miniaturas, temporales y
     * carpetas vacías en los primeros niveles). Tarda menos de un segundo.
     */
    suspend fun quickJunk(): List<ScanItem> = withContext(Dispatchers.IO) {
        val root = rootDir
        val rootPath = root.absolutePath
        val out = ArrayList<ScanItem>()

        for (relative in listOf("DCIM/.thumbnails", "Pictures/.thumbnails", "Movies/.thumbnails", "Music/.thumbnails")) {
            val dir = File(root, relative)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.name != ".nomedia" }
                .forEach { f ->
                    out += ScanItem(f.absolutePath, f.name, f.length(), f.lastModified(), Category.THUMBNAILS, preselect = true)
                }
        }
        ensureActive()

        root.walkTopDown()
            .maxDepth(3)
            .onEnter { dir -> dir == root || isScannableDir(dir, rootPath) }
            .forEach { f ->
                if (f == root) return@forEach
                if (f.isFile) {
                    val lower = f.name.lowercase(Locale.ROOT)
                    val ext = lower.substringAfterLast('.', "")
                    if (ext in tempExtensions || lower in tempNames || lower.startsWith("._")) {
                        out += ScanItem(f.absolutePath, f.name, f.length(), f.lastModified(), Category.TEMP, preselect = true)
                    }
                } else if (
                    f.isDirectory && f.list()?.isEmpty() == true &&
                    isRemovableEmptyDir(f.absolutePath, rootPath) && f.lastModified() < recentFolderCutoff()
                ) {
                    out += ScanItem(f.absolutePath, f.name, 0L, f.lastModified(), Category.EMPTY_FOLDERS, preselect = true, isDir = true)
                }
            }
        out
    }

    /** Borra los elementos indicados. Solo actúa dentro del almacenamiento compartido. */
    suspend fun delete(
        items: Collection<ScanItem>,
        onProgress: (Float) -> Unit = {},
    ): DeleteResult = withContext(Dispatchers.IO) {
        val rootPrefix = rootDir.absolutePath + "/"
        var freed = 0L
        var ok = 0
        var failed = 0
        val deleted = HashSet<String>()
        val (dirs, files) = items.partition { it.isDir }
        val total = items.size.coerceAtLeast(1)

        files.forEachIndexed { index, item ->
            ensureActive()
            if (!item.path.startsWith(rootPrefix)) {
                failed++
                return@forEachIndexed
            }
            val file = File(item.path)
            when {
                !file.exists() -> {
                    ok++
                    deleted += item.path
                }
                file.isFile && file.delete() -> {
                    ok++
                    freed += item.size
                    deleted += item.path
                }
                else -> failed++
            }
            if (index % 25 == 0) onProgress(index.toFloat() / total)
        }

        // Carpetas vacías: de la más profunda a la menos profunda.
        for (item in dirs.sortedByDescending { it.path.length }) {
            val dir = File(item.path)
            val removable = item.path.startsWith(rootPrefix) && dir.isDirectory && dir.list()?.isEmpty() == true
            if (removable && dir.delete()) {
                ok++
                deleted += item.path
            } else if (!dir.exists()) {
                ok++
                deleted += item.path
            } else {
                failed++
            }
        }
        onProgress(1f)

        // Actualiza la galería y demás apps multimedia.
        val removedFiles = files.map { it.path }.filter { it in deleted }
        if (removedFiles.isNotEmpty()) {
            try {
                MediaScannerConnection.scanFile(context, removedFiles.toTypedArray(), null, null)
            } catch (e: Exception) {
                // No es crítico.
            }
        }
        DeleteResult(freed, ok, failed, deleted)
    }

    /** Las carpetas vacías creadas en los últimos 7 días no se borran automáticamente. */
    private fun recentFolderCutoff(): Long = System.currentTimeMillis() - 7 * 86_400_000L

    private fun isScannableDir(dir: File, rootPath: String): Boolean =
        !dir.name.startsWith(".") && !dir.absolutePath.startsWith("$rootPath/Android")

    private fun isRemovableEmptyDir(path: String, rootPath: String): Boolean {
        if (path == rootPath) return false
        if (path == "$rootPath/Android" || path.startsWith("$rootPath/Android/")) return false
        val parent = path.substringBeforeLast('/')
        val name = path.substringAfterLast('/').lowercase(Locale.ROOT)
        if (parent == rootPath && name in protectedTopLevel) return false
        if (name == "camera" || name == "screenshots") return false
        return true
    }

    @Suppress("DEPRECATION")
    private fun apkItem(
        pm: PackageManager,
        path: String,
        name: String,
        size: Long,
        modified: Long,
        ext: String,
    ): ScanItem {
        var note = "Paquete de instalación (${ext.uppercase(Locale.ROOT)})"
        var preselect = false
        if (ext == "apk") {
            val archive = try {
                pm.getPackageArchiveInfo(path, 0)
            } catch (e: Exception) {
                null
            }
            if (archive == null) {
                note = "No se pudo leer (incompleto o dañado)"
            } else {
                val installed = try {
                    pm.getPackageInfo(archive.packageName, 0)
                } catch (e: Exception) {
                    null
                }
                when {
                    installed == null -> note = "App no instalada"
                    PackageInfoCompat.getLongVersionCode(installed) >= PackageInfoCompat.getLongVersionCode(archive) -> {
                        note = "Ya instalada"
                        preselect = true
                    }
                    else -> note = "Versión más nueva que la instalada"
                }
            }
        }
        return ScanItem(path, name, size, modified, Category.APKS, preselect = preselect, note = note)
    }

    private fun partialHash(path: String, size: Long): String? =
        try {
            val md = MessageDigest.getInstance("MD5")
            RandomAccessFile(path, "r").use { raf ->
                val chunk = 64 * 1024
                val buffer = ByteArray(chunk)
                if (size <= 2L * chunk) {
                    while (true) {
                        val read = raf.read(buffer)
                        if (read <= 0) break
                        md.update(buffer, 0, read)
                    }
                } else {
                    raf.readFully(buffer)
                    md.update(buffer)
                    raf.seek(size - chunk)
                    raf.readFully(buffer)
                    md.update(buffer)
                }
            }
            md.digest().toHex()
        } catch (e: Exception) {
            null
        }

    private fun fullHash(path: String, job: Job?): String? =
        try {
            val md = MessageDigest.getInstance("MD5")
            File(path).inputStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    if (job?.isActive == false) throw CancellationException("Análisis cancelado")
                    val read = input.read(buffer)
                    if (read < 0) break
                    md.update(buffer, 0, read)
                }
            }
            md.digest().toHex()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun ByteArray.toHex(): String {
        val hex = "0123456789abcdef"
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(hex[v ushr 4]).append(hex[v and 0x0F])
        }
        return sb.toString()
    }
}
