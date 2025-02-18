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

/**
 * 查询最近使用的文件列表请求
 * @property marker 用于顺序列出分页的标识，可选参数，不传默认第一页
 * @property limit 用于顺序列出分页时本地列出的项目数限制，可选参数，不传则默认20
 * @property filterActionBy 筛选操作方式，可选，不传返回全部，preview 只返回预览操作，modify 返回编辑操作
 * @property type 筛选文件类型，可选参数，字符串或字符串数组，当前支持的类型包括：
 * ■ all: 搜索所有文件，当不传 type 或传空时默认为 all；
 * ■document: 搜索所有文档，文档类型为：['pdf', 'powerpoint', 'excel', 'word' 'text']
 * ■pdf: 仅搜索 PDF 文档，对应的文件扩展名为 .pdf；
 * ■powerpoint: 仅搜索演示文稿，如 .ppt、.pptx、.pot、.potx 等；
 * ■excel: 仅搜索表格文件，如 .xls、.xlsx、.ett、.xltx、.csv 等；
 * ■word: 仅搜索文档，如 .doc、.docx、.dot、.wps、.wpt 等；
 * ■text: 仅搜索纯文本，如 .txt、.asp、.htm 等；
 * ■doc、xls 或 ppt: 仅搜索 Word、Excel 或 Powerpoint 类型文档，对应的文件扩展名为 .doc(x)、.xls(x) 或 .ppt(x)；
 * ■字符串数组: 可以是文档后缀数组，如 ['.ppt', '.doc', '.excel']等；也可以是上述筛选类型数组，如 ['pdf', 'powerpoint', 'word'] 等
 * @property withPath 响应是否带文件路径，默认为 false
 */
data class RecentlyUsedFileRequest(
    val marker: String? = null,
    val limit: Int? = null,
    val filterActionBy: String? = null,
    val type: List<String>? = null,
    val withPath: Boolean = false,
    val withFavoriteStatus: Boolean = false,
)

/**
 * 查询最近使用的文件列表结果
 */
data class RecentlyUsedFileContents(
    val nextMarker: String?,
    val contents: List<RecentlyUsedFileItem>
)

/**
 * 最近文档项
 * @property name 文档名称
 * @property spaceId 文档所在空间
 * @property inode 文档 ID
 * @property size 文档大小
 * @property actionType 操作类型
 * @property operationTime 操作时间(即访问/编辑的时间)
 * @property creationTime 文件创建时间
 * @property crc64 文件的 CRC64-ECMA182 校验值
 * @param path 文件真实路径。若传入了 withPath:true，则返回该字段
 */
data class RecentlyUsedFileItem(
    val name: String,
    val spaceId: String,
    val inode: String,
    val size: String,
    val type: MediaType,
    val fileType: FileType,
    val previewByDoc: Boolean,
    val previewByCI: Boolean,
    val previewAsIcon: Boolean?,
    val actionType: String,
    val operationTime: String?,
    val creationTime: String,
    val crc64: String,
    val path: List<String>? = null,
    val isFavorite: Boolean? = null,
)