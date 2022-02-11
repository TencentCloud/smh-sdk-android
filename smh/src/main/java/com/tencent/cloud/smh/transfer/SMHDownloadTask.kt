package com.tencent.cloud.smh.transfer

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import bolts.CancellationTokenSource
import com.tencent.cloud.smh.ClientInternalException
import com.tencent.cloud.smh.ClientManualCancelException
import com.tencent.cloud.smh.SMHCollection
import com.tencent.cloud.smh.api.model.FileInfo
import com.tencent.cos.xml.listener.CosXmlProgressListener
import com.tencent.cos.xml.model.CosXmlRequest
import com.tencent.cos.xml.utils.DigestUtils
import com.tencent.cos.xml.utils.FileUtils
import com.tencent.qcloud.core.logger.QCloudLogger
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicReference
import kotlin.Exception

/**
 * 将 COS 对象下载到本地，支持续传、下载指定 Range。
 *
 *
 *
 * 需要的 COS 权限：HeadObject、GetObject
 *
 *
 *
 * Created by rickenwang on 2021/6/30.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
class SMHDownloadTask(
    context: Context,
    smhCollection: SMHCollection,
    private val downloadFileRequest: DownloadFileRequest,
) : SMHTransferTask(context, smhCollection, downloadFileRequest) {

    private var simpleDownloadTask: SimpleDownloadTask? = null
    override fun tag(): String {
        return TAG
    }

    fun pause() {
        if (taskState != SMHTransferState.RUNNING && taskState != SMHTransferState.WAITING) {
            QCloudLogger.i(TAG, "[%s]: cannot pause upload task in state %s", taskId, taskState)
            return
        }
        QCloudLogger.i(TAG, "[%s]: pause upload task", taskId)
        isManualPaused = true
        // 快速回调状态
        onTransferPaused()
        cts.cancel()
        simpleDownloadTask?.cancel()
    }

    suspend fun resume() {
        if (taskState !== SMHTransferState.PAUSED) {
            QCloudLogger.i(TAG, "[%s]: cannot resume upload task in state %s", taskId, taskState)
            return
        }
        QCloudLogger.i(TAG, "[%s]: resume upload task", taskId, taskState)
        start()
    }

    fun cancel() {
        QCloudLogger.i(TAG, "[%s]: cancel upload task", taskId)
        isManualCanceled = true
        cts.cancel()
        simpleDownloadTask?.cancel()
        // 清空下载记录
        clearHasTransferPart()
        // 删除本地文件
        FileUtils.deleteFileIfExist(downloadFileRequest.localFullPath)
    }

    /**
     * 清除下载记录
     */
    private fun clearHasTransferPart() {
        val sharedPreferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        clearDownloadRecord(sharedPreferences, downloadFileRequest)
    }


    override suspend fun checking() {

    }

    override suspend fun execute(): SMHTransferResult {
        return simpleDownload()
    }


    // 简单下载
    private suspend fun simpleDownload(): DownloadFileResult {
        val downloadTask = SimpleDownloadTask(context, transferApiProxy, downloadFileRequest, cts, verifyContent)
        downloadTask.setTaskId(taskId)
        downloadTask.smhKey = smhKey
        downloadTask.progressListener = CosXmlProgressListener { progress, target ->
            onTransferProgressChange(progress, target)
        }
        simpleDownloadTask = downloadTask
        return downloadTask.execute()
    }


    private class SimpleDownloadTask(
        val context: Context,
        val transferApiProxy: TransferApiProxy,
        val downloadFileRequest: DownloadFileRequest,
        val cts: CancellationTokenSource,
        val verifyContent: Boolean,
        val mClearDownloadPart: Boolean = false,
    ) {
        lateinit var smhKey: String
        var historyId: Long? = null
        var progressListener: CosXmlProgressListener? = null
        private var taskId = ""

        private var offset: Long = 0
        private lateinit var fileInfo: FileInfo

        private lateinit var sharedPreferences: SharedPreferences
        private var creationTime: String? = null
        private var eTag: String? = null
        private var crc64ecma: String? = null

        val requestReference = AtomicReference<CosXmlRequest>()

        init {
            smhKey = downloadFileRequest.key
            historyId = downloadFileRequest.historyId
        }

        suspend fun execute(): DownloadFileResult {

            // 1. 生成下载的参数信息
            checkoutManualCanceled()
            checking()

            // 2. 检查是否需要续传，如果是续传需要修改 GetObjectRequest 参数
            checkoutManualCanceled()
            val hasDownloadPart = hasDownloadPart()

            // 3. 根据是否需要续传来修改上下文
            checkoutManualCanceled()
            prepareDownloadContext(hasDownloadPart)

            // 4. 执行下载
            checkoutManualCanceled()
            download()

            // 5. 文件 CRC 校验或者 MD5 校验
            //    校验失败后需要删除下载文件
            checkoutManualCanceled()
            try {
                if (verifyContent) {
                    verifyContent()
                }
            } catch (e: Exception) {
                FileUtils.deleteFileIfExist(downloadFileRequest.localFullPath)
                throw ClientInternalException("VerifyContentFailed: " + e.message)
            }

            return DownloadFileResult(
                smhKey, crc64ecma
            )
        }

        // 检查任务是否已经被取消
        private fun checkoutManualCanceled() {
            if (cts.isCancellationRequested) {
                throw ClientManualCancelException
            }
        }

        fun cancel() {
            cts.cancel()
            requestReference.get()?.let {
                transferApiProxy.cancel(it)
            }
        }

        fun setTaskId(taskId: String) {
            this.taskId = taskId
        }

        // 1. HeadObject 拿到文件信息 lastModified、contentLength、eTag、crc64ecma
        // 2. 拿到 range 参数
        // 3. 拿到 offset 参数
        private suspend fun checking() {
            sharedPreferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
            fileInfo = transferApiProxy.getFileInfo(smhKey, historyId)
            creationTime = fileInfo.creationTime
            eTag = fileInfo.eTag
            crc64ecma = fileInfo.crc64

            if (mClearDownloadPart) {
                QCloudLogger.i(TAG, "[%s]: clear has download part", taskId)
                clearDownloadRecord()
            }
        }

        // 1. 先根据 key 找到 DownloadRecord
        // 2. 校验 DownloadRecord 的有效性
        private fun hasDownloadPart(): Boolean {

            return try {
                val downloadRecord = getDownloadRecord()?: return false
                QCloudLogger.i(TAG, "[%s]: find DownloadRecord: %s", taskId, downloadRecord)
                if (downloadRecord.creationTime == null || downloadRecord.creationTime != creationTime ||
                    downloadRecord.eTag == null || downloadRecord.eTag != eTag ||
                    downloadRecord.crc64ecma != null && crc64ecma != null && downloadRecord.crc64ecma != crc64ecma) {
                    QCloudLogger.w(
                        TAG,
                        "[%s]: verify DownloadRecord failed: lastModified:%s, eTag:%s, crc64ecma:%s",
                        taskId,
                        creationTime,
                        eTag,
                        crc64ecma,
                    )
                    false
                } else {
                    true
                }
            } catch (e: JSONException) {
                QCloudLogger.i(TAG, "[%s]: parse DownloadRecord failed: %s", taskId, e.message)
                false
            }
        }

        // 1. 根据 DownloadRecord 清理本地环境
        // 2. 生成新的 GetObjectRequest
        private fun prepareDownloadContext(hasDownloadPart: Boolean) {
            val localFile = File(downloadFileRequest.localFullPath)

            // 续传
            if (hasDownloadPart) {
                if (localFile.exists() && localFile.isFile) {
                    offset = localFile.length()
                }
                QCloudLogger.i(TAG, "[%s]: has download part %d", taskId, offset)
            } else {
                FileUtils.deleteFileIfExist(localFile.absolutePath)
            }
        }

        // 执行 GetObjectRequest 请求
        private suspend fun download() {

            // 保存下载记录
            insertDownloadRecord(DownloadRecord(creationTime, eTag, crc64ecma))
            QCloudLogger.i(TAG, "[%s]: start download to %s", taskId, downloadFileRequest.localFullPath)

//            val totalSize = fileInfo.size
//            if (totalSize == null || totalSize == 0L || totalSize > offset ) {  //
//            }

            val etag = transferApiProxy.download(
                // transferApiProxy.smhCollection.getDownloadAccessUrl(downloadFileRequest.key, historyId, true),
                fileInfo.cosUrl,
//                transferApiProxy.smhCollection.getDownloadAccessUrl(
//                    smhKey, historyId, true
//                ),
                offset,
                downloadFileRequest.localFullPath, requestReference) { progress, target ->
                progressListener?.onProgress(
                    offset + progress,
                    offset + target
                )
            }

            clearDownloadRecord()
            QCloudLogger.i(TAG, "[%s]: download complete", taskId)
        }

        fun clearDownloadRecord() {
            Companion.clearDownloadRecord(sharedPreferences, downloadFileRequest)
        }

        fun getDownloadRecord(): DownloadRecord? {
            return Companion.getDownloadRecord(sharedPreferences, downloadFileRequest)

        }

        fun insertDownloadRecord(downloadRecord: DownloadRecord) {
            Companion.insertDownloadRecord(sharedPreferences, downloadFileRequest, downloadRecord)
        }

        private fun verifyContent() {

            val localFile = File(downloadFileRequest.localFullPath)
            checkCRC64(fileInfo.crc64, localFile)
        }

        private fun checkCRC64(
            remoteCRC: String?,
            localFile: File
        ) {
            if (TextUtils.isEmpty(remoteCRC)) {
                return
            }
            val localCRC64 = try {
                DigestUtils.getCRC64(FileInputStream(localFile))
            } catch (e: FileNotFoundException) {
                throw ClientInternalException("verify CRC64 failed: " + e.message)
            }
            val remoteCRC64: Long = DigestUtils.getBigIntFromString(remoteCRC)
            if (localCRC64 != remoteCRC64) {
                throw ClientInternalException("verify CRC64 failed, local crc64: $localCRC64, remote crc64: $remoteCRC64")
            }
        }
    }

    class DownloadRecord(
        var creationTime: String?, //
        var eTag: String?,
        var crc64ecma: String?,
    ) {
        companion object {

            fun flatJson(downloadRecord: DownloadRecord): String {
                val jsonObject = JSONObject()
                jsonObject.put("creationTime", downloadRecord.creationTime)
                jsonObject.put("eTag", downloadRecord.eTag)
                jsonObject.put("crc64ecma", downloadRecord.crc64ecma)
                return jsonObject.toString()
            }

            fun toJson(str: String): DownloadRecord {
                val jsonObject = JSONObject(str)
                val creationTime = jsonObject.getString("creationTime")
                val eTag = jsonObject.getString("eTag")
                val crc64ecma = jsonObject.optString("crc64ecma")
                return DownloadRecord(
                    creationTime,
                    eTag,
                    crc64ecma,
                )
            }
        }
    }

    companion object {
        private const val TAG = "SMHDownload"

        fun clearDownloadRecord(sharedPreferences: SharedPreferences, downloadFileRequest: DownloadFileRequest) {
            sharedPreferences.edit().remove(getSPKey(downloadFileRequest)).apply()
        }

        fun getDownloadRecord(sharedPreferences: SharedPreferences, downloadFileRequest: DownloadFileRequest): DownloadRecord? {
            return try {
                val downloadRecordStr = sharedPreferences.getString(getSPKey(downloadFileRequest), null)
                downloadRecordStr?.let {
                    DownloadRecord.toJson(downloadRecordStr)
                }
            } catch (exception: Exception) {
                null
            }

        }

        fun insertDownloadRecord(sharedPreferences: SharedPreferences, downloadFileRequest: DownloadFileRequest, downloadRecord: DownloadRecord) {
            try {
                sharedPreferences.edit().putString(
                    getSPKey(downloadFileRequest), DownloadRecord.flatJson(
                        downloadRecord
                    )
                ).apply()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        private fun getSPKey(downloadFileRequest: DownloadFileRequest) =
            "[${downloadFileRequest.key}]->[${downloadFileRequest.localFullPath}]:[${downloadFileRequest.historyId}]"
    }
}