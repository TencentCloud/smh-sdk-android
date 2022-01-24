package com.tencent.cloud.smh.transfer

import com.tencent.cos.xml.CosXmlSimpleService
import com.tencent.cos.xml.model.CosXmlRequest

/**
 * <p>
 * Created by rickenwang on 2021/7/19.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
class CancelHandler(var cos: CosXmlSimpleService? = null, private val requests: MutableList<CosXmlRequest> = mutableListOf<CosXmlRequest>()) {

    @Synchronized fun addRequests(requests: List<CosXmlRequest>) {
        this.requests.addAll(requests)
    }

    @Synchronized fun removeRequest(request: CosXmlRequest) {
        this.requests.remove(request)
    }

    @Synchronized fun cancel() {
        requests.forEach {
            cos?.cancel(it)
        }
    }
}