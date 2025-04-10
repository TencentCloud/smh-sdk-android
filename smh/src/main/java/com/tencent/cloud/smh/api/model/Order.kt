package com.tencent.cloud.smh.api.model

import com.google.gson.annotations.SerializedName

enum class OrderType {

    @SerializedName("name")
    NAME,

    @SerializedName("modificationTime")
    MODIFICATION_TIME,

    @SerializedName("creationTime")
    CREATION_TIME,

    @SerializedName("expireTime")
    EXPIRE_TIME,

    @SerializedName("removalTime")
    REMOVAL_TIME,

    @SerializedName("size")
    SIZE,

    @SerializedName("favoriteTime")
    FAVORITE_TIME,

    @SerializedName("visitTime")
    VISIT_TIME,

    @SerializedName("uploadTime")
    UPLOAD_TIME,

    @SerializedName("localCreationTime")
    LOCAL_CREATION_TIME,

    @SerializedName("localModificationTime")
    LOCAL_MODIFICATION_TIME,
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