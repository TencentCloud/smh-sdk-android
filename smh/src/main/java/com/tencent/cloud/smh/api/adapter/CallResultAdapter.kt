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
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Converter
import java.lang.reflect.Type

/**
 * <p>
 * </p>
 * Created by wjielai on 1/29/21.
 * Copyright 2010-2020 Tencent Cloud. All Rights Reserved.
 */
class CallResultAdapter<S : Any>(
    private val dataType: Type,
    private val errorBodyConverter: Converter<ResponseBody, SMHException>
) : CallAdapter<S, Call<SMHResponse<S>>> {

    override fun adapt(call: Call<S>): Call<SMHResponse<S>> {
        return SMHNetworkCall(
            call,
            errorBodyConverter
        )
    }

    override fun responseType(): Type {
        return dataType
    }

}