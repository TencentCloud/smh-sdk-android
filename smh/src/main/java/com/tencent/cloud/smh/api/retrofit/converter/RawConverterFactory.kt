package com.tencent.cloud.smh.api.retrofit.converter

import com.tencent.cloud.smh.api.model.RawResponse
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * @author Created by jordanqin.
 *
 * @date Created in 2022/4/26 2:52 下午
 *
 * @description：返回原始string响应
 *
 * Copyright 2010-2022 Tencent Cloud. All Rights Reserved.
 */
class RawConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, RawResponse>? {
        if (type != RawResponse::class.java) return null
        return Converter { body ->
            RawResponse(body.string())
        }
    }
}