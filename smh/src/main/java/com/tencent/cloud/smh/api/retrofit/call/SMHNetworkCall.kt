/*
 *
 *  * Copyright (C) 2021 Tencent, Inc.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.tencent.cloud.smh.api.retrofit.call

import com.tencent.cloud.smh.DATE_KEY
import com.tencent.cloud.smh.QuotaLimitReachedErrorCode
import com.tencent.cloud.smh.SMHException
import com.tencent.cloud.smh.SMHIllegalAccessException
import com.tencent.cloud.smh.SMHQuotaLimitReachedException
import com.tencent.cloud.smh.SMHServiceBaseException
import com.tencent.cloud.smh.api.*
import com.tencent.cloud.smh.api.retrofit.SMHResponse
import com.tencent.qcloud.core.http.CallMetricsListener
import com.tencent.qcloud.core.http.HttpConfiguration
import com.tencent.qcloud.core.http.HttpConstants
import com.tencent.qcloud.core.http.QCloudHttpClient
import com.tencent.qcloud.core.http.interceptor.RetryInterceptor
import com.tencent.qcloud.core.logger.QCloudLogger
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Converter
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Field
import java.util.Date

/**
 * <p>
 * </p>
 */
class SMHNetworkCall<S : Any>(
    private val call: Call<S>,
    private val errorConverter: Converter<ResponseBody, SMHException>
) : Call<SMHResponse<S>> {
    val TAG = "SMHNetworkCall"
    private var eventListenerFiled: Field? = null
    private var rawCallFiled: Field? = null
    override fun enqueue(callback: Callback<SMHResponse<S>>) {
        return call.enqueue(object : Callback<S> {
            override fun onResponse(call: Call<S>, response: Response<S>) {
                val body = response.body()
                val error = response.errorBody()

                if (response.isSuccessful || response.code() == 302) {
                    // 时间校正
                    response.headers().get(DATE_KEY)?.apply {
                        HttpConfiguration.calculateGlobalTimeOffset(
                            this,
                            Date(),
                            600
                        )
                    }

                    callback.onResponse(
                        this@SMHNetworkCall,
                        Response.success(
                            SMHResponse.Success(
                                body,
                                response.headers().toMultimap(),
                                response.code()
                            )
                        )
                    )
                } else {
                    val serverException = when {
                        error == null -> null
                        error.contentLength() == 0L -> null
                        else -> try {
                            errorConverter.convert(error)
                        } catch (ex: Exception) {
                            null
                        }
                    }
                    var smhException = SMHException(
                        errorCode = serverException?.errorCode,
                        errorMessage = serverException?.errorMessage,
                        innerCode = serverException?.innerCode,
                        requestId = serverException?.requestId,
                        smhRequestId = serverException?.smhRequestId,
                        statusCode = response.code(),
                        message = "${response.message()} - ${serverException?.errorMessage}",
                        headers = response.headers().toMultimap()
                    )
                    if (response.code() == 401 || response.code() == 403) {
                        // 访问权限问题
                        smhException = SMHIllegalAccessException(smhException)
                    } else if (response.code() == 400 && serverException?.errorCode == QuotaLimitReachedErrorCode) {
                        // 配额错误
                        smhException = SMHQuotaLimitReachedException(smhException)
                    }
                    smhException.request = call.request()
                    callback.onResponse(
                        this@SMHNetworkCall,
                        Response.success(
                            SMHResponse.ApiError(
                                smhException,
                                response.headers().toMultimap(),
                                response.code()
                            )
                        )
                    )
                    logException(smhException)
                }

                logCallMetrics()
            }

            override fun onFailure(call: Call<S>, throwable: Throwable) {
                val serviceException = SMHServiceBaseException(throwable)
                serviceException.request = call.request()
                val networkResponse = when (throwable) {
                    is IOException -> {
                        SMHResponse.NetworkError(serviceException)
                    }
                    else -> {
                        SMHResponse.UnknownError(serviceException)
                    }
                }
                logException(serviceException)
                callback.onResponse(this@SMHNetworkCall, Response.success(networkResponse))

                logCallMetrics()
            }
        })
    }

    private fun logException(e: SMHServiceBaseException){
        QCloudLogger.i(TAG, e.request.toString())
        e.printStackTrace()
    }

    override fun isExecuted(): Boolean {
        return call.isExecuted
    }

    override fun clone(): Call<SMHResponse<S>> {
        return SMHNetworkCall(call.clone(), errorConverter)
    }

    override fun isCanceled(): Boolean {
        return call.isCanceled
    }

    override fun cancel() {
        return call.cancel()
    }

    override fun execute(): Response<SMHResponse<S>> {
        throw UnsupportedOperationException("NetworkResponseCall doesn't support execute")
    }

    override fun request(): Request {
        return call.request()
    }

    /**
     * 反射获取eventListener 并进行打印
     */
    private fun logCallMetrics(){
        var eventListener: CallMetricsListener? = null
        var rawCall: okhttp3.Call? = null
        try {
            if(rawCallFiled == null) {
                rawCallFiled = call.javaClass.getDeclaredField("rawCall")
                rawCallFiled?.apply{
                    this.isAccessible = true
                    rawCall = this.get(call) as okhttp3.Call?
                    rawCall?.apply {
                        if (eventListenerFiled == null) {
                            eventListenerFiled = this.javaClass.getDeclaredField("eventListener")
                            eventListenerFiled?.apply {
                                this.isAccessible = true
                                eventListener = this.get(rawCall) as CallMetricsListener
                            }
                        }
                    }
                }
            }
        } catch (ignore: NoSuchFieldException) { } catch (ignore: IllegalAccessException) { } catch (ignore: ClassCastException) { }
        eventListener?.apply {
            QCloudLogger.i(
                QCloudHttpClient.HTTP_LOG_TAG,
                eventListener.toString()
            )
        }
    }
}
