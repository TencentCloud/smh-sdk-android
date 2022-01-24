package com.tencent.cloud.smh.api.model

import com.google.gson.annotations.SerializedName

enum class OrderType {

    @SerializedName("name")
    NAME,

    @SerializedName("modificationTime")
    MODIFICATION_TIME,

    @SerializedName("creationTime")
    CREATION_TIME,

    // TODO: 2021/9/28 ricken 记得支持
    @SerializedName("expireTime")
    EXPIRE_TIME,

    @SerializedName("removalTime")
    REMOVAL_TIME,

    @SerializedName("size")
    SIZE,

    @SerializedName("favoriteTime")
    FAVORITE_TIME
}

enum class OrderDirection{

    @SerializedName("asc")
    ASC,

    @SerializedName("desc")
    DESC
}

enum class DirectoryFilter {

    @SerializedName("onlyFile")
    ONLY_FILE,

    @SerializedName("onlyDir")
    ONLY_DIRECTORY,
}