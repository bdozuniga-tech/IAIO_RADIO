package com.example.radio_vertical

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
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
    
    // URL maestra en tu GitHub para chequear actualizaciones
    private val UPDATE_URL = "https://raw.githubusercontent.com/bdozuniga-tech/IAIO_RADIO/main/update.json"

    suspend fun checkForUpdates(currentVersionCode: Int): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(UPDATE_URL).build()
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
        withContext(Dispatchers.IO) {
            try {
                // PRIMERO: Chequear permiso en Android 8.0+ (Oreo en adelante)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        Log.d("UpdateManager", "No tiene permiso de instalación. Pidiendo...")
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        // Detenemos aquí para que el usuario de permiso y reintente
                        return@withContext
                    }
                }

                // Guardamos en External Cache para máxima compatibilidad con HyperOS
                val apkFile = File(context.externalCacheDir, "update.apk")
                if (apkFile.exists()) apkFile.delete() // Limpiar versiones viejas

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
                    
                    Log.d("UpdateManager", "APK lista en: ${apkFile.absolutePath}")
                    // PEQUEÑO DELAY PARA QUE EL SISTEMA REGISTRE EL ARCHIVO
                    delay(500)
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Error downloading APK: ${e.message}")
            }
        }
    }

    private fun installApk(file: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(intent)
    }
}