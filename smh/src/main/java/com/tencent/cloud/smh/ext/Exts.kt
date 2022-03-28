package com.tencent.cloud.smh.ext

import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.utils.URLEncodeUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
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
                cont.resume(block())
            } catch (exception: Exception) {
                exception.printStackTrace()
                cont.resumeWithException(exception)
            }
        }
    }
}