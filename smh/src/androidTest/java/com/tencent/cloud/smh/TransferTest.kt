package com.tencent.cloud.smh

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.tencent.cloud.smh.api.SMHService
import com.tencent.cloud.smh.api.dataOrNull
import com.tencent.cloud.smh.api.model.Directory
import com.tencent.cloud.smh.api.model.QuotaBody
import com.tencent.cloud.smh.transfer.*
import com.tencent.qcloud.core.logger.QCloudLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.Exception
import java.lang.Thread.sleep
import kotlin.random.Random


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class TransferTest {

    lateinit var smh: SMHCollection
    lateinit var user: SMHUser
    lateinit var context: Context
    private val defaultDirectory = Directory("default")
    private val assistDirectory = Directory("assist")

    @Rule
    @JvmField
    var mRuntimePermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        user = StaticUser(
                libraryId = BuildConfig.SMH_ID,
                librarySecret = BuildConfig.SMH_KEY
        )
        smh = SMHCollection(
            context = context,
            user = user
        )
    }


    @Test
    fun testUploadTask() {

        runBlocking {

            val file = File.createTempFile("read", ".txt")
            createFile(file, smhBigMediaSize)
            val uploadFileRequest = UploadFileRequest(
                key = "123.jpg",
                Uri.fromFile(file)
            )
            uploadFileRequest.progressListener = object: SMHProgressListener {
                override fun onProgressChange(request: SMHRequest, progress: Long, target: Long) {
                    Log.i("Test", "Progress change $progress/$target")
                }
            }
            uploadFileRequest.resultListener = object: SMHResultListener {
                override fun onSuccess(request: SMHRequest, result: SMHResult) {
                    Log.i("Test", "onSuccess")
                }

                override fun onFailure(
                    request: SMHRequest,
                    smhException: SMHException?,
                    smhClientException: SMHClientException?
                ) {
                    Log.i("Test", "onFailure $smhException and ")
                }
            }
            uploadFileRequest.stateListener = object : SMHStateListener {
                override fun onStateChange(request: SMHRequest, state: SMHTransferState) {
                    Log.i("Test", "onStateChange $state")
                }

            }

            val uploadTask = SMHUploadTask(
                context,
                smh,
                uploadFileRequest
            )

            launch {
                delay(5000)
                uploadTask.pause(true)
            }

            uploadTask.start()

            delay(2000)
            uploadTask.resume()
        }
    }

    @Test
    fun testDownloadTask() {

        runBlocking {

            val file = File.createTempFile("dowloadFile", ".txt")
            createFile(file, smhBigMediaSize)
            val downloadFileRequest = DownloadFileRequest(
                key = "123.jpg",
                // localFullPath = file.absolutePath
            )
            downloadFileRequest.progressListener = object: SMHProgressListener {
                override fun onProgressChange(request: SMHRequest, progress: Long, target: Long) {
                    Log.i("Test", "Progress change $progress/$target")
                }
            }
            downloadFileRequest.resultListener = object: SMHResultListener {
                override fun onSuccess(request: SMHRequest, result: SMHResult) {
                    Log.i("Test", "onSuccess")
                }

                override fun onFailure(
                    request: SMHRequest,
                    smhException: SMHException?,
                    smhClientException: SMHClientException?
                ) {
                    Log.i("Test", "onFailure $smhException, $smhClientException")
                }
            }
            downloadFileRequest.stateListener = object : SMHStateListener {
                override fun onStateChange(request: SMHRequest, state: SMHTransferState) {
                    Log.i("Test", "onStateChange $state")
                }

            }

            val downloadTask = SMHDownloadTask(
                context,
                smh,
                downloadFileRequest
            )

            downloadTask.start()
            val result = downloadTask.getResultOrThrow()


        }
    }

    @Test
    fun testDownload() {

        runBlocking {

            val initDownload = smh.initDownload("123")
            val downloadUrl = initDownload.url?: throw SMHClientException("DownloadUrlIsNull")
            val downloadResult = smh.download(
                DownloadRequest(downloadUrl, null)
            )

            val inputStream = downloadResult.inputStream?: return@runBlocking

            val buffer = ByteArray(8192)

            // val filePath = "${context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)}/dowload"
            var len: Int = -1
            var count = 0
            while (true) {
                len = inputStream.read(buffer, 0, buffer.size)
                QCloudLogger.i("Test", "read len $len")
                if (len <= 0) {
                    break
                } else {
                    count += len
                }
            }
            inputStream.close()
            QCloudLogger.i("Test", "read count is $count")
        }
    }

    @Test
    fun testInputStreamDownloadTask() {

        runBlocking {

            val file = File.createTempFile("dowloadFile", ".txt")
            createFile(file, smhBigMediaSize)
            val downloadFileRequest = DownloadFileRequest(
                key = "123",
            )
            downloadFileRequest.setRange(100)
            downloadFileRequest.progressListener = object: SMHProgressListener {
                override fun onProgressChange(request: SMHRequest, progress: Long, target: Long) {
                    Log.i("Test", "Progress change $progress/$target")
                }
            }

            val downloadTask = SMHDownloadTask(
                context,
                smh,
                downloadFileRequest
            )

            downloadTask.start()
            val result = downloadTask.getResultOrThrow() as DownloadFileResult
            val inputStream = result.content?: return@runBlocking

            val buffer = ByteArray(8192)

           // val filePath = "${context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)}/dowload"
            var len: Int = -1
            var count = 0
            while (true) {
                len = inputStream.read(buffer, 0, buffer.size)
                QCloudLogger.i("Test", "read len $len")
                if (len <= 0) {
                    break
                } else {
                    count += len
                }
            }
            inputStream.close()
            QCloudLogger.i("Test", "read count is $count")
        }
    }
}