package com.tencent.cloud.smh.ext

import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.utils.URLEncodeUtils

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