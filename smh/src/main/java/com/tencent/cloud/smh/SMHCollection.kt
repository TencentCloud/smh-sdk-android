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

package com.tencent.cloud.smh

import android.content.Context
import android.net.Uri
import com.tencent.cloud.smh.api.SMHService
import com.tencent.cloud.smh.api.adapter.*
import com.tencent.cloud.smh.api.dataOrNull
import com.tencent.cloud.smh.api.model.*
import com.tencent.cloud.smh.transfer.COSFileTransfer
import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.exception.CosXmlServiceException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal
import java.util.*
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext

/**
 * <p>
 *     SMH 资源库
 * </p>
 * Created by wjielai on 1/26/21.
 * Copyright 2010-2020 Tencent Cloud. All Rights Reserved.
 */
class SMHCollection @JvmOverloads constructor(
    private val context: Context,
    private val user: SMHUser,
    private val transfer: COSFileTransfer = COSFileTransfer(context),
) {

    internal val rootDirectory = Directory()

    private val libraryId get() = user.libraryId
    private val userSpace get() = user.userSpace

    @Synchronized
    private suspend fun ensureValidAK(): AccessToken {
        return user.provideAccessToken()
    }

    /**
     * 获取 Future 封装类，封装类适配 Java8 CompletableFuture 风格的 API
     *
     * @param context 工作线程，默认是 IO 线程
     * @param scope 协程 Scope，默认是 GlobalScope
     */
    @JvmOverloads
    fun future(
        context: CoroutineContext = Dispatchers.IO,
        scope: CoroutineScope = GlobalScope
    ) = SMHCollectionFuture(this, context = context, scope = scope)

    /**
     * 查询服务是否可用
     *
     */
    suspend fun checkCloudServiceEnableState() {
        ensureValidAK()
    }

    /**
     * 获取剩余可用配额空间
     *
     * @return 可用配额空间，单位是 Byte
     */
    suspend fun getSpaceQuotaRemainSize(): BigDecimal? {
        return user.getSpaceState().dataOrNull?.remainSize
    }

    /**
     * 列出根文件夹列表
     *
     * @return 文件夹列表
     */
    suspend fun listDirectory(): List<Directory> {
        return list(
            dir = rootDirectory,
            paging = false
        ).contents.filter {
            it.type == MediaType.dir
        }.map {
            Directory(path = it.name)
        }
    }

    /**
     * 列出文件列表
     *
     * @param dir 文件夹
     * @param paging 是否分页，默认是分页列出
     * @return 文件列表
     */
    @JvmOverloads
    suspend fun list(
        dir: Directory,
        paging: Boolean = true
    ): DirectoryContents {
        val accessToken = ensureValidAK()

        var nextMarker: Int = Int.MIN_VALUE
        val contents = ArrayList<MediaContent>()
        var path: List<String> = emptyList()
        while (nextMarker > 0 || nextMarker == Int.MIN_VALUE) {
            val directoryContents = SMHService.shared.listDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = dir.path ?: "",
                accessToken = accessToken.token,
                userId = userSpace.userId,
                marker = if (nextMarker == Int.MIN_VALUE) null else nextMarker,
            ).data.mapDir(dir)

            path = directoryContents.path
            nextMarker = directoryContents.nextMarker
            contents.addAll(directoryContents.contents)
            if (paging) {
                break
            }
        }

        return DirectoryContents(path = path, contents = contents);
    }

    /**
     * 创建文件夹
     *
     * @param dir 文件夹
     * @return 执行结果，true 表示创建成功，false 表示创建失败
     */
    suspend fun createDirectory(dir: Directory): Boolean {
        val path = checkNotNull(dir.path)
        val accessToken = ensureValidAK()

        return SMHService.shared.createDirectory(
            libraryId = libraryId,
            spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
            dirPath = path,
            accessToken = accessToken.token,
            userId = userSpace.userId,
        ).checkSuccess()
    }

    /**
     * 重命名文件夹
     *
     * @param target 目标文件夹
     * @param source 源文件夹
     * @return 执行结果，true 表示重命名成功，false 表示重命名失败
     */
    suspend fun renameDirectory(target: Directory, source: Directory): Boolean {
        val targetPath = checkNotNull(target.path)
        val sourcePath = checkNotNull(source.path)
        val accessToken = ensureValidAK()

        return SMHService.shared.renameDirectory(
            libraryId = libraryId,
            spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
            dirPath = targetPath,
            accessToken = accessToken.token,
            userId = userSpace.userId,
            from = RenameDirectoryBody(sourcePath)
        ).checkSuccess()
    }

    /**
     * 删除文件夹
     *
     * @param dir 文件夹
     * @return 执行结果，true 表示成功，false 表示失败
     */
    suspend fun deleteDirectory(dir: Directory): Boolean {
        val path = checkNotNull(dir.path)
        val accessToken = ensureValidAK()

        return SMHService.shared.deleteDirectory(
            libraryId = libraryId,
            spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
            dirPath = path,
            accessToken = accessToken.token,
            userId = userSpace.userId,
        ).checkSuccess()
    }

    /**
     * 获取文件信息
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 所在文件夹，默认是根目录下
     * @return 文件信息
     */
    @JvmOverloads
    suspend fun getFileInfo(
        name: String,
        dir: Directory = rootDirectory,
    ): FileInfo? {
        val accessToken = ensureValidAK()

        return SMHService.shared.getFileInfo(
            libraryId = libraryId,
            spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
            filePath = dir.path?.let { "$it/$name" } ?: name,
            accessToken = accessToken.token
        ).dataOrNull
    }

    /**
     * 初始化简单上传
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param meta 文件元数据
     * @param dir 所在文件夹，默认是根目录下
     * @param overrideOnNameConflict 存在重名文件时是覆盖还是重命名，true 表示覆盖，false 表示重命名
     * @return 上传信息
     */
    @JvmOverloads
    suspend fun initUpload(
        name: String,
        meta: Map<String, String>? = null,
        dir: Directory = rootDirectory,
        overrideOnNameConflict: Boolean = false
    ): InitUpload {
        val accessToken = ensureValidAK()

        val metaData = meta?.mapKeys { "x-smh-meta-${it.key}" }

        return SMHService.shared.initUpload(
            libraryId = libraryId,
            spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
            filePath = dir.path?.let { "$it/$name" } ?: name,
            accessToken = accessToken.token,
            force = if (overrideOnNameConflict) 1 else 0,
            metaData = metaData ?: emptyMap()
        ).data
    }

    /**
     * 初始化分块上传
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param meta 文件元数据
     * @param dir 所在文件夹，默认是根目录下
     * @param overrideOnNameConflict 存在重名文件时是覆盖还是重命名，true 表示覆盖，false 表示重命名
     * @return 上传信息
     */
    @JvmOverloads
    suspend fun initMultipartUpload(
        name: String,
        meta: Map<String, String>? = null,
        dir: Directory = rootDirectory,
        overrideOnNameConflict: Boolean = false
    ): InitUpload {
        val accessToken = ensureValidAK()

        val metaData = meta?.mapKeys { "x-smh-meta-${it.key}" }

        return SMHService.shared.initMultipartUpload(
            libraryId = libraryId,
            spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
            filePath = dir.path?.let { "$it/$name" } ?: name,
            accessToken = accessToken.token,
            force = if (overrideOnNameConflict) 1 else 0,
            metaData = metaData ?: emptyMap()
        ).data
    }

    /**
     * 列出分片上传任务信息
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 上传信息
     */
    suspend fun listMultipartUpload(confirmKey: String): MultiUploadMetadata {
        val accessToken = ensureValidAK()

        val meta = SMHService.shared.listMultipartUpload(
            libraryId = libraryId,
            spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
            confirmKey = confirmKey,
            accessToken = accessToken.token
        ).data
        meta.confirmKey = confirmKey
        return meta
    }

    /**
     * 分片上传
     *
     * @param metadata 文件元数据
     * @param uri 本地文件 URI
     * @param size 本地文件大小
     * @return 上传成功返回的 eTag
     */
    suspend fun multipartUpload(metadata: MultiUploadMetadata, uri: Uri, size: Long): String? {
        return transfer.multipartUpload(metadata, uri, size)
    }

    /**
     * 简单上传
     *
     * @param uploader 上传信息
     * @param uri 本地文件 URI
     * @return 上传成功返回的 eTag
     */
    suspend fun upload(uploader: InitUpload, uri: Uri): String? {
        return transfer.upload(uploader, uri)
    }

    /**
     * 取消上传
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 执行结果
     */
    suspend fun cancelUpload(confirmKey: String): Boolean {
        val accessToken = ensureValidAK()

        return SMHService.shared.cancelUpload(
            libraryId = libraryId,
            spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
            confirmKey = confirmKey,
            accessToken = accessToken.token
        ).isSuccess
    }

    /**
     * 完成上传
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 上传结果
     */
    suspend fun confirmUpload(confirmKey: String): ConfirmUpload {
        val accessToken = ensureValidAK()

        return SMHService.shared.confirmUpload(
            libraryId = libraryId,
            spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
            confirmKey = confirmKey,
            accessToken = accessToken.token
        ).data
    }

    /**
     * 初始化下载
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 文件所在文件夹，默认为根目录
     * @return 下载信息
     */
    suspend fun initDownload(
        name: String,
        dir: Directory = rootDirectory,
    ): InitDownload {
        val accessToken = ensureValidAK()

        val resp = SMHService.shared.initDownload(
            libraryId = libraryId,
            spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
            filePath = dir.path?.let { "$it/$name" } ?: name,
            accessToken = accessToken.token
        )
        val metaPrefix = "x-smh-meta-"
        return InitDownload(
            url = resp.header("Location"),
            metaData = resp.headers().filterKeys { it.toLowerCase().startsWith(metaPrefix) }
                .mapKeys { it.key.substring(metaPrefix.length) }
        )
    }

    /**
     * 下载文件
     *
     * @param url 文件下载地址
     * @param contentUri 本地文件 URI
     * @param offset 请求文件偏移
     */
    @JvmOverloads
    suspend fun download(url: String, contentUri: Uri, offset: Long = 0L) {
        val result = transfer.download(url, contentUri, offset)
        withContext(Dispatchers.IO) {
            // verify file size
            context.contentResolver.openFileDescriptor(contentUri, "r")?.use {
                val fileSize = it.statSize
                result.headers.filterKeys { it.toLowerCase(Locale.ROOT) == "content-length" }
                    .values.firstOrNull()?.firstOrNull()?.apply {
                        val expected = toInt() + offset
                        if (fileSize != expected) {
                            throw SMHException(message = "File is Not Complete, Expected: $expected, Actual: $fileSize")
                        }
                    }

            }
        }
    }

    /**
     * 删除文件
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 文件所在文件夹，默认为根目录
     * @return 执行结果
     */
    suspend fun delete(
        name: String,
        dir: Directory = rootDirectory
    ): Boolean {
        val accessToken = ensureValidAK()

        return SMHService.shared.deleteFile(
            libraryId = libraryId,
            spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
            filePath = dir.path?.let { "$it/$name" } ?: name,
            accessToken = accessToken.token
        ).checkSuccess()
    }

    /**
     * 创建文件链接
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 文件所在文件夹，默认为根目录
     * @param sourceFileName 源文件完整路径
     * @param overrideOnNameConflict 存在重名文件时是覆盖还是重命名，true 表示覆盖，false 表示重命名
     * @return 创建结果
     */
    @JvmOverloads
    suspend fun createSymLink(
        name: String,
        dir: Directory = rootDirectory,
        sourceFileName: String,
        overrideOnNameConflict: Boolean = false
    ): ConfirmUpload {
        val accessToken = ensureValidAK()

        return SMHService.shared.createSymLink(
            libraryId = libraryId,
            spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
            filePath = dir.path?.let { "$it/$name" } ?: name,
            accessToken = accessToken.token,
            force = if (overrideOnNameConflict) 1 else 0,
            linkTo = SymLinkBody(sourceFileName)
        ).data
    }
}