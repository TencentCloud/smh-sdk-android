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

import com.tencent.cloud.smh.SMHException
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * <p>
 * </p>
 * Created by wjielai on 1/29/21.
 * Copyright 2010-2020 Tencent Cloud. All Rights Reserved.
 */
class CallResultAdapterFactory: CallAdapter.Factory() {
    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {

        if (getRawType(returnType) != Call::class.java) {
            return null
        }

        // get the response type inside the `Call` type
        val responseType = getParameterUpperBound(0, returnType as ParameterizedType)

        if (getRawType(responseType) != SMHResponse::class.java) {
            return null
        }
        val dataType = getParameterUpperBound(0, responseType as ParameterizedType)

        val errorBodyConverter =
            retrofit.nextResponseBodyConverter<SMHException>(null, SMHException::class.java, annotations)
        return CallResultAdapter<Any>(dataType, errorBodyConverter)
    }
}