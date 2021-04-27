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
 * 文件夹内容
 *
 * @property nextMarker 下一页的下标
 * @property path 文件夹路径
 * @property contents 文件列表
 */
data class DirectoryContents(
    @JvmField val nextMarker: Int = -1,
    @JvmField val path: List<String>,
    @JvmField val contents: List<MediaContent>
)

fun DirectoryContents.mapDir(dir: Directory): DirectoryContents {
    return DirectoryContents(
        this.nextMarker,
        this.path,
        contents.map {
            MediaContent(
                type = it.type,
                creationTime = it.creationTime,
                name = if (dir.path?.isNotEmpty() == true)
                    dir.path.plus("/").plus(it.name) else it.name
            )
        }
    )
}

enum class MediaType {
    dir,
    image,
    video,
    file,
    symlink
}

/**
 * 媒体文件
 *
 * @property name 文件名
 * @property type 文件类型
 * @property creationTime 文件创建时间
 */
data class MediaContent(
    @JvmField val name: String,
    @JvmField val type: MediaType? = null,
    @JvmField val creationTime: String? = null
)