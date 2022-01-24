/*
 *
 *  * Copyright (C) 2021 Tencent, Inc.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.tencent.cloud.smh.api.model

import com.google.gson.annotations.SerializedName

/**
 * 上传信息
 *
 * @property domain 上传地址域名
 * @property path 上传地址路径
 * @property uploadId 上传 uploadId
 * @property confirmKey 上传确认 key
 * @property headers 上传 headers
 */
data class InitUpload(
    @JvmField val domain: String,
    @JvmField val path: String,
    @JvmField val uploadId: String?,
    @JvmField val confirmKey: String,
    @JvmField val headers: Map<String, String>,
)

/**
 * 上传确认信息
 *
 * @property path 文件最终路径
 */
data class ConfirmUpload(
    @JvmField val path: List<String>,
    @JvmField val name: String,
    @JvmField val contentType: String?,
    @JvmField val fileType: FileType?,
    @JvmField val crc64: String?,
    @JvmField val size: Long?,
    @JvmField val type: MediaType? = null,
    @JvmField val previewAsIcon: Boolean? = null,
    @JvmField val previewByCI: Boolean? = null,
    @JvmField val previewByDoc: Boolean? = null,
    @JvmField val creationTime: String? = null,
    @JvmField val modificationTime: String? = null,
    @JvmField val eTag: String? = null,
    @JvmField val metaData: Map<String, String>? = null,
) {
    val fileName: String?
        get() = path.lastOrNull()

    val key: String
        get() = path.joinToString(separator = "/")
}

data class ConfirmUploadRequestBody(
    @JvmField val crc64: String
)

/**
 * 分片上传信息
 *
 * @property path 文件路径
 * @property parts 已上传的分片列表
 * @property uploader 上传信息
 */
data class MultiUploadMetadata(
    @JvmField val path: List<String>,
    @JvmField val parts: List<UploadPart>,
    @SerializedName("uploadPartInfo")
    @JvmField val uploader: InitUpload,
    @JvmField var confirmKey: String,
    @JvmField var confirmed: Boolean?,
)

/**
 * 已上传的分片信息
 *
 * @property partNumber 分片序号
 * @property lastModified 最后修改时间
 * @property ETag 分片 ETag
 * @property size 分片大小
 */
data class UploadPart(
    @SerializedName("PartNumber")
    @JvmField val partNumber: Int,
    @SerializedName("LastModified")
    @JvmField val lastModified: String,
    @SerializedName("ETag")
    @JvmField val ETag: String,
    @SerializedName("Size")
    @JvmField val size: Long,
)