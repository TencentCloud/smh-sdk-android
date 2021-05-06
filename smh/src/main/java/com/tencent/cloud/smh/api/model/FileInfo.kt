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
 * 文件信息
 *
 * @property type 文件类型
 * @property cosUrl 文件下载路径
 * @property linkTo 源文件，但文件类型为软链接时有效
 */
data class FileInfo(
    @JvmField val type: MediaType,
    @JvmField val cosUrl: String,
    @JvmField val linkTo: List<String>?
) {
    val linkPath: String?
        get() = linkTo?.joinToString(separator = "/")
}