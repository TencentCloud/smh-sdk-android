package com.tencent.cloud.smh.api.retrofit.converter

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.tencent.cloud.smh.api.model.HighLight
import retrofit2.converter.gson.GsonConverterFactory


/**
 * @author Created by jordanqin.
 *
 * @date Created in 2022/10/26 17:02
 *
 * @description：
 *
 * Copyright 2010-2022 Tencent Cloud. All Rights Reserved.
 */
object GsonConverter {
    @JvmStatic
    fun buildGsonConverterFactory(): GsonConverterFactory {
        val defaultGson = Gson()
        val gson = GsonBuilder()
            .registerTypeAdapter(HighLight::class.java, JsonDeserializer<HighLight> { j, t, c ->
                val isEmptyString = j.isJsonPrimitive && j.asJsonPrimitive.isString && j.asString == ""
                if (t == HighLight::class.java && isEmptyString) {
                    return@JsonDeserializer null
                } else {
                    return@JsonDeserializer defaultGson.fromJson(j, t)
                }
            })
//                .registerTypeHierarchyAdapter(Any::class.java, JsonDeserializer<Any> { j, t, c ->
//                    //非String类型的字段 接收到""后一律为空
//                    val jCopy = j.deepCopy()
//                    if (t != String::class.java
//                        && jCopy.isJsonPrimitive && (jCopy as JsonPrimitive).isString && jCopy.asString == ""){
//                        return@JsonDeserializer null
//                    } else {
//                        return@JsonDeserializer defaultGson.fromJson(j, t)
//                    }
//                })
            .create()

        return GsonConverterFactory.create(gson)
    }
}