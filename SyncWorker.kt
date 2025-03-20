package com.dcp.android.fragments.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import androidx.work.multiprocess.RemoteWorkerService
import androidx.work.workDataOf
import com.dcp.android.BuildConfig
import com.dcp.android.R
import com.dcp.android.utils.Api
import java.util.concurrent.TimeUnit
import kotlin.jvm.java

class SyncWorker(context: Context, params: WorkerParameters) : RemoteCoroutineWorker(context, params) {

    companion object {
        private const val TYPE = "work_type"
        private const val TAG = "SyncWorker"
        private const val CHANNEL_ID = "sync_worker_channel"
        private const val NOTIFICATION_ID = 1
        /**
         * Builds a OneTimeWorkRequest for DataUpdateWorker.
         * @param context The context to use.
         */
        fun buildOneTimeWorkRequest(context: Context, workType: Type): OneTimeWorkRequest {
            Log.d(TAG, "buildOneTimeWorkRequest")
            // Create a component name for the remote worker service
            val serviceName = RemoteWorkerService::class.java.name
            val componentName = ComponentName(BuildConfig.APPLICATION_ID, serviceName)

            // Create work data containing necessary information
            val data = workDataOf(
                ARGUMENT_PACKAGE_NAME to componentName.packageName,
                ARGUMENT_CLASS_NAME to componentName.className,
                TYPE to workType.name
            )

            // Build constraints for the work request
            val constraints = Constraints.Builder().apply {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }.build()

            // Build the OneTimeWorkRequest
            val oneTimeWorkRequest = OneTimeWorkRequest.Builder(SyncWorkeryty::class.java)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(data)
                // Set backoff criteria for retrying
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30000, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

            // Enqueue the work request with WorkManager
            WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
            return oneTimeWorkRequest
        }
    }

    private val api by lazy { Api(context).getApiHandler() }
    private var percentage = 5
    /**
     * Performs the actual work in a remote coroutine.
     */
    override suspend fun doRemoteWork(): Result {
        // Extract work type
        val workType = inputData.getString(TYPE)
        Log.d(TAG, "doRemoteWork: $workType")

        return try {
            when (workType) {
                Type.UPLOAD.name -> {
                    Result.failure(workDataOf("message" to "Upload not supported"))
                }

                Type.DOWNLOAD.name -> {
                    val dp = downloadProcesses()
                    if(percentage != 25) percentage = 25
                    updateProgress(percentage, "Downloading Processes Page $totalPage")
                    val ds = downloadSuppliers()
                    if(percentage != 50) percentage = 50
                    updateProgress(percentage, "Downloading Suppliers Page $totalPage")
                    val dc = downloadConceptions()
                    percentage = 100

                    if (dp && ds && dc)
                        Result.success(workDataOf("message" to "Data Downloaded"))
                    else
                        Result.failure(workDataOf("message" to "Failed to download data"))
                }
                else -> Result.failure(workDataOf("message" to "Unknown work type"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(workDataOf("message" to e.message))
        }
    }

    init {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Data Sync", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateProgress(progress: Int, message: String) {
        setProgressAsync(workDataOf("progress" to progress, "message" to message))
//        setForegroundAsync(createForegroundInfo(message, progress, isFailed))
    }

    private fun createForegroundInfo(progressText: String, progress: Int, isFailed: Boolean): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Syncing Data")
            .setContentText(progressText)
            .setSmallIcon(R.drawable.ic_sync)
            .setProgress(100, if (isFailed) 100 else progress, false)
            .setOngoing(!isFailed)
            .build()

        // Specify the foreground service type (DATA_SYNC is most appropriate for your use case)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

   // Enumeration representing the type of work
    enum class Type {
        UPLOAD, DOWNLOAD
    }

}
