package com.tencent.cloud.smh.api.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

/**
 * <p>
 * Created by rickenwang on 2021/8/19.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */

/**
 * 搜索条件
 * @property keyword keyword: 搜索关键字，可使用空格分隔多个关键字，关键字之间为“或”的关系并优先展示匹配关键字较多的项目
 * @property tags 搜索标签
 * @property scope 搜索范围，指定搜索的目录，如搜索根目录可指定为空字符串、“/”或不指定该字段
 * @property extname 搜索文件后缀，字符串数组
 * @property type 搜索类型
 * @property creators 搜索创建/更新者
 * @property minFileSize 搜索文件大小范围，单位 Byte
 * @property maxFileSize 搜索文件大小范围，单位 Byte
 * @property modificationTimeStart 搜索更新时间范围，时间戳字符串，与时区无关
 * @property modificationTimeEnd 搜索更新时间范围，时间戳字符串，与时区无关
 * @property orderBy 排序字段，可选参数，当前支持按名称、修改时间、文件大小、创建时间排序具体类型如下：
 * @property orderByType 排序方式，升序为 asc，降序为 desc，可选参数。
 * @property searchMode 搜索方式，快速为 fast，普通为 normal，可选参数，默认 normal。
 * @property labels 简易文件标签
 * @property categories 文件自定义分类信息
 */
data class InitSearchMedia(
    val type: List<SearchType>,
    val keyword: String? = null,
    val scope: String? = null,
    val tags: List<SearchTag>? = null,
    val extname: List<String>? = null,
    val creators: List<SearchCreator>? = null,
    val minFileSize: Long? = null,
    val maxFileSize: Long? = null,
    val modificationTimeStart: String? = null,
    val modificationTimeEnd: String? = null,
    val orderBy: OrderType? = null,
    val orderByType: OrderDirection? = null,
    val searchMode: String? = null,
    val labels: List<String>? = null,
    val categories: List<String>? = null,
) {
    fun signature(): String {
        return "$scope/$keyword/${type.joinToString(",")}/${tags?.joinToString(",")}/" +
                "${extname?.joinToString(",")}/${creators?.joinToString(",")}/" +
                "$minFileSize/$maxFileSize/$modificationTimeStart/$modificationTimeEnd/$orderBy/$orderByType/$searchMode/${labels?.joinToString(",")}/${categories?.joinToString(",")}"
    }
}

/**
 * 搜索标签
 * @property id 标签 id
 * @property value 标签值 用于键值对标签，如：标签名 ios 标签值 13.2，搜索特定版本标签
 */
@Parcelize
data class SearchTag(
    val id: Long,
    val value: String?
): Parcelable

/**
 * 搜索创建/更新者
 * @property userOrgId 创建/更新者所在的组织 ID
 * @property userId 创建/更新者用户 ID
 */
@Parcelize
data class SearchCreator(
    val userOrgId: Long,
    val userId: Long
): Parcelable

data class SearchPartContent(

    val searchId: String,
    val hasMore: Boolean?,
    val searchFinished: Boolean?,
    val nextMarker: Long?,
    val contents: List<SearchItem>
)


data class SearchItem(
    val name: String,
    val userId: String,
    val fileType: FileType?,
    val type: MediaType?,
    val creationTime: String,
    val modificationTime: String,
    val contentType: String?,
    val size: String?,
    val eTag: String?,
    val crc64: String?,
    val path: List<String>,
    val metaData: Map<String, String>?,
    val highlight: HighLight?,
    val previewByDoc: Boolean,
    val previewByCI: Boolean,
    val authorityList: MediaAuthority?,
    val authorityButtonList: MediaAuthorityButton?,
    val localSync: LocalSync?,
    val versionId: String?,
    val modifierName: String?,
    val userOrgId: String? = null,
    val inode: String? = null,
    val isFavorite: Boolean? = null,
    val labels: List<String>? = null,
    val category: String? = null,
    val localCreationTime: String? = null,
    val localModificationTime: String? = null
)

enum class SearchType {

    @SerializedName("all")
    All,

    @SerializedName("dir")
    Dir,

    @SerializedName("file")
    File,

    @SerializedName("doc")
    Doc,

    @SerializedName("ppt")
    PPT,

    @SerializedName("xls")
    XLS,

    @SerializedName("pdf")
    PDF,

    @SerializedName("txt")
    TXT,

    @SerializedName("image")
    Image,

    @SerializedName("video")
    Video,

    @SerializedName("audio")
    Audio,

    @SerializedName("powerpoint")
    Powerpoint,

    @SerializedName("excel")
    Excel,

    @SerializedName("word")
    Word,

    @SerializedName("text")
    Text,
}

data class HighLight(
    val name: List<String>
)