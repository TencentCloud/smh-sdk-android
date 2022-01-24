package com.tencent.cloud.smh.api.model

/**
 * <p>
 * Created by rickenwang on 2021/9/16.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */

data class HistoryMediaContent(
    val totalNum: Int,
    val contents: List<HistoryMedia>,
)

data class HistoryMedia(
    val id: Long,
    val createdBy: Long,
    val creationWay: Int,
    val createdUserId: String,
    val createdUserNickname: String,
    var createdUserAvatar: String?,
    val version: Long,
    val isLatestVersion: Boolean,
    val name: String,
    val size: Long,
    val crc64: String?,
    val creationTime: String,
    val authorityList: MediaAuthority,
)

data class HistoryStatus(
    val enableFileHistory: Boolean,
    val fileHistoryCount: Long,
    val fileHistoryExpireDay: Int,
)