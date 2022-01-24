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

open class SMHClientException(
    val code: String,
    message: String? = null
): Exception(message)

const val InternalError = "InternalError"
const val ManualCanceled = "ManualCanceled"
const val PoorNetwork = "PoorNetwork"
const val InvalidArgument = "InvalidArgument"
const val InvalidCredentials = "InvalidCredentials"
const val FileCRC64InConsist = "FileCRC64InConsist"
const val FileNotFound = " FileNotFound"

class ClientInternalException(
    message: String?
): SMHClientException(
    InternalError, message
)


val ClientManualCancelException = SMHClientException(
    ManualCanceled, "you cancel this request by yourself"
)

val PoorNetworkException = SMHClientException(
    PoorNetwork, "poor network"
)

val InvalidArgumentException = SMHClientException(
    InvalidArgument, "invalid argument"
)

val InvalidCredentialsException = SMHClientException(
    InvalidCredentials, "invalid credentials"
)

val FileCRC64InConsistException = SMHClientException(
    FileCRC64InConsist, "file crc64 inconsistent"
)

val FileNotFoundException = SMHClientException(
    FileNotFound, "file not found"
)


class ClientIOException(
    message: String?
): SMHClientException(
    "ClientIOError", message
)