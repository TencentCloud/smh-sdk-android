//package com.tencent.cloud.smh.api.retrofit.converter
//
//import com.google.gson.Gson
//import com.google.gson.TypeAdapter
//import com.google.gson.reflect.TypeToken
//import okhttp3.MediaType
//import okhttp3.RequestBody
//import okhttp3.ResponseBody
//import okio.Buffer
//import retrofit2.Converter
//import retrofit2.Retrofit
//import java.io.IOException
//import java.io.OutputStreamWriter
//import java.io.Writer
//import java.lang.reflect.Type
//import java.nio.charset.Charset
//
///**
// * <p>
// * Created by rickenwang on 2021/7/30.
// * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
// */
//class RawResponseConverterFactory: Converter.Factory() {
//
//    private val gson = Gson()
//
//    /**
//     * 用 Gson 来处理请求
//     */
//    override fun requestBodyConverter(
//        type: Type?,
//        parameterAnnotations: Array<Annotation?>?,
//        methodAnnotations: Array<Annotation?>?,
//        retrofit: Retrofit?
//    ): Converter<*, RequestBody> {
//        val adapter: TypeAdapter<*> = gson.getAdapter(TypeToken.get(type))
//        return GsonRequestBodyConverter(gson, adapter)
//    }
//
//    /**
//     * 直接返回 body string
//     */
//    override fun responseBodyConverter(
//        type: Type,
//        annotations: Array<out Annotation>,
//        retrofit: Retrofit
//    ): Converter<ResponseBody, *> {
//        return RawResponseBodyConverter<Any>()
//    }
//
//}
//
//
//internal class GsonRequestBodyConverter<T>(
//    private val gson: Gson,
//    private val adapter: TypeAdapter<T>
//) :
//    Converter<T, RequestBody> {
//    @Throws(IOException::class)
//    override fun convert(value: T): RequestBody? {
//        val buffer = Buffer()
//        val writer: Writer = OutputStreamWriter(buffer.outputStream(), UTF_8)
//        val jsonWriter = gson.newJsonWriter(writer)
//        adapter.write(jsonWriter, value)
//        jsonWriter.close()
//        return RequestBody.create(MEDIA_TYPE, buffer.readByteString())
//    }
//
//    companion object {
//        private val MEDIA_TYPE = MediaType.get("application/json; charset=UTF-8")
//        private val UTF_8 = Charset.forName("UTF-8")
//    }
//}
//
//internal class RawResponseBodyConverter<T>(
//) : Converter<ResponseBody, T> {
//
//    override fun convert(value: ResponseBody): T? {
//        return value.string() as T
//    }
//
//
//}