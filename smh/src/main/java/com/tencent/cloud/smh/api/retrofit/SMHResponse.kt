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

package com.tencent.cloud.smh.api.retrofit

import com.tencent.cloud.smh.SMHException
import com.tencent.cloud.smh.SMHServiceBaseException

/**
 * <p>
 * </p>
 */
sealed class SMHResponse<out T> {

    data class Success<out T>(val result: T?, val headers: Map<String, List<String>>, val code: Int) : SMHResponse<T>()

    data class ApiError(val err: SMHException,
                        val headers: Map<String, List<String>>,
                        val code: Int): SMHResponse<Nothing>()

    data class NetworkError(val err: SMHServiceBaseException): SMHResponse<Nothing>()

    data class UnknownError(val err: SMHServiceBaseException): SMHResponse<Nothing>()
}

val <T> SMHResponse<T>.isSuccess: Boolean

    get() = this is SMHResponse.Success

//val <T> SMHResponse<T>.dataOrNull: T?
//    get() = (this as? SMHResponse.Success)?.data

fun <T> SMHResponse<T>.code(): Int  = when (this) {
    is SMHResponse.Success -> this.code
    is SMHResponse.ApiError -> this.code
    is SMHResponse.NetworkError -> throw this.err
    is SMHResponse.UnknownError -> throw this.err
}

fun <T> SMHResponse<T>.header(key: String): String?  = when (this) {
        is SMHResponse.Success -> this.headers[key]?.firstOrNull()
        is SMHResponse.ApiError -> this.headers[key]?.firstOrNull()
        is SMHResponse.NetworkError -> throw this.err
        is SMHResponse.UnknownError -> throw this.err
    }

fun <T> SMHResponse<T>.headers(): Map<String, List<String>>  = when (this) {
    is SMHResponse.Success -> this.headers
    is SMHResponse.ApiError -> this.headers
    is SMHResponse.NetworkError -> throw this.err
    is SMHResponse.UnknownError -> throw this.err
}

fun <T> SMHResponse<T>.checkSuccess()
    = when (this) {
        is SMHResponse.Success -> true
        is SMHResponse.ApiError -> throw this.err
        is SMHResponse.NetworkError -> throw this.err
        is SMHResponse.UnknownError -> throw this.err
    }

val <T> SMHResponse<T>.data: T
    get() = when (this) {
        is SMHResponse.Success -> this.result ?: throw IllegalAccessException("data is null")
        is SMHResponse.ApiError -> throw this.err
        is SMHResponse.NetworkError -> throw this.err
        is SMHResponse.UnknownError -> throw this.err
    }

val <T> SMHResponse<T>.checkSuccess: T?
    get() = when (this) {
        is SMHResponse.Success -> this.result
        is SMHResponse.ApiError -> throw this.err
        is SMHResponse.NetworkError -> throw this.err
        is SMHResponse.UnknownError -> throw this.err
    }

val <T> SMHResponse<T>.statusCode: Int?
    get() = (this as? SMHResponse.ApiError)?.err?.statusCode

//val <T> SMHResponse<T>.errorCode: String?
//    get() = (this as? SMHResponse.ApiError)?.err?.errorCode
