package io.nekohasekai.sagernet.bg

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy.KEEP
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.*
import libcore.Libcore
import moe.matsuri.nb4a.utils.Util
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Weekly auto-update for route assets (geoip.db / geosite.db),
 * reusing the same update logic as AssetsActivity (管理路由资源).
 */
object RouteAssetUpdater {

    private const val WORK_NAME = "RouteAssetUpdater"
    private const val WEEK_MINUTES = 7 * 24 * 60

    val assetFiles = arrayOf("geoip.db", "geosite.db")

    private val rulesProviders = listOf(
        RuleAssetsProvider(
            "SagerNet/sing-geoip",
            "SagerNet/sing-geosite",
        ),
        RuleAssetsProvider(
            "soffchen/sing-geoip",
            "soffchen/sing-geosite",
        ),
        RuleAssetsProvider(
            "Chocolate4U/Iran-sing-box-rules"
        ),
        RuleAssetsProvider(
            "L11R/antizapret-sing-box-geo"
        ),
    )

    private data class RuleAssetsProvider(
        val repoByFileName: Map<String, String>
    ) {
        constructor(
            geoipRepo: String,
            geositeRepo: String = geoipRepo,
        ) : this(
            mapOf(
                "geoip.db" to geoipRepo,
                "geosite.db" to geositeRepo,
            )
        )
    }

    suspend fun reconfigureUpdater() {
        val wm = RemoteWorkManager.getInstance(app)
        if (!DataStore.autoUpdateRouteAssets) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            KEEP,
            PeriodicWorkRequest.Builder(RouteAssetUpdateTask::class.java, WEEK_MINUTES.toLong(), TimeUnit.MINUTES)
                .setInitialDelay(WEEK_MINUTES.toLong(), TimeUnit.MINUTES)
                .build()
        )
    }

    /**
     * Update a single route asset file from its provider's latest GitHub release.
     * @return true if a new version was applied, false if already up-to-date.
     */
    suspend fun updateAsset(file: File, versionFile: File, localVersion: String): Boolean {
        val repo = rulesProviders[DataStore.rulesProvider].repoByFileName[file.name] ?: return false

        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            trySocks5(DataStore.mixedPort)
        }

        try {
            var response = client.newRequest().apply {
                setURL("https://api.github.com/repos/$repo/releases/latest")
            }.execute()

            val release = JSONObject(Util.getStringBox(response.contentString))
            val tagName = release.optString("tag_name")

            if (tagName == localVersion) {
                return false
            }

            val releaseAssets = release.getJSONArray("assets").filterIsInstance<JSONObject>()
            val assetToDownload = releaseAssets.find { it.getStr("name") == file.name }
                ?: error("File ${file.name} not found in release ${release["url"]}")

            response = client.newRequest().apply {
                setURL(assetToDownload.getStr("browser_download_url"))
            }.execute()

            val cacheFile = File(file.parentFile, file.name + ".tmp")
            cacheFile.parentFile?.mkdirs()

            response.writeTo(cacheFile.canonicalPath)

            if (file.name.endsWith(".xz")) {
                Libcore.unxz(cacheFile.absolutePath, file.absolutePath)
                cacheFile.delete()
            } else {
                cacheFile.renameTo(file)
            }

            versionFile.writeText(tagName)
            return true
        } finally {
            client.close()
        }
    }

    /** Headless update of the official route assets. Safe to call from any background context. */
    suspend fun updateAll() {
        val filesDir = app.getExternalFilesDir(null) ?: app.filesDir
        for (name in assetFiles) {
            val file = File(filesDir, name)
            val versionFile = File(filesDir, name.removeSuffix(".db") + ".version.txt")
            val localVersion = if (file.isFile && versionFile.isFile) {
                versionFile.readText().trim()
            } else {
                "Unknown"
            }
            try {
                val updated = updateAsset(file, versionFile, localVersion)
                Logs.d("route assets: $name " + (if (updated) "updated" else "no update"))
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
    }

    class RouteAssetUpdateTask(
        appContext: Context, params: WorkerParameters
    ) : CoroutineWorker(appContext, params) {

        val nm = NotificationManagerCompat.from(applicationContext)

        val notification = NotificationCompat.Builder(applicationContext, "service-subscription")
            .setWhen(0)
            .setTicker(applicationContext.getString(R.string.route_assets))
            .setContentTitle(applicationContext.getString(R.string.route_assets))
            .setSmallIcon(R.drawable.ic_service_active)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        override suspend fun doWork(): Result {
            if (!DataStore.autoUpdateRouteAssets) {
                return Result.success()
            }
            nm.notify(3, notification.build())
            updateAll()
            nm.cancel(3)
            return Result.success()
        }
    }
}
