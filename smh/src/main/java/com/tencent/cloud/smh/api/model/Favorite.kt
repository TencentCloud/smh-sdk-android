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

/**
 * @property inode 文件目录ID，可以为文件或者目录
 * @property path 文件目录路径，与 inode 参数二选一，如果同时指定 inode 和 path，则以 inode 为准
 */
data class FavoriteRequest(
    @JvmField val inode: String? = null,
    @JvmField val path: String? = null
)

data class FavoriteResult(
    @JvmField val inode: String?
)

data class FavoriteContents(
    val totalNum: Int? = null,
    val contents: List<FavoriteItem>,
    val nextMarker: String? = null,
)

data class FavoriteItem(
    @JvmField val spaceId: String,
    @JvmField val type: MediaType?,
    @JvmField val inode: String?,
    @JvmField val name: String?,
    @JvmField val size: String?,
    @JvmField val creationTime: String?,
    @JvmField val modificationTime: String?,
    @JvmField val favoriteTime: String?,
    @JvmField val fileType: FileType?,
    @JvmField val path: List<String>?,
    @JvmField val userId: String?,
    @JvmField val eTag: String?,
    @JvmField val virusAuditStatus: Int?,
    @JvmField val labels: List<String>?,
    @JvmField val category: String?,
    @JvmField val contentType: String?,
    @JvmField val crc64: String?,
    @JvmField val previewByDoc: Boolean?,
    @JvmField val previewByCI: Boolean?,
    @JvmField val previewAsIcon: Boolean?,
    @JvmField val removedByQuota: Boolean?,
    @JvmField val metaData: Map<String, String>?,
    @JvmField val versionId: String?,

)