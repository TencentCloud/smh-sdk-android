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

/**
 * 批量请求结果
 */
data class BatchResponse(
    @JvmField val taskId: Long? = null,
    @JvmField val status: Int? = null,
    @JvmField var result: List<BatchResponseItem>? = null) {
}

/**
 * 批量请求结果 result非数组
 */
data class BatchResponseSingleResult(
    @JvmField val taskId: Long? = null,
    @JvmField val status: Int? = null,
    @JvmField var result: BatchResponseItem? = null) {
}

data class BatchResponseItem(
    @JvmField val status: Int,
    @JvmField val recycledItemId: Long? = null,
    @JvmField val path: List<String>? = null,
    @JvmField val code: String? = null,
    @JvmField val message: String? = null,
)


data class RestorePath(
    @JvmField val path: List<String>? = null,
)

/**
 * 批量删除项
 * @property permanent 是否永久删除(1为永久删除 0为放入回收站)
 */
data class BatchDeleteItem(
    @JvmField val path: String,
    @JvmField val permanent: Int = 0
)

data class BatchSaveToDiskItem(
    @JvmField val copyFromLibraryId: String,
    @JvmField val copyFromSpaceId: String,
    @JvmField val copyFrom: String,
    @JvmField val to: String,
    @JvmField val conflictResolutionStrategy: ConflictStrategy? = null,
    @JvmField val moveAuthority: Boolean? = null,
)

data class BatchCopyItem(
    @JvmField val copyFrom: String,
    @JvmField val to: String,
    @JvmField val conflictResolutionStrategy: ConflictStrategy? = null,
    @JvmField val moveAuthority: Boolean? = null,
)

data class BatchMoveItem(
    @JvmField val from: String,
    @JvmField val to: String,
    @JvmField val conflictResolutionStrategy: ConflictStrategy? = null,
    @JvmField val moveAuthority: Boolean? = null,
)
