package com.example.data.github

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val body: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkAssetName: String?,
    val apkSizeBytes: Long,
    val publishedAt: String,
    val isUpdateAvailable: Boolean
)

class GitHubReleaseUpdater(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun checkLatestRelease(owner: String, repo: String): Result<GitHubReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeOwner = owner.trim().replace(Regex("[^a-zA-Z0-9._-]"), "")
                val safeRepo = repo.trim().replace(Regex("[^a-zA-Z0-9._-]"), "")
                val apiUrl = "https://api.github.com/repos/$safeOwner/$safeRepo/releases/latest"

                val req = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "JRS-AI-Guard/${BuildConfig.VERSION_NAME}")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException(
                            if (resp.code == 404) {
                                "No published GitHub release found yet for $safeOwner/$safeRepo (HTTP 404)."
                            } else {
                                "GitHub Releases API returned HTTP ${resp.code}"
                            }
                        )
                    }
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val tag = json.optString("tag_name", "v1.0.0")
                    val name = json.optString("name", tag)
                    val notes = json.optString("body", "Latest JRS AI Guard release")
                    val htmlUrl = json.optString(
                        "html_url",
                        "https://github.com/$safeOwner/$safeRepo/releases"
                    )
                    val published = json.optString("published_at", "")

                    var apkUrl: String? = null
                    var apkName: String? = null
                    var apkSize = 0L
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val assetName = asset.optString("name")
                            if (assetName.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url")
                                apkName = assetName
                                apkSize = asset.optLong("size", 0L)
                                break
                            }
                        }
                    }

                    GitHubReleaseInfo(
                        tagName = tag,
                        releaseName = name,
                        body = notes,
                        htmlUrl = htmlUrl,
                        apkDownloadUrl = apkUrl,
                        apkAssetName = apkName,
                        apkSizeBytes = apkSize,
                        publishedAt = published,
                        isUpdateAvailable = isNewerVersion(tag, BuildConfig.VERSION_NAME)
                    )
                }
            }
        }

    fun enqueueApkDownload(downloadUrl: String, fileName: String): Boolean {
        return runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("JRS AI Guard Update ($fileName)")
                .setDescription("Downloading verified GitHub Release APK")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
            dm.enqueue(req)
            true
        }.getOrDefault(false)
    }

    fun openReleasePage(url: String) {
        val parsed = Uri.parse(url)
        if (parsed.scheme != "https" && parsed.scheme != "http") return
        val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
        val remoteParts = remoteTag.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = localVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
