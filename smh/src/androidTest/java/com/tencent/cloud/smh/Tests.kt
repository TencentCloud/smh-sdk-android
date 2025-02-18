package com.tencent.cloud.smh

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tencent.cloud.smh.ext.resumeIfActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume

/**
 * <p>
 * Created by jordanqin on 2024/3/19 19:26.
 * Copyright 2010-2024 Tencent Cloud. All Rights Reserved.
 */
@RunWith(AndroidJUnit4::class)
class Tests {
    @Test
    fun testResume() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val result = suspendCancellableCoroutine<String> {
                    println("suspendCancellableCoroutine 正在执行")
//                    it.resume("返回值")
                    it.resumeIfActive("返回值")
                    println("suspendCancellableCoroutine 已经返回")
//                    it.resume("返回值2")
                    it.resumeIfActive("返回值2")
                    println("suspendCancellableCoroutine 再次返回")
                }
                println("suspendCancellableCoroutine 执行成功，返回结果：$result")
            } catch (e: java.lang.Exception) {
                println("suspendCancellableCoroutine 执行失败，返回异常：$e")
            }
        }
    }
}