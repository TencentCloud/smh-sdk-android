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

package com.tencent.cloud.smh.api.adapter

import com.tencent.cloud.smh.api.*
import com.tencent.cloud.smh.QuotaLimitReachedErrorCode
import com.tencent.cloud.smh.SMHException
import com.tencent.cloud.smh.SMHIllegalAccessException
import com.tencent.cloud.smh.SMHQuotaLimitReachedException
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Converter
import retrofit2.Response
import java.io.IOException

/**
 * <p>
 * </p>
 */
class SMHNetworkCall<S: Any>(
    private val call: Call<S>,
    private val errorConverter: Converter<ResponseBody, SMHException>
): Call<SMHResponse<S>> {

    override fun enqueue(callback: Callback<SMHResponse<S>>) {
        return call.enqueue(object : Callback<S> {
            override fun onResponse(call: Call<S>, response: Response<S>) {
                val body = response.body()
                val error = response.errorBody()

                if (response.isSuccessful) {
                    callback.onResponse(
                        this@SMHNetworkCall,
                        Response.success(SMHResponse.Success(body, response.headers().toMultimap()))
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
                    callback.onResponse(
                        this@SMHNetworkCall,
                        Response.success(SMHResponse.ApiError(smhException, response.headers().toMultimap()))
                    )
                }
            }

            override fun onFailure(call: Call<S>, throwable: Throwable) {
                val networkResponse = when (throwable) {
                    is IOException -> SMHResponse.NetworkError(throwable)
                    else -> SMHResponse.UnknownError(throwable)
                }

                callback.onResponse(this@SMHNetworkCall, Response.success(networkResponse))
            }
        })
    }

    override fun isExecuted(): Boolean {
        return call.isExecuted
    }

    override fun timeout(): Timeout {
        return call.timeout()
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


}