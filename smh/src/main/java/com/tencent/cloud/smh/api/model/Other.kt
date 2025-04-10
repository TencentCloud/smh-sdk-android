package com.tencent.cloud.smh.api.model

import com.google.gson.annotations.SerializedName

/**
 * @author Created by jordanqin.
 *
 * @date Created in 2022/6/29 5:35 下午
 *
 * @description：
 *
 * Copyright 2010-2022 Tencent Cloud. All Rights Reserved.
 */
class Other {
}

/**
 * 用途
 */
enum class Purpose(var value: String) {
    /**
     * 列表页
     */
    @SerializedName("list") LIST("list"),

    /**
     * 预览页
     */
    @SerializedName("preview") PREVIEW("preview"),

    /**
     * 用于下载
     */
    @SerializedName("download") DOWNLOAD("download");

    override fun toString(): String = value
}

/**
 * 设置租户空间限速
 * @property downloadTrafficLimit 空间下载限速，数字类型，必选参数，范围 100KB/s - 100MB/s ，单位Byte，当输入-1时表示取消限速；
 */
data class SpaceTrafficLimit(@JvmField val downloadTrafficLimit: Long)