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
import android.text.TextUtils
import com.google.gson.Gson
import com.tencent.cloud.smh.api.SMHService
import com.tencent.cloud.smh.api.SMHService.Companion.baseUrl
import com.tencent.cloud.smh.api.adapter.*
import com.tencent.cloud.smh.api.data
import com.tencent.cloud.smh.api.dataOrNull
import com.tencent.cloud.smh.api.model.*
import com.tencent.cloud.smh.ext.cosPathEncode
import com.tencent.cloud.smh.track.SMHBeaconKey
import com.tencent.cloud.smh.track.SMHFailureRequestTrackEvent
import com.tencent.cloud.smh.track.SMHSuccessRequestTrackEvent
import com.tencent.cloud.smh.transfer.COSFileTransfer
import com.tencent.cloud.smh.transfer.CancelHandler
import com.tencent.cos.xml.listener.CosXmlProgressListener
import com.tencent.qcloud.core.track.TrackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.util.*
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

/**
 * <p>
 *     SMH 资源库
 * </p>
 *
 * @param user 通过调用 [SMHUser.provideAccessToken] 提供 AccessToken，
 * 如果报错 Token 过期，则会调用 [SMHUser.refreshAccessToken] 来刷新 Token，并重新请求
 * 
 */
class SMHCollection @JvmOverloads constructor(
    private val context: Context,
    internal val user: SMHUser,
    private val transfer: COSFileTransfer = COSFileTransfer(context),
) {

    internal val rootDirectory = Directory()

    private val libraryId get() = user.libraryId
    private val userSpace get() = user.userSpace
    private val gson = Gson()

    init {
        TrackService.init(context, SMHBeaconKey, BuildConfig.DEBUG)
    }

    @Synchronized
    private suspend fun ensureValidAK(): AccessToken {
        return user.provideAccessToken()
    }

    @Synchronized
    private suspend fun ensureRefreshValidAK(): AccessToken {
        return if (user is SMHRefreshTokenUser) {
            user.refreshAccessToken()
        } else {
            user.provideAccessToken()
        }
    }

    fun spaceId() = user.userSpace.spaceId

    /**
     * 获取一个有效的 AccessToken
     */
    suspend fun getAccessToken(): AccessToken {
        return ensureValidAK()
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
     * 获取用户空间状态
     */
    suspend fun getUserSpaceState(): UserSpaceState = user.getSpaceState().data


    /**
     * 列出所有的文件列表
     */
    suspend fun listAll(

        dir: Directory,
        pageSize: Int = 100,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
        directoryFilter: DirectoryFilter? = null,
    ): DirectoryContents {

        var page = 1
        val contents = ArrayList<MediaContent>()

        while (true) {

            val directoryContents = list(
                dir = dir,
                page = page,
                pageSize = pageSize,
                orderType = orderType,
                orderDirection = orderDirection,
                directoryFilter = directoryFilter,
            )
            contents.addAll(directoryContents.contents)
            page++

            // 已经最后一页了
            if (directoryContents.contents.size < pageSize) {
                return DirectoryContents(
                    path = directoryContents.path,
                    fileCount = directoryContents.fileCount,
                    subDirCount = directoryContents.subDirCount,
                    totalNum = directoryContents.totalNum,
                    authorityList = directoryContents.authorityList,
                    localSync = directoryContents.localSync,
                    contents = contents
                )
            }
        }
    }

    /**
     * 通过 marker + limit 的方式列出所有的文件列表
     */
    suspend fun listAllWithMarker(

        dir: Directory,
        limit: Int = 100,
    ): DirectoryContents {

        var nextMarker: Long? = null
        val contents = ArrayList<MediaContent>()

        while (true) {

            val directoryContents = listWithMarker(
                dir = dir,
                nextMarker = nextMarker,
                limit = limit,
            )
            contents.addAll(directoryContents.contents)
            nextMarker = directoryContents.nextMarker

            // 已经最后一页了
            if (nextMarker == null) {
                return DirectoryContents(
                    path = directoryContents.path,
                    fileCount = directoryContents.fileCount,
                    subDirCount = directoryContents.subDirCount,
                    totalNum = directoryContents.totalNum,
                    authorityList = directoryContents.authorityList,
                    localSync = directoryContents.localSync,
                    contents = contents,
                )
            }
        }
    }

    /**
     * 列出文件列表
     *
     * @param dir 文件夹
     * @param page 文件页码
     * @param pageSize 一页的数据量
     * @param orderType 排序方式
     * @param orderDirection 排序方向
     * @param directoryFilter 过滤类型
     *
     * @return 文件列表
     */
    @JvmOverloads
    suspend fun list(
        dir: Directory,
        page: Int,
        pageSize: Int,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
        directoryFilter: DirectoryFilter? = null,
    ): DirectoryContents {


        return runWithBeaconReport("ListDirectory", dir.path) { accessToken ->
            SMHService.shared.listDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = dir.path ?: "",
                accessToken = accessToken,
                userId = userSpace.userId,
                page = page,
                pageSize = pageSize,
                orderBy = orderType,
                orderByType = orderDirection,
                directoryFilter = directoryFilter,
            ).data
        }
    }

    /**
     * 通过 marker + limit 的方式列出文件列表
     *
     * 不支持排序
     *
     * @param dir 文件夹
     * @param page 文件页码
     * @param pageSize 一页的数据量
     * @param orderType 排序方式
     * @param orderDirection 排序方向
     * @param directoryFilter 过滤类型
     *
     * @return 文件列表
     */
    @JvmOverloads
    suspend fun listWithMarker(
        dir: Directory,
        nextMarker: Long? = null,
        limit: Int? = null,
    ): DirectoryContents {

        return runWithBeaconReport("ListDirectoryWithMarker", dir.path) { accessToken ->
            SMHService.shared.listDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = dir.path ?: "",
                accessToken = accessToken,
                userId = userSpace.userId,
                marker = nextMarker,
                limit = limit,
            ).data
        }
    }

    /**
     * 获取角色列表，不同的角色对应不同的权限
     */
    suspend fun getRoleList(): List<Role> {
        return runWithBeaconReport("GetRoleList", null) { accessToken ->
            SMHService.shared.getRoleList(
                libraryId = libraryId,
                accessToken = accessToken,
            ).data
        }
    }

    /**
     * 获取下载链接
     *
     * @param path 文件路径
     * @param historyId 历史版本号
     * @param encode 文件路径是否已经 url 编码
     */
    suspend fun getDownloadAccessUrl(path: String, historyId: Long? = null, encode: Boolean = false): String {
        val encodePath = if (encode) {
            path
        } else {
            path.cosPathEncode()
        }
        return retryWhenTokenExpired() { accessToken ->
            "${baseUrl()}api/v1/file/$libraryId/${userSpace.spaceId}/${encodePath}?"
                .query("history_id", historyId)
                .query("access_token", accessToken).query("user_id", userSpace.userId)
        }
    }


    /**
     * 获取文档预览链接
     * @param filePath 文件路径
     * @param historyId 历史版本号
     */
    suspend fun getPreviewAccessUrl(filePath: String, historyId: Long? = null,): String {
        return retryWhenTokenExpired() { accessToken ->
            "${baseUrl()}api/v1/file/${libraryId}/${userSpace.spaceId}/${filePath.cosPathEncode()}?preview"
                .query("history_id", historyId)
                .query("access_token", accessToken).query("user_id", userSpace.userId)
        }
    }

    /**
     * 获取照片/视频封面缩略图链接
     *
     * @param name 文件名
     * @param dir 相册
     * @param historyId 历史版本号
     * @param size 图片大小
     * @param scale 缩放尺寸
     * @param widthSize 图片宽度
     * @param heightSize 图片高度
     */
    @JvmOverloads
    suspend fun getThumbnailAccessUrl(name: String, dir: Directory = rootDirectory, historyId: Long? = null,
                                      size: Int? = null, scale: Int? = null,
                                      widthSize: Int? = null, heightSize: Int? = null): String {

        val filePath = dir.path?.let { "$it/$name" } ?: name
        return retryWhenTokenExpired() { accessToken ->
            "${baseUrl()}api/v1/file/${libraryId}/${userSpace.spaceId}/${filePath.cosPathEncode()}?preview"
                .query("history_id", historyId)
                .query("size", size).query("scale", scale).query("width_size", widthSize).query("height_size", heightSize)
                .query("access_token", accessToken).query("user_id", userSpace.userId)
        }
    }

    /**
     * 获取我共享的文件夹
     *
     * @param page 文件页码
     * @param pageSize 一页的数据量
     * @param orderType 排序方式
     * @param orderDirection 排序方向
     */
    suspend fun getMyAuthorizedDirectory(page: Int, pageSize: Int, orderType: OrderType? = null,
                                         orderDirection: OrderDirection? = null): AuthorizedContent {
        return runWithBeaconReport("ListAuthorizedDirectory", null) { accessToken ->
             SMHService.shared.getMyAuthorizedDirectory(
                libraryId = libraryId,
                page = page,
                pageSize = pageSize,
                orderBy = orderType,
                orderByType = orderDirection,
                accessToken = accessToken,
            ).data
        }
    }

    /**
     * 初始化搜索，可能会返回一定量的搜索结果
     *
     * @param keyword 搜索关键字
     * @param scope 搜索范围
     * @param searchTypes 搜索的文件类型
     */
    suspend fun initSearch(keyword: String?, scope: String? = null, searchTypes: List<SearchType>): SearchPartContent {

        return runWithBeaconReport("InitSearch", keyword) { accessToken ->
            SMHService.shared.initSearch(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                userId = userSpace.userId,
                initSearch = InitSearchMedia(keyword, scope, searchTypes)
            ).data
        }

    }

    /**
     * 查询搜索状态
     *
     * @param searchId 搜索的 id 号
     * @param marker 分页标记
     */
    suspend fun searchMore(searchId: String, marker: Long): SearchPartContent {

        return runWithBeaconReport("GetSearch", searchId) { accessToken ->
            SMHService.shared.searchMore(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                userId = userSpace.userId,
                searchId = searchId,
                marker = marker,
            ).data
        }
    }

    /**
     * 完成搜索
     *
     * @param searchId 搜索的 id 号
     */
    suspend fun deleteSearch(searchId: String) {

        return runWithBeaconReport("DeleteSearch", searchId) { accessToken ->
            SMHService.shared.deleteSearch(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                userId = userSpace.userId,
                searchId = searchId
            ).checkSuccess()
        }
    }

    /**
     * 列出回收站项目
     *
     * @param page 文件页码
     * @param pageSize 一页的数据量
     * @param orderType 排序方式
     * @param orderDirection 排序方向
     * @return 文件列表
     */
    @JvmOverloads
    suspend fun listRecycled(
        page: Int,
        pageSize: Int,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
    ): RecycledContents {
        return runWithBeaconReport("ListRecycled", null) { accessToken ->
            val contents = ArrayList<RecycledItem>()

            val recycledContents = SMHService.shared.listRecycled(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                userId = userSpace.userId,
                page = page,
                pageSize = pageSize,
                orderBy = orderType,
                orderByType = orderDirection
            ).data
            val totalNum = recycledContents.totalNum
            contents.addAll(recycledContents.contents)
            RecycledContents(totalNum = totalNum, contents = contents)
        }
    }

    /**
     * 创建文件夹
     *
     * @param dir 文件夹
     * @return 执行结果，true 表示创建成功，false 表示创建失败
     */
    suspend fun createDirectory(dir: Directory): CreateDirectoryResult {
        val path = checkNotNull(dir.path)
        return runWithBeaconReport("PutDirectory", dir.path) { accessToken ->
            SMHService.shared.createDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = path,
                accessToken = accessToken,
                userId = userSpace.userId,
            ).data
        }
    }

    /**
     * 重命名文件夹
     *
     * @param target 目标文件夹
     * @param source 源文件夹
     * @param conflictStrategy 冲突处理方式
     * @return 执行结果，true 表示重命名成功，false 表示重命名失败
     */
    suspend fun renameDirectory(target: Directory, source: Directory, conflictStrategy: ConflictStrategy? = null): Boolean {
        val targetPath = checkNotNull(target.path)
        val sourcePath = checkNotNull(source.path)
        return runWithBeaconReport("RenameDirectory", sourcePath) { accessToken ->
            SMHService.shared.renameDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = targetPath,
                accessToken = accessToken,
                userId = userSpace.userId,
                conflictStrategy = conflictStrategy,
                moveAuthority = true,
                from = RenameDirectoryBody(sourcePath)
            ).checkSuccess()
        }
    }

    /**
     * 重命名或者移动文件
     *
     * @param targetName 目标文件名
     * @param targetDir 目标文件夹，默认为根目录
     * @param sourceName 源文件名
     * @param sourceDir 源文件夹，默认为根目录
     * @param conflictStrategy 文件名冲突时是否覆盖
     *
     * @return 重命名结果
     */
    suspend fun renameFile(targetName: String, targetDir: Directory = rootDirectory,
                           sourceName: String, sourceDir: Directory = rootDirectory,
                           conflictStrategy: ConflictStrategy? = null): RenameFileResponse {
        val sourceKey = smhKey(sourceDir.path, sourceName)
        return runWithBeaconReport("RenameFile", sourceKey) { accessToken ->
            val targetKey = smhKey(targetDir.path, targetName)
            SMHService.shared.renameFile(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = targetKey,
                conflictStrategy = conflictStrategy,
                accessToken = accessToken,
                userId = userSpace.userId,
                from = RenameFileBody(sourceKey)
            ).data
        }
    }

    /**
     * 检查文件状态
     *
     * @param name 文件名称
     * @param dir 文件所在的文件夹
     */
    suspend fun headFile(
        name: String,
        dir: Directory = rootDirectory
    ): HeadFileContent {
        val filePath = dir.path?.let { "$it/$name" } ?: name
        return runWithBeaconReport("HeadFile", filePath) { accessToken ->
            val headFileResponse = SMHService.shared.headFile(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken
            )
            headFileResponse.checkSuccess()
            HeadFileContent(headFileResponse.headers())
        }
    }

    /**
     * 删除文件夹
     *
     * @param dir 文件夹
     * @return 执行结果，true 表示成功，false 表示失败
     */
    suspend fun deleteDirectory(dir: Directory): Boolean {
        val path = checkNotNull(dir.path)
        return runWithBeaconReport("DeleteDirectory", dir.path) { accessToken ->
            SMHService.shared.deleteDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = path,
                accessToken = accessToken,
                userId = userSpace.userId,
            ).checkSuccess()
        }
    }

    /**
     * 获取文件信息
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 所在文件夹，默认是根目录下
     * @param historyId 历史版本号
     * @return 文件信息
     */
    @JvmOverloads
    suspend fun getFileInfo(
        name: String,
        dir: Directory = rootDirectory,
        historyId: Long? = null
    ): FileInfo {
        val filePath = dir.path?.let { "$it/$name" } ?: name
        return runWithBeaconReport("GetFileInfo", filePath) { accessToken ->
            SMHService.shared.getFileInfo(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                historyId = historyId,
                accessToken = accessToken
            ).data
        }
    }

    /**
     * 获取文件夹信息
     *
     * @param dir 所在文件夹，默认是根目录下
     */
    @JvmOverloads
    suspend fun getDirectoryInfo(
        dir: Directory = rootDirectory,
    ): DirectoryInfo {
        return runWithBeaconReport("GetDirectoryInfo", dir.path) { accessToken ->
            SMHService.shared.getDirectoryInfo(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = dir.path?: "",
                accessToken = accessToken
            ).data
        }
    }

    /**
     * 获取文件缩略图
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 所在文件夹，默认是根目录下
     * @param size 生成的预览图尺寸
     *
     */
    @JvmOverloads
    suspend fun getThumbnail(name: String,
                                    dir: Directory = rootDirectory,
                                    size: Int? = null
    ): ThumbnailResult {
        val filePath = dir.path?.let { "$it/$name" } ?: name
        return runWithBeaconReport("GetThumbnail", filePath) { accessToken ->
            val thumbnailResult = SMHService.shared.getThumbnail(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                size = size,
                accessToken = accessToken,
                userId = userSpace.userId
            )
            ThumbnailResult(thumbnailResult.headers())
        }
    }


    /**
     * 初始化简单上传
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param meta 文件元数据
     * @param dir 所在文件夹，默认是根目录下
     * @param conflictStrategy 冲突处理方式
     * @return 上传信息
     */
    @JvmOverloads
    suspend fun initUpload(
        name: String,
        meta: Map<String, String>? = null,
        dir: Directory = rootDirectory,
        conflictStrategy: ConflictStrategy? = null
    ): InitUpload {
        val filePath = dir.path?.let { "$it/$name" } ?: name
        return runWithBeaconReport("InitUpload", filePath) { accessToken ->
            val metaData = meta?.mapKeys { "x-smh-meta-${it.key}" }
            SMHService.shared.initUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken,
                conflictStrategy = conflictStrategy,
                metaData = metaData ?: emptyMap()
            ).data
        }
    }

    /**
     * 初始化分块上传
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param meta 文件元数据
     * @param dir 所在文件夹，默认是根目录下
     * @param conflictStrategy 冲突处理方式
     * @return 上传信息
     */
    @JvmOverloads
    suspend fun initMultipartUpload(
        name: String,
        meta: Map<String, String>? = null,
        dir: Directory = rootDirectory,
        conflictStrategy: ConflictStrategy? = null,
        overrideOnNameConflict: Boolean? = true
    ): InitUpload {
        val filePath = dir.path?.let { "$it/$name" } ?: name
        val compatConflictStrategy = if (overrideOnNameConflict == true) {
            ConflictStrategy.OVERWRITE
        } else {
            conflictStrategy
        }
        return runWithBeaconReport("InitMultipartUpload", filePath) { accessToken ->
            val metaData = meta?.mapKeys { "x-smh-meta-${it.key}" }
            SMHService.shared.initMultipartUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken,
                conflictStrategy = compatConflictStrategy,
                metaData = metaData ?: emptyMap()
            ).data
        }
    }

    /**
     * 列出分片上传任务信息
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 上传信息
     */
    suspend fun listMultipartUpload(confirmKey: String): MultiUploadMetadata {
        val meta = runWithBeaconReport("GetMultipartUploadMetadata", confirmKey) { accessToken ->
                SMHService.shared.listMultipartUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                confirmKey = confirmKey,
                accessToken = accessToken
            ).data
        }
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
    suspend fun multipartUpload(metadata: MultiUploadMetadata, uri: Uri, size: Long, cancelHandler: CancelHandler? = null, cosXmlProgressListener: CosXmlProgressListener? = null): String? {
        return transfer.multipartUpload(metadata, uri, size, cancelHandler, cosXmlProgressListener)
    }


    /**
     * 简单上传
     *
     * @param uploader 上传信息
     * @param uri 本地文件 URI
     * @return 上传成功返回的 eTag
     */
    suspend fun upload(uploader: InitUpload, uri: Uri, progressListener: CosXmlProgressListener? = null): String? {
        return transfer.upload(uploader, uri, progressListener)
    }

    /**
     * 取消上传
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 执行结果
     */
    suspend fun cancelUpload(confirmKey: String): Boolean {
        return runWithBeaconReport("CancelUpload", confirmKey) { accessToken ->
            SMHService.shared.cancelUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                confirmKey = confirmKey,
                accessToken = accessToken
            ).isSuccess
        }
    }

    /**
     * 完成上传
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 上传结果
     */
    suspend fun confirmUpload(confirmKey: String, crc64: String? = null): ConfirmUpload {
        return runWithBeaconReport("ConfirmUpload", confirmKey) { accessToken ->
            SMHService.shared.confirmUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                confirmKey = confirmKey,
                confirmUploadRequestBody = ConfirmUploadRequestBody(crc64),
                accessToken = accessToken,
            ).data
        }
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
        val filePath = dir.path?.let { "$it/$name" } ?: name
        val resp = runWithBeaconReport("InitDownload", filePath) { accessToken ->
             SMHService.shared.initDownload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken
            )
        }
        resp.checkSuccess()
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
    suspend fun download(url: String, contentUri: Uri, offset: Long = 0L, cancelHandler: CancelHandler? = null,
        cosXmlProgressListener: CosXmlProgressListener? = null) {
        val result = transfer.download(url, contentUri, offset, cancelHandler, cosXmlProgressListener)
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
     * 下载文件
     *
     * @param url 文件下载地址
     * @param contentUri 本地文件 URI
     * @param offset 请求文件偏移
     */
    @JvmOverloads
    suspend fun download(url: String, fullPath: String, offset: Long = 0L, cancelHandler: CancelHandler? = null,
                         cosXmlProgressListener: CosXmlProgressListener? = null) {
        val result = transfer.download(url, fullPath, offset, cancelHandler, cosXmlProgressListener)
        withContext(Dispatchers.IO) {
            // verify file size
            context.contentResolver.openFileDescriptor(Uri.fromFile(File(fullPath)), "r")?.use {
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
     * @param permanent 是否永久删除，为 true 表示不放到回收站中
     * @return 执行结果
     */
    suspend fun delete(
        name: String,
        dir: Directory = rootDirectory,
        permanent: Boolean = false,
    ): DeleteMediaResult {
        val filePath = dir.path?.let { "$it/$name" } ?: name
        return runWithBeaconReport("DeleteFile", filePath) { accessToken ->
            SMHService.shared.deleteFile(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                permanent = permanent,
                filePath = filePath,
                accessToken = accessToken
            ).data
        }
    }

    /**
     * 删除回收站文件
     *
     * @param itemId 回收站文件 id
     */
    suspend fun deleteRecycledItem(
        itemId: Long
    ): Boolean {
        return runWithBeaconReport("DeleteRecycled", itemId.toString()) { accessToken ->
            SMHService.shared.deleteRecycledItem(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                itemId = itemId,
                accessToken = accessToken
            ).checkSuccess()
        }
    }

    /**
     * 批量删除回收站文件
     *
     * @param itemIds 回收站文件 id
     */
    suspend fun deleteRecycledItems(
        itemIds: List<Long>
    ): Boolean {
        return runWithBeaconReport("DeletesRecycled", null) { accessToken ->
            SMHService.shared.deleteRecycledItems(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                itemIds = itemIds,
                accessToken = accessToken
            ).checkSuccess()
        }
    }

    /**
     * 将文件从回收站中恢复
     *
     * @param itemId 回收站文件 id
     */
    suspend fun restoreRecycledItem(
        itemId: Long
    ): String? {
        return runWithBeaconReport("RestoreRecycled", itemId.toString()) { accessToken ->
            SMHService.shared.restoreRecycledItem(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                itemId = itemId,
                accessToken = accessToken
            ).data.path?.joinToString("/")
        }
    }

    /**
     * 将文件批量从回收站中恢复
     *
     * @param itemIds 回收站文件 id
     */
    suspend fun restoreRecycledItems(
        itemIds: List<Long>
    ): BatchResponse {
        return runWithBeaconReport("RestoresRecycled", null) { accessToken ->
            SMHService.shared.restoreRecycledItems(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                itemIds = itemIds,
                accessToken = accessToken
            ).data
        }
    }

    /**
     * 清空回收站
     */
    suspend fun clearRecycledItem(): Boolean {
        return runWithBeaconReport("ClearRecycled", null) { accessToken ->
            SMHService.shared.clearRecycled(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken
            ).checkSuccess()
        }
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
        val filePath = dir.path?.let { "$it/$name" } ?: name
        return runWithBeaconReport("CreateSynLink", filePath) { accessToken ->
            SMHService.shared.createSymLink(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken,
                force = if (overrideOnNameConflict) 1 else 0,
                linkTo = SymLinkBody(sourceFileName)
            ).data
        }
    }

    /**
     * 批量删除
     *
     * @param items 需要批量删除的文件或文件夹
     */
    suspend fun batchDelete(
        items: List<BatchDeleteItem>
    ): BatchResponse {

        return runWithBeaconReport("BatchDelete", items.getOrNull(0)?.path) { accessToken ->
            SMHService.shared.batchDelete(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                items = items
            ).data
        }
    }

    /**
     * 批量复制
     *
     * @param items 需要批量复制的文件或文件夹
     */
    suspend fun batchCopy(
        items: List<BatchCopyItem>,
    ): BatchResponse {
        return runWithBeaconReport("BatchCopy", items.getOrNull(0)?.copyFrom) { accessToken ->
            SMHService.shared.batchCopy(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                items = items
            ).data
        }
    }

    /**
     * 批量移动
     *
     * @param items 需要批量移动的文件或文件夹
     */
    suspend fun batchMove(
        items: List<BatchMoveItem>,
    ): BatchResponse {
        return runWithBeaconReport("BatchMove", items.getOrNull(0)?.from) { accessToken ->
            SMHService.shared.batchMove(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                items = items
            ).data
        }
    }

    /**
     * 批量保存至网盘
     *
     * @param shareAccessToken accessToken
     * @param items 需要批量保存至网盘的文件或文件夹
     */
    suspend fun batchSaveToDisk(
        shareAccessToken: String,
        items: List<BatchSaveToDiskItem>,
    ): BatchResponse {
        return runWithBeaconReport("BatchSaveToDisk", shareAccessToken) { accessToken ->
            SMHService.shared.batchSaveToDisk(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                shareAccessToken = shareAccessToken,
                items = items
            ).data
        }
    }


    /**
     * 查询批量任务
     *
     * @param taskIds 任务 id
     */
    suspend fun queryTasks(
        taskIds: List<Long>
    ): List<BatchResponse> {
        val taskIdList = taskIds.joinToString(",")
        return runWithBeaconReport("QueryTasks", taskIds.getOrNull(0).toString()) { accessToken ->
            SMHService.shared.queryTasks(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                taskIdList = taskIdList
            ).data
        }
    }

    /**
     * 给目录分配权限
     *
     * @param dirPath 目录路径
     * @param authorizeToContent 授权信息
     */
    suspend fun addAuthorityDirectory(dirPath: String, authorizeToContent: AuthorizeToContent) {

        return runWithBeaconReport("AddDirectoryAuthority", dirPath) { accessToken ->
            SMHService.shared.addAuthorizeDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = dirPath,
                accessToken = accessToken,
                authorizeToContent = authorizeToContent
            ).checkSuccess()
        }
    }

    /**
     * 删除目录分配的授权
     *
     * @param dirPath 目录路径
     * @param authorizeToContent 授权信息
     */
    suspend fun deleteDirectoryAuthority(dirPath: String, authorizeToContent: AuthorizeToContent) {

        return runWithBeaconReport("DeleteDirectoryAuthority", dirPath) { accessToken ->
            SMHService.shared.deleteAuthorityDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = dirPath,
                accessToken = accessToken,
                authorizeToContent = authorizeToContent
            ).checkSuccess()
        }
    }


    /**
     * 获取相簿封面链接
     *
     * @param albumName 相簿名，分相簿媒体库必须指定该参数，不分相簿媒体库不能指定该参数
     * @param size 图片缩放大小
     * @return 封面图片下载链接
     */
    @JvmOverloads
    suspend fun getAlbumCoverUrl(
       albumName: String? = null,
       size: String? = null
    ): String? {
        return runWithBeaconReport("GetAlbumCover", albumName) { accessToken ->
            if (albumName != null) {
                SMHService.shared.getAlbumCoverUrlInAlbum(
                    libraryId = libraryId,
                    spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                    albumName = albumName,
                    accessToken = accessToken
                ).header("Location")
            } else {
                SMHService.shared.getAlbumCoverUrl(
                    libraryId = libraryId,
                    spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                    size = size,
                    accessToken = accessToken
                ).header("Location")
            }
        }
    }

    /**
     * 删除文件的历史版本
     *
     * @param historyIds 历史版本 id 号
     */
    suspend fun deleteHistoryMedia(historyIds: List<Long>) {

        runWithBeaconReport("DeleteHistoryMedias", historyIds.getOrNull(0).toString()) { accessToken ->
            SMHService.shared.deleteHistoryMedia(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                historyIds = historyIds,
            ).checkSuccess
        }
    }

    /**
     * 将指定的历史版本恢复为最新版本
     *
     * @param historyId 历史版本 id 号
     */
    suspend fun restoreHistoryMedia(historyId: Long): MediaContent {
        
        return runWithBeaconReport("RestoreHistoryMedias", historyId.toString()) { accessToken ->
            SMHService.shared.restoreHistoryMedia(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                historyId = historyId,
            ).data
        }
    }

    /**
     * 查询是否开启了历史版本
     *
     */
    suspend fun getHistoryStatus(): HistoryStatus {

        return runWithBeaconReport("GetLibraryHistory", null) { accessToken ->
            SMHService.shared.getHistoryStatus(
                libraryId = libraryId,
                accessToken = accessToken).data
        }
    }

    /**
     * 删除目录同步
     */
    suspend fun deleteDirectoryLocalSync(syncId: Int) {

        return runWithBeaconReport("DeleteDirectoryLocalSync", syncId.toString()) { accessToken ->
            SMHService.shared.deleteDirectoryLocalSync(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                syncId = syncId,
                accessToken = accessToken).checkSuccess()
        }
    }

    /**
     * 设置目录为同步盘
     */
    suspend fun putDirectoryLocalSync(path: String, strategy: DirectoryLocalSyncStrategy, localPath: String): PutDirectoryLocalSyncResponseBody {

        return runWithBeaconReport("PutDirectoryLocalSync", path) { accessToken ->
            SMHService.shared.putDirectoryLocalSync(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                body = PutDirectoryLocalSyncRequestBody(path, strategy, localPath),
                userId = user.userSpace.userId,
                accessToken = accessToken).data
        }
    }


    companion object {
        public fun smhKey(directory: String?, name: String): String {
            return if (TextUtils.isEmpty(directory)) name else "$directory/$name"
        }
    }

    private fun String.query(key: String, value: Any?): String {
        return this.concatIf("&$key=$value", value != null)
    }

    private fun String.concatIf(s: String, condition: Boolean): String {
        return if (condition) {
            "${this}$s"
        } else {
            this
        }
    }

    private suspend fun <R> retryWhenTokenExpired(f: suspend (accessToken: String) -> R): R {
        return try {
            val token = ensureValidAK()
            f(token.token)
        } catch (e: Exception) {
            if (e is SMHException && e.isTokenExpiredException()) {
                val refreshToken = ensureRefreshValidAK()
                f(refreshToken.token)
            } else {
                throw e
            }
        }
    }

    private suspend fun <R> runWithBeaconReport(requestName: String, path: String?, f: suspend (accessToken: String) -> R): R {
        val startTime = System.currentTimeMillis()
        return try {
            
            val result = retryWhenTokenExpired(f)
            val tookTime = System.currentTimeMillis() - startTime

            SMHSuccessRequestTrackEvent(
                requestName = requestName,
                smhUser = user,
                smhPath = path,
                tookTime = tookTime,
            ).trackWithBeaconParams(context)

            result
        } catch (exception: Exception) {
           
            val tookTime = System.currentTimeMillis() - startTime
            SMHFailureRequestTrackEvent(
                requestName = requestName,
                smhUser = user,
                smhPath = path,
                tookTime = tookTime,
                exception = exception,
            ).trackWithBeaconParams(context)

            throw exception
        }
    }
}

fun SMHException.isTokenExpiredException(): Boolean {

    return (this.statusCode == 403 && this.errorCode in listOf(
        "InvalidAuthCode", "AuthorizationExpired", "InvalidAuthorization",
        "InvalidRefreshToken", "InvalidAccessToken", "InvalidUserToken",

        "UserDisabled", // 用户已退出该组织
    ))
}




