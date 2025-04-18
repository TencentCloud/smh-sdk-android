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
import com.tencent.cloud.smh.smhKey

/**
 * 文件夹
 *
 * @property path 文件夹路径，为空时表示根目录
 */
data class Directory(@JvmField val path: String? = null) {

    fun subDirectory(name: String): Directory {
        return Directory(path = smhKey(path, name))
    }

    fun directoryPath() = path?: ""
}

/**
 * 文件夹创建结果
 * @property inode 最后一级文件目录ID
 * @property path 表示最终的目录或相簿路径，因为可能存在自动重命名，所以这里的最终路径可能不等同于创建目录或相簿时指定的路径；
 */
data class CreateDirectoryResult(
    val inode: String? = null,
    val path: List<String>? = null,
    val creationTime: String? = null,
)

data class FilesPath(
    val paths: List<FileId>
)

data class FileId(
    val path: String,
    val versionId: String? = null,
    val spaceOrgId: Long? = null
)

/**
 * 从模板创建文件请求
 * @property fromTemplate 模板名字，当前支持 word.docx、excel.xlsx 和 powerpoint.pptx
 */
data class CreateFileFromTemplateRequest(
    val fromTemplate: String,
)