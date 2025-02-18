package com.tencent.cloud.smh.api.retrofit.converter

import com.google.gson.annotations.SerializedName
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type


/**
 * <p>
 * Created by rickenwang on 2021/8/2.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
class EnumConverterFactory : Converter.Factory() {
    override fun stringConverter(type: Type?, annotations: Array<out Annotation>?,
                                 retrofit: Retrofit?): Converter<*, String>? {
        if (type is Class<*> && type.isEnum) {
            return Converter<Any?, String> { value -> getSerializedNameValue(value as Enum<*>) }
        }
        return null
    }
}

fun <E : Enum<*>> getSerializedNameValue(e: E): String {
    try {
        return e.javaClass.getField(e.name).getAnnotation(SerializedName::class.java).value
    } catch (exception: NoSuchFieldException) {
        exception.printStackTrace()
    }

    return ""
}
