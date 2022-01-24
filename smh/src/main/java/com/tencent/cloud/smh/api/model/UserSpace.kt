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

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/**
 * 用户空间
 *
 * @property userId     用户 ID
 * @property clientId   客户端 ID
 * @property spaceId    用户空间 ID
 */
data class UserSpace(
    @JvmField val userId: String,

    @JvmField val clientId: String? = null,

    @JvmField val spaceId: String? = null
)

/**
 * 用户空间状态
 *
 * @property capacity   空间容量
 * @property size       当前已用空间
 * @property expires    空间过期事件
 */
data class UserSpaceState(
        val capacity: String = "",
        val size: String = "",
        val expires: Int = -1,
) {
    val capacityNumber: Pair<Double, Int>
        get() = parseBigInt(capacity)

    val sizeNumber: Pair<Double, Int>
        get() = parseBigInt(size)

    val remainSize: BigDecimal
        get() = BigDecimal(capacity) - BigDecimal(size)

    val usagePercent: Double
        get() = percent(capacity, size)

    private fun parseBigInt(bigInt: String): Pair<Double, Int> {
        return try {
            val unit = BigDecimal("1024")
            var remain = BigDecimal(bigInt)
            var unitLevel = 0

            while (remain >= unit) {
                remain = remain.divide(unit)
                unitLevel++
            }

            Pair(remain.setScale(2, BigDecimal.ROUND_HALF_UP).toDouble(), unitLevel)
        } catch (e: Exception) {
            Pair(0.0, 0)
        }
    }

    private fun percent(capacity: String, size: String): Double {
        try {
            val bigCap = BigDecimal(capacity)
            val bigSize = BigDecimal(size)

            return bigSize.divide(bigCap, 4, BigDecimal.ROUND_HALF_UP).toDouble()
        } catch (e: Exception) {
            return 0.0
        }
    }

}

val EmptyUseSpace = UserSpace("")

const val DEFAULT_SPACE_ID = "-"

/**
 * SMH 访问凭证
 *
 * @property token      访问凭证
 * @property startTime  访问凭证起始时间
 * @property expiresIn  访问凭证有效时长
 */
data class AccessToken(
    @SerializedName("accessToken")
    @JvmField val token: String,
    @JvmField val startTime: Long,
    @JvmField val expiresIn: Int
) {
    fun isValid(): Boolean {
        return (System.currentTimeMillis() - startTime) / 1000 < expiresIn - 30
    }

    fun now(): AccessToken {
        return AccessToken(
            token = token,
            startTime = System.currentTimeMillis(),
            expiresIn = expiresIn
        )
    }
}