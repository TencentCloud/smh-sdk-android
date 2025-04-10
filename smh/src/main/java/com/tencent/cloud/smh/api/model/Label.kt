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
 * 更新目录自定义标签的请求实体
 *
 * @property labels 文件标签列表
 */
data class UpdateDirectoryLabel(
    @JvmField val labels: List<String>? = null
)

/**
 * 更新文件的标签（Labels）或分类（Category）的请求实体
 * @property labels 文件标签列表, 比如大象
 * @property category 文件自定义的分类,string类型,最大长度16字节，用户可通过更新文件接口修改文件的分类，也可以根据文件后缀预定义文件的分类信息。
 * @property localCreationTime 文件对应的本地创建时间，时间戳字符串
 * @property localModificationTime 文件对应的本地修改时间，时间戳字符串
 */
data class UpdateFileLabel(
    @JvmField val labels: List<String>? = null,
    @JvmField val category: String? = null,
    @JvmField val localCreationTime: String? = null,
    @JvmField val localModificationTime: String? = null
)