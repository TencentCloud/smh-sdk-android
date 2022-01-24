package com.tencent.cloud.smh.api.model

import com.google.gson.annotations.SerializedName

/**
 * <p>
 * Created by rickenwang on 2021/8/19.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */

data class InitSearchMedia(
    val keyword: String?,
    val scope: String?,
    val type: List<SearchType>,
) {
    fun signature(): String {
        return "$scope/$keyword/${type.joinToString(",")}"
    }
}


data class SearchPartContent(

    val searchId: String,
    val hasMore: Boolean?,
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
    val localSync: LocalSync?,
)

enum class SearchType {

    @SerializedName("all") All,
    @SerializedName("dir") Dir,
    @SerializedName("file") File,
    @SerializedName("doc") Doc,
    @SerializedName("ppt") PPT,
    @SerializedName("xls") XLS,
    @SerializedName("pdf") PDF,
    @SerializedName("txt") TXT,
    @SerializedName("image") Image,
    @SerializedName("video") Video,
    @SerializedName("audio") Audio,
    @SerializedName("other") Other,

}

data class HighLight(
    val name: List<String>
)