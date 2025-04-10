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
 * 快速上传信息
 *
 * @property size 文件大小
 * @property beginningHash 文件头hash
 * @property fullHash 文件整体hash
 * @property labels 标签
 * @property category 分类
 * @property localCreationTime 本地创建时间
 * @property localModificationTime 本地修改时间
 */
data class UploadRequestBody(
    val size: Long? = null,
    val beginningHash: String? = null,
    val fullHash: String? = null,
    val labels: List<String>? = null,
    val category: String? = null,
    val localCreationTime: String? = null,
    val localModificationTime: String? = null
)

/**
 * 分块上传签名PartNumber区域
 * @property partNumberRange 格式为逗号分隔的 区段，区段 可以是 单个数字 n，也可以是 由两个数字组成的范围 n-m
 * 举例来说 1,8-10,20 表示包含以下数字的范围：[1, 8, 9, 10, 20]
 */
data class PartNumberRange(
    @JvmField val partNumberRange: String
)

/**
 * 上传信息
 *
 * @property domain 上传地址域名
 * @property path 上传地址路径
 * @property uploadId 上传 uploadId
 * @property confirmKey 上传确认 key
 * @property headers 分块上传header
 * @property expiration 上传信息有效期，超过有效期后将失效，需要调用分块上传任务续期接口获取新的上传参数
 */
data class InitUpload(
    @JvmField val domain: String,
    @JvmField val path: String,
    @JvmField val uploadId: String?,
    @JvmField val confirmKey: String,
    @JvmField val headers: Map<String, String>,
    @JvmField val expiration: String? = null,
)

/**
 * 分块上传信息
 *
 * @property domain 上传地址域名
 * @property path 上传地址路径
 * @property uploadId 上传 uploadId
 * @property confirmKey 上传确认 key
 * @property parts 分块上传 信息
 * @property expiration 上传信息有效期，超过有效期后将失效，需要调用分块上传任务续期接口获取新的上传参数
 */
data class InitMultipartUpload(
    @JvmField val domain: String,
    @JvmField val path: String,
    @JvmField val uploadId: String?,
    @JvmField val confirmKey: String,
    @JvmField val parts: Map<String, PartsHeaders>,
    @JvmField val expiration: String? = null,
)

/**
 * 分块上传信息
 * @property headers 分块上传header
 */
data class PartsHeaders(
    @JvmField val headers: Map<String, String>
)

/**
 * 上传确认信息
 *
 * @property path 文件最终路径
 */
data class ConfirmUpload(
    @JvmField val path: List<String>,
    @JvmField val name: String,
    @JvmField val contentType: String? = null,
    @JvmField val fileType: FileType? = null,
    @JvmField val crc64: String? = null,
    @JvmField val size: Long? = null,
    @JvmField val type: MediaType? = null,
    @JvmField val previewAsIcon: Boolean? = null,
    @JvmField val previewByCI: Boolean? = null,
    @JvmField val previewByDoc: Boolean? = null,
    @JvmField val creationTime: String? = null,
    @JvmField val modificationTime: String? = null,
    @JvmField val eTag: String? = null,
    @JvmField var metaData: Map<String, String>? = null,
    @JvmField val virusAuditStatus: Int? = null,
) {
    val fileName: String?
        get() = path.lastOrNull()

    val key: String
        get() = path.joinToString(separator = "/")
}

data class ConfirmUploadRequestBody(
    @JvmField val crc64: String? = null,
    @JvmField val labels: List<String>? = null,
    @JvmField val category: String? = null,
    @JvmField val localCreationTime: String? = null,
    @JvmField val localModificationTime: String? = null
)

/**
 * 分片上传信息
 *
 * @property path 文件路径
 * @property parts 已上传的分片列表
 */
data class MultiUploadMetadata(
    @JvmField val path: List<String>,
    @JvmField val parts: List<UploadPart>,
    @JvmField var confirmKey: String,
    @JvmField var confirmed: Boolean?,
)

/**
 * 公有云分片上传信息
 *
 * @property path 文件路径
 * @property parts 已上传的分片列表
 */
data class PublicMultiUploadMetadata(
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