package com.tencent.cloud.smh.ext

import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.utils.URLEncodeUtils
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * <p>
 * Created by rickenwang on 2021/10/18.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
fun String.cosPathEncode(): String {
    return try {
        URLEncodeUtils.cosPathEncode(this)
    } catch (e: CosXmlClientException){
        this
    }
}


suspend fun <T> Executor.runWithSuspend(block: () -> T): T {

    return suspendCancellableCoroutine<T> { cont ->
        execute {
            try {
                cont.resumeIfActive(block())
            } catch (exception: Exception) {
                exception.printStackTrace()
                cont.resumeWithExceptionIfActive(exception)
            }
        }
    }
}

fun Date.formatToUtc(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(this)
}

fun <T> Continuation<T>.resumeIfActive(value: T) {
    if(this is CancellableContinuation) {
        if(this.isActive) {
            this.resume(value)
        }
    } else {
        this.resume(value)
    }
}

fun <T> Continuation<T>.resumeWithExceptionIfActive(exception: Throwable) {
    if(this is CancellableContinuation) {
        if(this.isActive) {
            this.resumeWithException(exception)
        }
    } else {
        this.resumeWithException(exception)
    }
}