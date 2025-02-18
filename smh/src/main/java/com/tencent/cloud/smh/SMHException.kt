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

package com.tencent.cloud.smh

import com.google.gson.annotations.SerializedName
import okhttp3.Request
import java.math.BigDecimal

/**
 * SMH 异常
 *
 * @property errorCode
 * @property errorMessage
 * @property statusCode
 * @property headers
 * @constructor
 *
 * @param message
 */
open class SMHException(
    @SerializedName("code")
    @JvmField val errorCode: String? = null,
    @SerializedName("inner")
    @JvmField val innerCode: String? = null,
    @SerializedName("message")
    @JvmField val errorMessage: String? = null,

    @SerializedName("requestId")
    @JvmField val requestId: String? = null,
    @SerializedName("smhRequestId")
    @JvmField val smhRequestId: String? = null,

    @JvmField val statusCode: Int = 0,

    message: String = "",

    @JvmField val headers: Map<String, List<String>> = emptyMap(),
): SMHServiceBaseException(message)

class SMHIllegalAccessException(e: SMHException): SMHException(
    errorCode = e.errorCode,
    errorMessage = e.errorMessage,
    statusCode = e.statusCode,
    message = e.message ?: "",
    headers = e.headers
) {
    constructor(message: String): this(SMHException(message = message))
}

class SMHQuotaLimitReachedException(e: SMHException): SMHException(
    errorCode = e.errorCode,
    errorMessage = e.errorMessage,
    statusCode = e.statusCode,
    message = e.message ?: "",
    headers = e.headers
)

class SMHQuotaInsufficientException(val remainSize: BigDecimal?): SMHException(
    errorCode = QuotaLimitReachedErrorCode,
)

val SMHNoUserException = SMHIllegalAccessException("NoUser")

internal const val QuotaLimitReachedErrorCode = "QuotaLimitReached"

/**
 * smh服务端异常基类
 * @property request 请求
 */
open class SMHServiceBaseException: Exception {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)

    @JvmField var request: Request? = null
}