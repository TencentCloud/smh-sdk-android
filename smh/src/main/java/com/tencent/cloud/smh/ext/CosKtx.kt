package com.tencent.cloud.smh.ext

import android.content.Context
import com.tencent.cloud.smh.api.SMHService
import com.tencent.cos.xml.CosXmlBaseService
import com.tencent.cos.xml.CosXmlServiceConfig
import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.exception.CosXmlServiceException
import com.tencent.cos.xml.listener.CosXmlResultListener
import com.tencent.cos.xml.model.CosXmlRequest
import com.tencent.cos.xml.model.CosXmlResult
import com.tencent.qcloud.core.auth.BasicLifecycleCredentialProvider
import com.tencent.qcloud.core.auth.QCloudCredentialProvider
import com.tencent.qcloud.core.auth.QCloudLifecycleCredentials
import com.tencent.qcloud.core.http.QCloudHttpClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation

/**
 * @author Created by jordanqin.
 *
 * @date Created in 2022/5/17 1:27 上午
 *
 * @description：
 *
 * Copyright 2010-2022 Tencent Cloud. All Rights Reserved.
 */

class COSServiceBuilder(val context: Context) {
    var cp: QCloudCredentialProvider? = null
        private set
    var cosXmlConfig: CosXmlServiceConfig? = null
        private set

    fun configuration(init: CosXmlServiceConfig.Builder.() -> Unit) {
        val builder = CosXmlServiceConfig.Builder().apply(init)
        cosXmlConfig = builder.builder()
    }

    fun credentialProvider(callback: () -> QCloudCredentialProvider) {
        cp = callback()
    }

    fun lifecycleCredentialProvider(callback: () -> QCloudLifecycleCredentials):
            QCloudCredentialProvider {
        return object : BasicLifecycleCredentialProvider() {
            override fun fetchNewCredentials(): QCloudLifecycleCredentials {
                return callback()
            }
        }
    }
}

fun cosService(context: Context, init: COSServiceBuilder.() -> Unit): CosXmlBaseService {
    val builder = COSServiceBuilder(context).apply(init)
    val config = builder.cosXmlConfig ?: throw java.lang.IllegalArgumentException("config is null")
    val provider = builder.cp
    CosXmlBaseService.BRIDGE = "SMH"
    val cosXmlBaseService = CosXmlBaseService(builder.context, config, provider)
    cosXmlBaseService.addCustomerDNSFetch(QCloudHttpClient.QCloudDnsFetch { hostname ->
        SMHService.dnsFetch?.fetch(hostname)
    })
    return cosXmlBaseService
}

suspend inline fun <T> suspendBlock(crossinline block: (listener: CosXmlResultListener) -> Unit): T where T : CosXmlResult {
    return suspendCancellableCoroutine<T> { cont ->
        block(cosXmlListenerWrapper(cont))
    }
}


fun <T> cosXmlListenerWrapper(cont: Continuation<T>): CosXmlResultListener where T : CosXmlResult {
    return object : CosXmlResultListener {
        override fun onSuccess(p0: CosXmlRequest?, p1: CosXmlResult?) {
            cont.resumeIfActive(p1 as T)
        }

        override fun onFail(p0: CosXmlRequest?, p1: CosXmlClientException?, p2: CosXmlServiceException?) {
            cont.resumeWithExceptionIfActive(p1 ?: p2!!)
        }
    }
}

