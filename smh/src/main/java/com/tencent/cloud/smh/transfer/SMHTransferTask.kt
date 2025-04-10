/*
 * Copyright (c) 2010-2020 Tencent Cloud. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */
package com.tencent.cloud.smh.transfer

import android.content.Context
import android.util.Log
import bolts.CancellationTokenSource
import com.tencent.cloud.smh.*
import com.tencent.cloud.smh.api.SMHService
import com.tencent.cloud.smh.ext.cosService
import com.tencent.cloud.smh.track.*
import com.tencent.cos.xml.common.ClientErrorCode
import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.exception.CosXmlServiceException
import com.tencent.qcloud.core.logger.QCloudLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import java.math.BigInteger
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * 传输任务
 */
abstract class SMHTransferTask(val context: Context,
                               val smhCollection: SMHCollection,
                               transferRequest: SMHTransferRequest) {

    protected var transferApiProxy: TransferApiProxy
    private var smhProgressListener: SMHProgressListener? = null
    private var smhStateListener: SMHStateListener? = null
    private var smhResultListener: SMHResultListener? = null

    @Volatile var taskState = SMHTransferState.WAITING

    private var transferRequest: SMHTransferRequest
    private var transferResult: SMHTransferResult? = null
    protected var taskId: String

    var verifyContent = true

    @Volatile
    protected var isManualPaused = false

    @Volatile
    protected var isManualCanceled = false

    @Volatile
    protected var mContentLength = -1L

    @Volatile
    protected lateinit var cts: CancellationTokenSource

    // 自定义线程池
    protected var mExecutor: Executor? = null

    val transferApi: TransferApiProxy = TransferApiProxy(
        cosService(context = context) {
            configuration {
                setRegion("ap-guangzhou") // 一个有效值即可
                isHttps(smhCollection.isHttps())
                setDebuggable(smhCollection.isDebuggable)
                setDomainSwitch(false)
            }
        }, smhCollection)

    private val TAG: String
    private var mServerException: SMHException? = null
    private var mClientException: SMHClientException? = null
    protected var smhKey: String
    // 内部异常是否已经重试
    private var internalExceptionRetry = false

    init {
        transferApiProxy = transferApi
        taskId = UUID.randomUUID().toString()
        this.transferRequest = transferRequest
        smhKey = transferRequest.key
        TAG = tag()
        QCloudLogger.i(
            TAG, "[%s]: create a %s task, key: %s",
            taskId, transferRequest.javaClass.simpleName, transferRequest.key
        )
    }

    // 启动传输
    open suspend fun start() {
        startInit()
        handle()
    }

    //恢复传输
    open suspend fun resume() {
        startInit()
        handle()
    }

    private fun startInit(){
        smhStateListener = transferRequest.stateListener
        smhProgressListener = transferRequest.progressListener
        smhResultListener = transferRequest.resultListener
        isManualPaused = false
        isManualCanceled = false
        mServerException = null
        mClientException = null
        onTransferWaiting()
        cts = CancellationTokenSource()
    }

//    fun setExecutor(executor: Executor) {
//        mExecutor = executor
//    }

    protected abstract fun tag(): String

    private fun getTrackEventCode(): String? {
        var trackEventCode: String? = null
        if (this is SMHUploadTask) {
            trackEventCode = UploadEventCode
        } else if (this is SMHDownloadTask) {
            trackEventCode = DownloadEventCode
        }
        return trackEventCode
    }

    private fun getLocalUri(transferRequest: SMHTransferRequest): String? {
        return if (transferRequest is UploadFileRequest) {
            transferRequest.localUri.toString()
        } else if (transferRequest is DownloadFileRequest) {
            transferRequest.localFullPath
        } else {
            null
        }
    }

    private suspend fun handle() {
        val eventCode = getTrackEventCode()
        val startTime = System.currentTimeMillis()
        try {
            val start = System.nanoTime()
            checking()
            Log.e(TAG,
                "checking_time=${
                    java.lang.String.valueOf(
                        TimeUnit.MILLISECONDS.convert(
                            System.nanoTime() - start,
                            TimeUnit.NANOSECONDS
                        )
                    )}")
            onTransferRunBefore()
            runBefore()
            onTransferInProgress()
            execute()
            onTransferRunAfter()
            transferResult = runAfter()

            // 上报传输成功
            eventCode?.let {
                SuccessTransferTrackEvent(
                    eventCode = it,
                    smhUser = smhCollection.user,
                    smhPath = transferRequest.key,
                    localUri = getLocalUri(transferRequest)?: "",
                    tookTime = System.currentTimeMillis() - startTime,
                    contentLength = mContentLength
                ).trackWithBeaconParams(context)
            }

            onTransferSuccess(transferRequest, transferResult!!)
        } catch (exception: SMHClientException) {
            exception.printStackTrace()
            QCloudLogger.e(tag(), "transfer with SMHClientException: code ${exception.code}, message is ${exception.message}")
            // 手动取消和暂停报错不在这里回调，否则可能会长时间阻塞
            mClientException = exception
        } catch (exception: SMHException) {
            exception.printStackTrace()
            QCloudLogger.e(tag(), "transfer with SMHException: code ${exception.errorCode}, message is ${exception.errorMessage}, smhRequestId is ${exception.smhRequestId}, requestId is ${exception.requestId}")
            mServerException = exception
        } catch (exception: CosXmlClientException) {
            exception.printStackTrace()
            QCloudLogger.e(tag(), "transfer with CosXmlClientException: code ${exception.errorCode}, message is ${exception.message}")
            mClientException = when(exception.errorCode) {
                    10004, 20002, 20003 -> PoorNetworkException
                    10000, 10002 -> if (exception.message == "upload file does not exist") {
                        FileNotFoundException
                    } else {
                        InvalidArgumentException
                    }
                    10001 -> InvalidCredentialsException
                    30000 -> ClientManualCancelException
                    ClientErrorCode.SINK_SOURCE_NOT_FOUND.code -> FileNotFoundException
                    else -> ClientInternalException(exception.message)
                }

        } catch (exception: CosXmlServiceException) {
            QCloudLogger.e(tag(), "transfer with CosXmlServiceException: code ${exception.errorCode}, message is ${exception.errorMessage}, requestId is ${exception.requestId}")
            mServerException = SMHException(errorCode = exception.errorCode, errorMessage = exception.errorMessage, requestId = exception.requestId?: "", statusCode = exception.statusCode, message = exception.message?: "")
        } catch (exception: Exception) {
            exception.printStackTrace()
            QCloudLogger.e(tag(), "transfer with Exception: message is ${exception.message}, stackTrace is ${exception.stackTrace}")
            mClientException = ClientInternalException(exception.message)

        } finally {
            if (!isManualPaused && !isManualCanceled) {
                if(!internalExceptionRetry && mClientException is ClientInternalException){
                    internalExceptionRetry = true
                    resume()
                }

                if (mClientException != null || mServerException != null) {
                    onTransferFailed(transferRequest, mClientException, mServerException)
                    Log.e("QCloudHttp", "client ${mClientException}, server ${mServerException}")
                    // 上报传输失败
                    eventCode?.let {
                        FailureTransferTrackEvent(
                            eventCode = it,
                            smhUser = smhCollection.user,
                            smhPath = transferRequest.key,
                            localUri = getLocalUri(transferRequest)?: "",
                            tookTime = System.currentTimeMillis() - startTime,
                            contentLength = mContentLength,
                            exception = mClientException?: mServerException?: Exception("UnknownException")
                        ).trackWithBeaconParams(context)
                    }
                }
            }
        }
    }

    fun checkIfSuccess() {
        val serviceException = mServerException
        val clientException = mClientException
        if (serviceException != null) {
            throw serviceException
        } else if (clientException != null) {
            throw clientException
        }
    }


    fun getResultOrThrow(): SMHTransferResult {
        checkIfSuccess()
        return transferResult?: throw SMHClientException("TransferResultNotFound")
    }

    @JvmOverloads
    fun future(
        context: CoroutineContext = Dispatchers.IO,
        scope: CoroutineScope = GlobalScope,
    ) = TransferTaskFuture(this, context, scope)

    /**
     * 检查传输参数，并计算额外参数
     *
     * @throws SMHClientException
     */
    @Throws(SMHClientException::class)
    protected abstract fun checking()

    protected open suspend fun runBefore() = Unit

    @Throws(SMHClientException::class, SMHException::class)
    protected abstract suspend fun execute()

    /**
     * 运行结束后返回结果，一般用于校验等
     */
    protected abstract suspend fun runAfter(): SMHTransferResult

    protected fun onTransferWaiting() {
        taskState = SMHTransferState.WAITING
        notifySMHTransferStateChange()
    }

    protected fun onTransferRunBefore() {
        taskState = SMHTransferState.RUN_BEFORE
        notifySMHTransferStateChange()
    }

    protected fun onTransferInProgress() {
        taskState = SMHTransferState.RUNNING
        notifySMHTransferStateChange()
    }

    protected fun onTransferPaused() {
        taskState = SMHTransferState.PAUSED
        notifySMHTransferStateChange()
    }

    protected fun onTransferRunAfter() {
        taskState = SMHTransferState.RUN_AFTER
        notifySMHTransferStateChange()
    }

    protected open fun onTransferSuccess(
        transferRequest: SMHTransferRequest,
        transferResult: SMHTransferResult
    ) {
        this.transferRequest = transferRequest
        this.transferResult = transferResult
        taskState = SMHTransferState.COMPLETE
        notifySMHTransferStateChange()
        notifySMHTransferResultSuccess(transferRequest, transferResult)
    }

    protected open fun onTransferFailed(
        transferRequest: SMHTransferRequest, clientException: SMHClientException?,
        smhException: SMHException?
    ) {
        if(taskState == SMHTransferState.FAILURE) return

        this.mClientException = clientException
        this.mServerException = smhException
        taskState = SMHTransferState.FAILURE
        notifySMHTransferStateChange()
        notifySMHTransferResultFailed(transferRequest, clientException, smhException)
    }

    protected fun onTransferProgressChange(complete: Long, target: Long) {
        notifyTransferProgressChange(complete, target)
    }

    private fun notifySMHTransferStateChange() {
        smhStateListener?.onStateChange(transferRequest, taskState)
        Log.e(TAG, "transfer_State=$taskState")
    }

    private fun notifyTransferProgressChange(complete: Long, target: Long) {
        if (smhProgressListener != null &&
            (taskState === SMHTransferState.RUNNING || taskState === SMHTransferState.RUN_BEFORE)) {
            smhProgressListener!!.onProgressChange(transferRequest, complete, target)
        }
//        QCloudLogger.i(TAG, "transfer_Progress = $complete-$target")
    }

    private fun notifySMHTransferResultSuccess(
        transferRequest: SMHTransferRequest,
        transferResult: SMHTransferResult
    ) {
        smhResultListener?.onSuccess(transferRequest, transferResult)
    }

    private fun notifySMHTransferResultFailed(
        transferRequest: SMHTransferRequest, clientException: SMHClientException?,
        smhException: SMHException?
    ) {
        smhResultListener?.onFailure(transferRequest, smhException, clientException)
    }

//    @Throws(SMHClientException::class, SMHException::class)
//    protected fun throwException(e: Exception?) {
//        if (e is SMHClientException) {
//            throw (e as SMHClientException?)!!
//        } else if (e is SMHException) {
//            throw (e as SMHException?)!!
//        } else if (e != null) {
//            throw ClientInternalException(e.message)
//        } else {
//            throw ClientInternalException("smh sdk encounter unknown error")
//        }
//    }
//
//    protected class TaskThreadFactory internal constructor(
//        private val tag: String,
//        private val priority: Int
//    ) : ThreadFactory {
//        private val increment = AtomicInteger(1)
//        override fun newThread(runnable: Runnable): Thread {
//            val newThread = Thread(runnable, tag + increment.getAndIncrement())
//            newThread.isDaemon = false
//            newThread.priority = priority
//            return newThread
//        }
//    }


}

private val TWO_64 = BigInteger.ONE.shiftLeft(64)

fun Long.toUnsignedLong(): String {
    var b = BigInteger.valueOf(this)
    if (b.signum() < 0) {
        b = b.add(TWO_64)
    }
    return b.toString()
}
