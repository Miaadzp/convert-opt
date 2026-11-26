package ir.miaad.optionheatmap.core.diagnostics

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Writes app diagnostics to private storage; export uses Android document picker. */
object DiagnosticLog {
    private const val TAG = "OH-DIAG-File"
    private const val MAX_BYTES = 2L * 1024L * 1024L
    private const val FILE_NAME = "optionheatmap-diagnostic.log"
    private val lock = Any()
    @Volatile private var logFile: File? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            val directory = File(context.applicationContext.filesDir, "diagnostics")
            if (!directory.exists() && !directory.mkdirs()) { Log.e(TAG, "Could not create diagnostics directory"); return }
            logFile = File(directory, FILE_NAME)
            appendLocked("INFO", TAG, "DIAGNOSTIC_SESSION_START appVersion=1.0")
        }
    }

    fun i(tag: String, message: String) { Log.i(tag, message); append("INFO", tag, message) }
    fun w(tag: String, message: String) { Log.w(tag, message); append("WARN", tag, message) }
    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        val detail = if (error == null) message else "$message\n${Log.getStackTraceString(error)}"
        append("ERROR", tag, detail)
    }
    fun stackTrace(error: Throwable): String = Log.getStackTraceString(error)

    fun export(context: Context, uri: Uri): Boolean {
        val source = synchronized(lock) { logFile } ?: File(context.applicationContext.filesDir, "diagnostics/$FILE_NAME")
        return try {
            if (!source.exists()) return false
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer -> synchronized(lock) { writer.write(source.readText(Charsets.UTF_8)) } } ?: return false
            true
        } catch (error: Exception) { Log.e(TAG, "Diagnostic export failed", error); false }
    }

    private fun append(level: String, tag: String, message: String) { synchronized(lock) { appendLocked(level, tag, message) } }
    private fun appendLocked(level: String, tag: String, message: String) {
        val file = logFile ?: return
        try {
            if (file.exists() && file.length() >= MAX_BYTES) {
                val previous = File(file.parentFile, "$FILE_NAME.1")
                if (previous.exists()) previous.delete()
                if (!file.renameTo(previous)) file.writeText("", Charsets.UTF_8)
            }
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Tehran") }.format(Date())
            file.appendText("$timestamp $level/$tag: $message\n", Charsets.UTF_8)
        } catch (error: IOException) { Log.e(TAG, "Could not write diagnostic log", error) }
    }
}
