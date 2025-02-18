package com.tencent.cloud.smh

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.tencent.cloud.smh.api.model.Directory
import com.tencent.cloud.smh.transfer.DownloadFileRequest
import com.tencent.cloud.smh.transfer.DownloadFileResult
import com.tencent.cloud.smh.transfer.DownloadRequest
import com.tencent.cloud.smh.transfer.SMHDownloadTask
import com.tencent.cloud.smh.transfer.SMHInitMultipleUploadListener
import com.tencent.cloud.smh.transfer.SMHProgressListener
import com.tencent.cloud.smh.transfer.SMHRequest
import com.tencent.cloud.smh.transfer.SMHResult
import com.tencent.cloud.smh.transfer.SMHResultListener
import com.tencent.cloud.smh.transfer.SMHStateListener
import com.tencent.cloud.smh.transfer.SMHTransferState
import com.tencent.cloud.smh.transfer.SMHUploadTask
import com.tencent.cloud.smh.transfer.UploadFileResult
import com.tencent.qcloud.core.logger.QCloudLogger
import com.tencent.qcloud.core.util.ContextHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream


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

    private val utDirectory = Directory("AndroidUT")

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
        user = MySMHSimpleUser()
        smh = SMHCollection(
            context = context,
            user = user,
            customHost = "api.test.tencentsmh.cn",
            isDebuggable = true
        )
    }


    @Test
    fun testUploadTask() {
        runBlocking {
            val file = File.createTempFile("uploadBigMedia", ".jpg")
//            val file = File("/data/user/0/com.tencent.cloud.smh.test/cache/uploadBigMedia1604114299541749973.jpg")
            createFile(file, smhBigMediaSize)
            val uploadTask = smh.upload(
                name = "uploadBigMedia2.jpg",
                dir = utDirectory,
//                dir = Directory(),
                uri = Uri.fromFile(file),
//                inputStream = file.inputStream(),
//                inputStream = MyUriInputStream(Uri.fromFile(file)),
//                inputStream = MyFileInputStream(file),
                meta = mapOf("test1" to "test1_value", "test2" to "test2_value"),
                stateListener = object : SMHStateListener {
                    override fun onStateChange(request: SMHRequest, state: SMHTransferState) {
                        Log.i("testUploadTask", "onStateChange $state")
                    }
                },
                progressListener = object: SMHProgressListener {
                    override fun onProgressChange(request: SMHRequest, progress: Long, target: Long) {
                        Log.i("testUploadTask", "Progress change $progress/$target")
                    }
                },
                initMultipleUploadListener = object : SMHInitMultipleUploadListener{
                    override fun onSuccess(confirmKey: String) {
                        Log.i("testUploadTask", "onInitMultipleUpload $confirmKey")
                    }
                },
                resultListener = object: SMHResultListener {
                    override fun onSuccess(request: SMHRequest, result: SMHResult) {
                        val isQuick = (result as UploadFileResult).isQuick
                        Log.i("testUploadTask", "onSuccess_$isQuick")
                    }

                    override fun onFailure(
                        request: SMHRequest,
                        smhException: SMHException?,
                        smhClientException: SMHClientException?
                    ) {
                        smhException?.apply {
                            Log.i("testUploadTask", "onFailure request id ${this.requestId}")
                        }
                        smhException?.printStackTrace()
                        smhClientException?.printStackTrace()
                        Log.i("testUploadTask", "onFailure $smhException and ")
                        Log.i("testUploadTask", "onFailure $smhClientException and ")
                    }
                }
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
    fun testBatchUploadTask() {
        runBlocking {
            val taskList = mutableListOf<SMHUploadTask>()
            for (i in 1..10){
                val file = File.createTempFile("uploadBigMedia", ".jpg")
                createFile(file, smhBigMediaSize)
                val uploadTask = smh.upload(
                    name = "uploadBigMedia2.jpg",
                    dir = utDirectory,
                    uri = Uri.fromFile(file),
                    meta = mapOf("test1" to "test1_value", "test2" to "test2_value"),
                    stateListener = object : SMHStateListener {
                        override fun onStateChange(request: SMHRequest, state: SMHTransferState) {
                            Log.i("testUploadTask${i}", "onStateChange $state")
                        }
                    },
                    progressListener = object: SMHProgressListener {
                        override fun onProgressChange(request: SMHRequest, progress: Long, target: Long) {
                            Log.i("testUploadTask${i}", "Progress change $progress/$target")
                        }
                    },
                    resultListener = object: SMHResultListener {
                        override fun onSuccess(request: SMHRequest, result: SMHResult) {
                            val isQuick = (result as UploadFileResult).isQuick
                            Log.i("testUploadTask${i}", "onSuccess_$isQuick")
                        }

                        override fun onFailure(
                            request: SMHRequest,
                            smhException: SMHException?,
                            smhClientException: SMHClientException?
                        ) {
                            Log.i("testUploadTask${i}", "onFailure $smhException and ")
                            Log.i("testUploadTask${i}", "onFailure $smhClientException and ")
                        }
                    }
                )
                taskList.add(uploadTask)
            }

            launch {
                delay(10000)
                for (uploadTask in taskList){
                    uploadTask.pause(true)
                }
            }

            for (uploadTask in taskList){
                launch {
                    uploadTask.start()
                }
            }

            launch {
                delay(10000)
                for (uploadTask in taskList) {
                    uploadTask.resume()
                }
            }
        }
    }

    @Test
    fun testDownloadTask() {
        runBlocking {
            val file = File.createTempFile("dowloadBigMedia", ".jpg")
            createFile(file, smhBigMediaSize)
            val downloadTask = smh.download(
                name = "uploadBigMedia.jpg",
                dir = utDirectory,
//                dir = Directory(),
                localFullPath = file.absolutePath,
                rangeStart = 0,
                stateListener = object : SMHStateListener {
                    override fun onStateChange(request: SMHRequest, state: SMHTransferState) {
                        Log.i("testDownloadTask", "onStateChange $state")
                    }
                },
                progressListener = object: SMHProgressListener {
                    override fun onProgressChange(request: SMHRequest, progress: Long, target: Long) {
                        Log.i("testDownloadTask", "Progress change $progress/$target")
                    }
                },
                resultListener = object: SMHResultListener {
                    override fun onSuccess(request: SMHRequest, result: SMHResult) {
                        Log.i("testDownloadTask", "onSuccess")
                        if(result is DownloadFileResult){
                            Log.i("testDownloadTask", "bytesTotal: ${result.bytesTotal}")
                            Log.i("testDownloadTask", "content: ${result.content.toString()}")
                            Log.i("testDownloadTask", "crc64: ${result.crc64}")
                            Log.i("testDownloadTask", "key: ${result.key}")
                            Log.i("testDownloadTask", "meta: ${result.meta?.entries?.joinToString()}")
                        }
                    }

                    override fun onFailure(
                        request: SMHRequest,
                        smhException: SMHException?,
                        smhClientException: SMHClientException?
                    ) {
                        Log.i("testDownloadTask", "onFailure $smhException, $smhClientException")
                    }
                }
            )

            launch {
                delay(5000)
                downloadTask.pause()
            }

            downloadTask.start()

            delay(2000)
            downloadTask.resume()
        }
    }

    @Test
    fun testDownloadTask1() {
        runBlocking {
            val file = File.createTempFile("dowloadBigMedia", ".jpg")
            createFile(file, smhBigMediaSize)

            val filePath = smhKey(utDirectory.path, "uploadBigMedia.jpg")
            val downloadFileRequest = DownloadFileRequest(
                key = filePath,
                localFullPath = file.absolutePath
            )
            downloadFileRequest.setRange(100)
            downloadFileRequest.stateListener = object : SMHStateListener {
                override fun onStateChange(request: SMHRequest, state: SMHTransferState) {
                    Log.i("testDownloadTask", "onStateChange $state")
                }
            }
            downloadFileRequest.progressListener = object: SMHProgressListener {
                override fun onProgressChange(request: SMHRequest, progress: Long, target: Long) {
                    Log.i("testDownloadTask", "Progress change $progress/$target")
                }
            }
            downloadFileRequest.resultListener = object: SMHResultListener {
                override fun onSuccess(request: SMHRequest, result: SMHResult) {
                    Log.i("testDownloadTask", "onSuccess")
                    if(result is DownloadFileResult){
                        Log.i("testDownloadTask", "bytesTotal: ${result.bytesTotal}")
                        Log.i("testDownloadTask", "content: ${result.content.toString()}")
                        Log.i("testDownloadTask", "crc64: ${result.crc64}")
                        Log.i("testDownloadTask", "key: ${result.key}")
                        Log.i("testDownloadTask", "meta: ${result.meta?.entries?.joinToString()}")
                    }
                }

                override fun onFailure(
                    request: SMHRequest,
                    smhException: SMHException?,
                    smhClientException: SMHClientException?
                ) {
                    Log.i("testDownloadTask", "onFailure $smhException, $smhClientException")
                }
            }
            val downloadTask = SMHDownloadTask(
                context,
                smh,
                downloadFileRequest
            )

            launch {
                delay(5000)
                downloadTask.pause()
            }

            downloadTask.start()

            delay(2000)
            downloadTask.resume()
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

    class MyUriInputStream(
        val uri: Uri
    ): InputStream() {
        var stream: InputStream
        init {
            stream = ContextHolder.getAppContext()?.contentResolver?.openInputStream(uri)
                ?: throw ClientIOException("Open inputStream failed")
        }

        @Throws(IOException::class)
        override fun read(): Int {
            return stream.read()
        }

        override fun read(b: ByteArray?): Int {
            return stream.read(b)
        }

        override fun read(b: ByteArray?, off: Int, len: Int): Int {
            return stream.read(b, off, len)
        }

        override fun markSupported(): Boolean {
            return true
        }

        override fun available(): Int {
            return stream.available()
        }

        override fun close() {
            stream.close()
        }

        override fun skip(n: Long): Long {
            return stream.skip(n)
        }

        override fun reset() {
            stream.close()
            stream = ContextHolder.getAppContext()?.contentResolver?.openInputStream(uri)
                ?: throw ClientIOException("Open inputStream failed")
        }
    }

    class MyFileInputStream(
        val file: File
    ): InputStream() {
        var stream: FileInputStream
        init {
            stream = file.inputStream()
        }

        @Throws(IOException::class)
        override fun read(): Int {
            return stream.read()
        }

        override fun read(b: ByteArray?): Int {
            return stream.read(b)
        }

        override fun read(b: ByteArray?, off: Int, len: Int): Int {
            return stream.read(b, off, len)
        }

        override fun markSupported(): Boolean {
            return true
        }

        override fun available(): Int {
            return stream.available()
        }

        override fun close() {
            stream.close()
        }

        override fun skip(n: Long): Long {
            return stream.skip(n)
        }

        override fun reset() {
            stream.close()
            stream = file.inputStream()
        }
    }
}