package com.tencent.cloud.smh.api.model

/**
 * @author Created by jordanqin.
 *
 * @date Created in 2022/6/2 7:30 下午
 *
 * @description：
 *
 * Copyright 2010-2022 Tencent Cloud. All Rights Reserved.
 */
/**
 * 跨空间复制目录 请求
 * @property copyFrom 被复制的源目录或相簿路径
 * @property copyFromSpaceId 被复制的源空间 SpaceId
 */
data class AsyncCopyCrossSpaceRequest(
    @JvmField val copyFrom: String,
    @JvmField val copyFromSpaceId: String,
)

/**
 * 跨空间复制目录 结果
 * @property taskId 异步方式复制时的任务 ID，可通过查询任务接口查询任务状态
 * @property path 字符串数组，表示最终的目录或相簿路径，因为可能存在自动重命名，所以这里的最终路径可能不等同于复制目录或相簿时指定的路径
 */
data class AsyncCopyCrossSpaceResult(
    @JvmField val taskId: Long? = null,
    @JvmField val path: List<String>? = null,
)

