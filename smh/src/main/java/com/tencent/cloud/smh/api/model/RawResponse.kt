package com.tencent.cloud.smh.api.model

/**
 * @author Created by jordanqin.
 *
 * @date Created in 2022/4/26 3:23 下午
 *
 * @description：原始响应
 *
 * Copyright 2010-2022 Tencent Cloud. All Rights Reserved.
 */
data class RawResponse(
    val rawString: String?,
    var statusCode: Int? = null
)