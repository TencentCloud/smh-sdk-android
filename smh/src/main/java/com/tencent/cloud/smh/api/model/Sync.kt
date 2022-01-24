package com.tencent.cloud.smh.api.model

import com.google.gson.annotations.SerializedName

/**
 * <p>
 * Created by rickenwang on 2022/1/11.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */

data class PutDirectoryLocalSyncRequestBody(
    val path: String,
    val strategy: DirectoryLocalSyncStrategy,
    val localPath: String,
)

data class PutDirectoryLocalSyncResponseBody(
    val id: Int,
)

enum class DirectoryLocalSyncStrategy {

    @SerializedName("local_to_cloud")
    LocalToCloud,

    @SerializedName("cloud_to_local")
    CloudToLocal,

    @SerializedName("both")
    Both,
}