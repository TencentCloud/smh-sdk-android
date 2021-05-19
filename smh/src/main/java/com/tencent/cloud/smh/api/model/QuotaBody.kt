package com.tencent.cloud.smh.api.model

/**
 * <p>
 * </p>
 * Created by wjielai on 5/19/21.
 * Copyright 2010-2020 Tencent Cloud. All Rights Reserved.
 */
data class QuotaBody(
    val spaces: Array<String>? = null,
    val capacity: String,
    val removeWhenExceed: Boolean,
    val removeAfterDays: Int,
    val removeNewest: Boolean
)

data class QuotaResponse(val quotaId: Long)