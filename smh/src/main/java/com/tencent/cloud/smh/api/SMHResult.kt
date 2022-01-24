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

package com.tencent.cloud.smh.api

import java.lang.IllegalStateException

/**
 * <p>
 * </p>
 */
sealed class SMHResult<out T> {
    data class Success<out T>(val data: T) : SMHResult<T>()
    data class Failure(val e: Throwable) : SMHResult<Nothing>()
    object Loading: SMHResult<Nothing>()
}

fun <T> SMHResult<T>.isSuccess(): Boolean {
    return this is SMHResult.Success
}

fun <T> SMHResult<T>.isFailure(): Boolean {
    return this is SMHResult.Failure
}

fun <T> SMHResult<T>.error(): Throwable? {
    return (this as? SMHResult.Failure)?.e
}

val <T> SMHResult<T>.dataOrNull: T?
    get() = (this as? SMHResult.Success)?.data

val <T> SMHResult<T>.data: T
    get() = when (this) {
        is SMHResult.Success -> this.data?: throw IllegalAccessException("data is null")
        is SMHResult.Failure -> throw this.e
        is SMHResult.Loading -> throw IllegalStateException("result is loading")
    }