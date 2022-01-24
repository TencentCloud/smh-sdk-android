package com.tencent.cloud.smh.api.model

import com.tencent.cloud.smh.*
import okhttp3.Headers

/**
 * <p>
 * Created by rickenwang on 2021/5/27.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
data class HeadFileContent(
    val type: String?,
    val creationTime: String?,
    val contentType: String?,
    val eTag: String?,
    val size: Long?,
    val crc64: String?,
    val metas: Map<String, String>?
) {

    constructor(
        headers: Map<String, List<String>>
    ) : this(
        headers.getOrElse(X_SMH_TYPE_KEY, { null })?.first(),
        headers.getOrElse(X_SMH_CREATION_TIME_KEY, { null })?.first(),
        headers.getOrElse(X_SMH_CONTENT_TYPE_KEY, { null })?.first(),
        headers.getOrElse(X_SMH_ETAG_KEY, { null })?.first(),
        headers.getOrElse(X_SMH_SIZE_KEY, { null })?.first()?.toLong(),
        headers.getOrElse(X_SMH_CRC64_KEY, { null })?.first(),
        headers.filter {
            it.key.startsWith(X_SMH_META_KEY_PREFIX)
        }.mapValues {
            it.value.first()
        }
    )
}