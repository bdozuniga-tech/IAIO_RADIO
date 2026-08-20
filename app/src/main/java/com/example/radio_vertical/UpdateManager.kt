package com.example.radio_vertical

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String = ""
)

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    private val UPDATE_URL = "https://raw.githubusercontent.com/bdozuniga-tech/IAIO_RADIO/main/update.json"

    suspend fun checkForUpdates(currentVersionCode: Int): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val nocacheUrl = "$UPDATE_URL?t=${System.currentTimeMillis()}"
                val request = Request.Builder().url(nocacheUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@withContext null
                        val info = json.decodeFromString<UpdateInfo>(body)
                        if (info.versionCode > currentVersionCode) {
                            return@withContext info
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Error checking updates: ${e.message}")
            }
            null
        }
    }

    suspend fun downloadAndInstallApk(info: UpdateInfo, onProgress: (Float) -> Unit) {
        withContext(Dispatchers.Main) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "IAIO: Activa el permiso de instalación para continuar", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return@withContext
                }
            }
        }

        withContext(Dispatchers.IO) {
            try {
                // USAMOS EXTERNAL FILES DIR - El lugar infalible para Android 14 y Xiaomi
                val apkFile = File(context.getExternalFilesDir(null), "update.apk")
                if (apkFile.exists()) apkFile.delete()

                val request = Request.Builder().url(info.apkUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext

                    val body = response.body ?: return@withContext
                    val totalSize = body.contentLength()
                    
                    body.byteStream().use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            var totalRead = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                totalRead += read
                                if (totalSize > 0) {
                                    onProgress(totalRead.toFloat() / totalSize)
                                }
                            }
                        }
                    }
                    
                    // FORZAR PERMISOS DE LECTURA (PARA EL INSTALADOR DEL SISTEMA)
                    apkFile.setReadable(true, false)
                    
                    Log.d("UpdateManager", "APK lista para instalación forzada: ${apkFile.absolutePath}")
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "¡Lanzando Instalador Maestro! Prepárate...", Toast.LENGTH_SHORT).show()
                        delay(1000)
                        installApk(apkFile)
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Fallo crítico: ${e.message}")
            }
        }
    }

    private fun installApk(file: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // INTENT AGRESIVO CON SELECTOR (BYPASS HYPEROS)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // Truco extra para Xiaomi
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            
            val chooser = Intent.createChooser(intent, "Instalar Actualización IAIO")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error al abrir instalador: ${e.message}")
        }
    }
}