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

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

/**
 * 文件夹内容
 *
 * @property path 文件夹路径
 * @property fileCount 文件的数量
 * @property subDirCount 子文件夹的数量
 * @property totalNum 文件和子文件夹的总数量
 * @property localSync 同步盘配置
 * @property authorityList 操作权限
 * @property contents 文件列表
 */
data class DirectoryContents(
    @JvmField val path: List<String>,
    @JvmField val fileCount: Int?,
    @JvmField val subDirCount: Int?,
    @JvmField val totalNum: Int?,
    @JvmField val localSync: LocalSync?,
    @JvmField val authorityList: MediaAuthority?,
    @JvmField val authorityButtonList: MediaAuthorityButton?,
    @JvmField val contents: List<MediaContent>,
    @JvmField val eTag: String? = null,
    @JvmField val nextMarker: String? = null,
)

/**
 * 回收站内容
 *
 * @property totalNum 文件总数
 * @property contents 回收站文件内容
 */
data class RecycledContents(
    @JvmField val totalNum: Int = -1,
    @JvmField val contents: List<RecycledItem>,
    val eTag: String? = null,
    val nextMarker: String? = null,
)

/**
 * 同步盘配置
 *
 * @property syncId 同步 id
 * @property strategy 同步策略
 * @property isSyncRootFolder 是否同步根目录
 * @property syncUserId 同步的 userId
 */
@Parcelize
data class LocalSync(
    val syncId: Int,
    val strategy: String,
    val isSyncRootFolder: Boolean,
    val syncUserId: String,
): Parcelable {

    override fun equals(other: Any?): Boolean {
        return other is LocalSync && other.syncId == syncId
    }

    override fun hashCode(): Int {
        return syncId.hashCode()
    }
}

/**
 * 媒体类型
 */
enum class MediaType {
    dir,
    image,
    video,
    file,

    @SerializedName("multi-file")
    multiFile,
    symlink
}

/**
 * 文件类型
 */
enum class FileType {

    word,
    powerpoint,
    excel,
    portable,
    image,
    audio,
    video,
    text,
    archive,
    other,
}

/**
 * 冲突处理方式
 */
enum class ConflictStrategy {

    /**
     * 冲突时返回 HTTP 409 Conflict
     */
    @SerializedName("ask") ASK,

    /**
     * 冲突时自动重命名
     */
    @SerializedName("rename") RENAME,

    /**
     * 冲突时覆盖
     */
    @SerializedName("overwrite") OVERWRITE,
}

/**
 * 媒体文件
 *
 * @property name 文件名
 * @property contentType 媒体类型
 * @property crc64 文件的 CRC64-ECMA182 校验值
 * @property size 文件大小
 * @property type 条目类型
 * @property objectKey 将历史版本设置为最新版时返回的文件路径
 * @property fileType 文件类型
 * @property creationTime 文件首次完成上传的时间
 * @property modificationTime 文件最近一次被覆盖的时间
 * @property eTag 文件 ETag
 * @property metaData 元数据，如果没有元数据则不存在该字段
 * @property removedByQuota 是否因为配额被删除
 * @property previewByDoc 是否可通过 wps 预览
 * @property previewByCI 是否可通过万象预览
 * @property previewAsIcon 是否可用预览图当做 icon
 * @property authorityList 文件创建时间
 * @property isExist 查询文件信息时是否存在
 * @property localSync 文件同步配置
 */
data class MediaContent(
    @JvmField val name: String,
    @JvmField val contentType: String?= null,
    @JvmField val crc64: String? = null,
    @JvmField val size: Long? = null,
    @JvmField val type: MediaType? = null,
    @JvmField val objectKey:String? = null,
    @JvmField val fileType: FileType? = null,
    @JvmField val creationTime: String? = null,
    @JvmField val modificationTime: String? = null,
    @JvmField val versionId: Long? = null,
    @JvmField val eTag: String? = null,
    @JvmField var metaData: Map<String, String>? = null,
    @JvmField val path: List<String>? = null,
    @JvmField val removedByQuota: Boolean? = null,
    @JvmField val previewByDoc: Boolean? = null,
    @JvmField val previewByCI: Boolean? = null,
    @JvmField val previewAsIcon: Boolean? = null,
    @JvmField val authorityList: MediaAuthority? = null,
    @JvmField val authorityButtonList: MediaAuthorityButton? = null,
    @JvmField val isExist: Boolean? = null,
    @JvmField val localSync: LocalSync? = null,
    @JvmField val userId: String? = null,
    @JvmField val inode: String? = null,
    @JvmField val isFavorite: Boolean? = null,
)

/**
 * 回收站中的文件
 *
 *
 */
data class RecycledItem(
    @JvmField val recycledItemId: Long,
    @JvmField val name: String,
    @JvmField val spaceId: String,
    @JvmField val remainingTime: Int,
    @JvmField val removalTime: String?,
    @JvmField val originalPath: List<String>,
    @JvmField val crc64: String?,
    @JvmField val size: Long?,
    @JvmField val type: MediaType? = null,
    @JvmField val fileType: FileType? = null,
    @JvmField val creationTime: String? = null,
    @JvmField val modificationTime: String? = null,
    @JvmField val authorityList: RecycledAuthority? = null,
    @JvmField val authorityButtonList: MediaAuthorityButton? = null,
)

data class DeleteMediaResult(
    val recycledItemId: Long,
)

//@Parcelize
//data class MetaData(
//    @JvmField val data: Map<String, String>,
//
//): Parcelable