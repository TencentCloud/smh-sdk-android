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
 * @property nextMarker 下一页的下标
 * @property path 文件夹路径
 * @property contents 文件列表
 */
data class DirectoryContents(
    @JvmField val path: List<String>,
    @JvmField val fileCount: Int?,
    @JvmField val subDirCount: Int?,
    @JvmField val totalNum: Int?,
    @JvmField val localSync: LocalSync?,
    @JvmField val authorityList: MediaAuthority?,
    @JvmField val contents: List<MediaContent>
)

data class RecycledContents(
    @JvmField val totalNum: Int = -1,
    @JvmField val contents: List<RecycledItem>
)

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

//fun DirectoryContents.mapDir(dir: Directory): DirectoryContents {
//    return DirectoryContents(
//        this.nextMarker,
//        this.path,
//        this.fileCount,
//        this.subDirCount,
//        this.totalNum,
//        this.authorityList,
//        contents.map {
//            MediaContent(
//                type = it.type,
//                creationTime = it.creationTime,
//                name = if (dir.path?.isNotEmpty() == true)
//                    dir.path.plus("/").plus(it.name) else it.name,
//                contentType = it.contentType,
//                crc64 = it.crc64,
//                size = it.size,
//                authorityList = it.authorityList,
//            )
//        }
//    )
//}

enum class MediaType {
    dir,
    image,
    video,
    file,

    @SerializedName("multi-file")
    multiFile,
    symlink
}


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

enum class ConflictStrategy {

    @SerializedName("ask") ASK,

    @SerializedName("rename") RENAME,

    @SerializedName("overwrite") OVERWRITE,
}

/**
 * 媒体文件
 *
 * @property name 文件名
 * @property type 文件类型
 * @property creationTime 文件创建时间
 */
data class MediaContent(
    @JvmField val name: String,
    @JvmField val contentType: String?,
    @JvmField val crc64: String?,
    @JvmField val size: Long?,
    @JvmField val type: MediaType? = null,
    @JvmField val objectKey:String? = null,
    @JvmField val fileType: FileType? = null,
    @JvmField val creationTime: String? = null,
    @JvmField val modificationTime: String? = null,
    @JvmField val eTag: String? = null,
    @JvmField val metaData: Map<String, String>?,
    @JvmField val removedByQuota: Boolean? = null,
    @JvmField val previewByDoc: Boolean? = null,
    @JvmField val previewByCI: Boolean? = null,
    @JvmField val previewAsIcon: Boolean? = null,
    @JvmField val authorityList: MediaAuthority? = null,
    @JvmField val isExist: Boolean? = null,
    @JvmField val localSync: LocalSync? = null,
)

data class RecycledItem(

    @JvmField val recycledItemId: Long,
    @JvmField val name: String,
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
)

data class DeleteMediaResult(
    val recycledItemId: Long,
)

//@Parcelize
//data class MetaData(
//    @JvmField val data: Map<String, String>,
//
//): Parcelable