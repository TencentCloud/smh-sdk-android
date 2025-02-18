package com.tencent.cloud.smh

import com.google.gson.annotations.SerializedName
import com.tencent.cloud.smh.api.model.AccessToken
import com.tencent.cloud.smh.api.model.EmptyUseSpace
import com.tencent.cloud.smh.api.model.UserSpace

/**
 * @author Created by jordanqin.
 *
 * @date Created in 2022/5/19 2:59 下午
 *
 * @description：
 *
 * Copyright 2010-2022 Tencent Cloud. All Rights Reserved.
 */
class MySMHSimpleUser: SMHSimpleUser() {
    val token: String = "acctk015a1f0cc2lwsvq18vzm5szlmxewkf983wnhgw7lz74e56d75p9gexbfevl62pubj86fnxcmaj2cc8bq6777zstbf8tv3wb6ksvm4lrvj9tctyb9969a1884fbb"
    val startTime: Long = 1717050815924
    val expiresIn: Int =  1799
    val userId: String = "59"
    val libraryIdArg: String = "smh3ptyc9mscifdi"
    val spaceId = "space1x8mfjgno6nyy"

    override val libraryId: String
        get() = libraryIdArg
    override val userSpace: UserSpace
        get() = UserSpace(
            userId = userId,
            spaceId = spaceId)

    override suspend fun provideAccessToken(): AccessToken = AccessToken(
        token,
        startTime,
        expiresIn)
}