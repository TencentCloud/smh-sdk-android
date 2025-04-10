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
import com.tencent.cloud.smh.api.data
import com.tencent.cloud.smh.api.dataOrNull
import com.tencent.cloud.smh.api.model.*
import com.tencent.cloud.smh.api.retrofit.checkSuccess
import com.tencent.cloud.smh.api.retrofit.code
import com.tencent.cloud.smh.api.retrofit.data
import com.tencent.cloud.smh.api.retrofit.header
import com.tencent.cloud.smh.api.retrofit.headers
import com.tencent.cloud.smh.api.retrofit.isSuccess
import com.tencent.cloud.smh.ext.cosPathEncode
import com.tencent.cloud.smh.ext.formatToUtc
import com.tencent.cloud.smh.ext.runWithSuspend
import com.tencent.cloud.smh.track.SMHFailureRequestTrackEvent
import com.tencent.cloud.smh.track.SMHSuccessRequestTrackEvent
import com.tencent.cloud.smh.transfer.*
import com.tencent.cloud.smh.utils.Utils
import com.tencent.cos.xml.common.VersionInfo
import com.tencent.qcloud.core.http.QCloudHttpClient
import com.tencent.qcloud.core.http.QCloudHttpRequest
import com.tencent.qcloud.core.logger.AndroidLogcatAdapter
import com.tencent.qcloud.core.logger.QCloudLogger
import com.tencent.qcloud.core.task.TaskExecutors
import com.tencent.qcloud.track.Constants
import com.tencent.qcloud.track.QCloudTrackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.InputStream
import java.math.BigDecimal
import java.net.URL
import java.util.*
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

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
    val isDebuggable: Boolean = false,
    private val customHost: String = "",
    val isPrivate: Boolean = false,
) {
    val shared: SMHService
    private val httpClient = QCloudHttpClient.getDefault()
    internal val rootDirectory = Directory()
    private val libraryId get() = user.libraryId
    private val userSpace get() = user.userSpace

    private val defaultHost = "api.tencentsmh.cn"
    private fun host() = if (customHost.isNotEmpty()) {
        customHost
    } else {
        defaultHost
    }

    init {
        if(isDebuggable) {
            QCloudLogger.addAdapter(AndroidLogcatAdapter())
        }

        var packageName = ""
        var versionName = ""
        try {
            packageName = context.packageName
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            versionName = packageInfo.versionName
        } catch (_: Exception){}
        SMHService.userAgent = "app:$packageName/$versionName-sdk:${VersionInfo.getUserAgent()}"

        shared = SMHService.shared(baseUrl())

        // 初始化QCloudTrack
        QCloudTrackService.getInstance().init(context.applicationContext)
        QCloudTrackService.getInstance().setDebug(isDebuggable)
        // 启动上报
        reportSdkStart()
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
        return user.getSpaceState(this).dataOrNull?.remainSize
    }

    /**
     * 获取用户空间状态
     */
    suspend fun getUserSpaceState(): UserSpaceState = user.getSpaceState(this).data

    fun baseUrl(): String {
        val host = host()
        return Utils.baseUrl(host)
    }

    fun getProtocol(): String{
        val host = host()
        return if(host.startsWith("https://", true)){
            "https"
        } else if(host.startsWith("http://", true)){
            "http"
        } else{
            "https"
        }
    }

    fun isHttps(): Boolean{
        return "https" == getProtocol()
    }

    /**
     * 是否为正式环境
     */
    fun isReleaseHost() = host() == defaultHost

    /**
     * 列出所有的文件列表
     */
    @JvmOverloads
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
                    authorityButtonList = directoryContents.authorityButtonList,
                    localSync = directoryContents.localSync,
                    contents = contents
                )
            }
        }
    }

    /**
     * 通过 marker + limit 的方式列出所有的文件列表
     */
    @JvmOverloads
    suspend fun listAllWithMarker(

        dir: Directory,
        limit: Int = 100,
    ): DirectoryContents {

        var nextMarker: String? = null
        val contents = ArrayList<MediaContent>()

        while (true) {

            val directoryContents = listWithMarker(
                dir = dir,
                marker = nextMarker,
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
                    authorityButtonList = directoryContents.authorityButtonList,
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
        sortType: String? = null,
        withInode: Boolean = false,
        withFavoriteStatus: Boolean = false,
    ): DirectoryContents {
        return runWithBeaconReport(RequestNameListDirectoryWithPage, dir.path) { accessToken ->
            shared.listDirectoryByPageSize(
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
                sortType = sortType,
                withInode = if(withInode) 1 else 0,
                withFavoriteStatus = if(withFavoriteStatus) 1 else 0,
            ).data.also { directoryContents ->
                directoryContents.contents.forEach { content ->
                    content.metaData = Utils.metaDataTrim(content.metaData)
                }
            }
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
        marker: String? = null,
        limit: Int? = null,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
        directoryFilter: DirectoryFilter? = null,
        sortType: String? = null,
        eTag: String? = null,
        withInode: Boolean = false,
        withFavoriteStatus: Boolean = false,
    ): DirectoryContents {

        return runWithBeaconReport(RequestNameListDirectoryWithMarker, dir.path) { accessToken ->
            shared.listDirectoryByMarkerLimit(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = dir.path ?: "",
                accessToken = accessToken,
                userId = userSpace.userId,
                marker = marker,
                limit = limit,
                orderBy = orderType,
                orderByType = orderDirection,
                directoryFilter = directoryFilter,
                sortType = sortType,
                eTag = eTag,
                withInode = if(withInode) 1 else 0,
                withFavoriteStatus = if(withFavoriteStatus) 1 else 0,
            ).data.also { directoryContents ->
                directoryContents.contents.forEach { content ->
                    content.metaData = Utils.metaDataTrim(content.metaData)
                }
            }
        }
    }

    /**
     * 通过 offset + limit 的方式来列出文件列表
     *
     * @param dir 文件夹
     * @param offset 文件偏移量
     * @param limit 列出的数量
     * @param orderType 排序方式
     * @param orderDirection 排序方向
     * @param directoryFilter 过滤类型
     *
     * @return 文件列表
     */
    @JvmOverloads
    suspend fun listWithOffset(
        dir: Directory,
        offset: Long,
        limit: Int,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
        directoryFilter: DirectoryFilter? = null,
        sortType: String? = null,
        withInode: Boolean = false,
        withFavoriteStatus: Boolean = false,
    ): DirectoryContents {

        return runWithBeaconReport("ListDirectoryWithOffset", dir.path) { accessToken ->
            shared.listDirectoryByOffsetLimit(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = dir.path ?: "",
                accessToken = accessToken,
                userId = userSpace.userId,
                offset = offset,
                limit = limit,
                orderBy = orderType,
                orderByType = orderDirection,
                directoryFilter = directoryFilter,
                sortType = sortType,
                withInode = if(withInode) 1 else 0,
                withFavoriteStatus = if(withFavoriteStatus) 1 else 0,
            ).data.also { directoryContents ->
                directoryContents.contents.forEach { content ->
                    content.metaData = Utils.metaDataTrim(content.metaData)
                }
            }
        }
    }


    /**
     * 获取角色列表，不同的角色对应不同的权限
     */
    suspend fun getRoleList(): List<Role> {
        return runWithBeaconReport("GetRoleList", null) { accessToken ->
            shared.getRoleList(
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
    @JvmOverloads
    suspend fun getDownloadAccessUrl(
        path: String,
        historyId: Long? = null,
        encode: Boolean = false,
        purpose: Purpose = Purpose.DOWNLOAD
    ): String {
        val encodePath = if (encode) {
            path
        } else {
            path.cosPathEncode()
        }
        return retryWhenTokenExpired { accessToken ->
            "${baseUrl()}api/v1/file/$libraryId/${userSpace.spaceId}/${encodePath}?"
                .query("history_id", historyId).query("purpose", purpose)
                .query("access_token", accessToken).query("user_id", userSpace.userId)
        }
    }

    /**
     * 获取文档预览链接
     * @param filePath 文件路径
     * @param historyId 历史版本号
     * @param purpose 用途
     */
    @JvmOverloads
    suspend fun getPreviewAccessUrl(
        filePath: String,
        historyId: Long? = null,
        purpose: Purpose? = null,
        lang: String = "zh-CN"
    ): String {
        return retryWhenTokenExpired { accessToken ->
            "${baseUrl()}api/v1/file/${libraryId}/${userSpace.spaceId}/${filePath.cosPathEncode()}?preview&mobile"
                .query("history_id", historyId).query("purpose", purpose)
                .query("access_token", accessToken).query("user_id", userSpace.userId)
                .query("lang", lang)
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
     * @param frameNumber 帧数，针对 gif 的降帧处理；
     * @param purpose 用途
     */
    @JvmOverloads
    suspend fun getThumbnailAccessUrl(
        name: String,
        dir: Directory = rootDirectory,
        historyId: Long? = null,
        size: Int? = null,
        scale: Int? = null,
        widthSize: Int? = null,
        heightSize: Int? = null,
        frameNumber: Int? = null,
        purpose: Purpose? = null
    ): String {
        val filePath = smhKey(dir.path, name)
        return retryWhenTokenExpired { accessToken ->
            "${baseUrl()}api/v1/file/${libraryId}/${userSpace.spaceId}/${filePath.cosPathEncode()}?preview&mobile"
                .query("history_id", historyId)
                .query("purpose", purpose)
                .query("size", size)
                .query("scale", scale)
                .query("width_size", widthSize)
                .query("height_size", heightSize)
                .query("frame_number", frameNumber)
                .query("access_token", accessToken)
                .query("user_id", userSpace.userId)
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
    @JvmOverloads
    suspend fun getMyAuthorizedDirectory(page: Int, pageSize: Int, orderType: OrderType? = null,
                                         orderDirection: OrderDirection? = null): AuthorizedContent {
        return runWithBeaconReport("ListAuthorizedDirectory", null) { accessToken ->
             shared.getMyAuthorizedDirectory(
                libraryId = libraryId,
                page = page,
                pageSize = pageSize,
                orderBy = orderType,
                orderByType = orderDirection,
                accessToken = accessToken,
            ).data
        }
    }

    @JvmOverloads
    suspend fun getMyAuthorizedDirectoryWithMarker(
        marker: String?, limit: Int, orderType: OrderType?, orderDirection: OrderDirection?, eTag: String?
    ): AuthorizedContent {
        return runWithBeaconReport("ListAuthorizedDirectory", null) { accessToken ->
            shared.getMyAuthorizedDirectoryWithMarker(
                libraryId = libraryId,
                marker = marker,
                limit = limit,
                orderBy = orderType,
                orderByType = orderDirection,
                eTag = eTag,
                accessToken = accessToken,
            ).data
        }
    }

    /**
     * 初始化搜索，可能会返回一定量的搜索结果
     *
     * @param keyword 搜索关键字，可使用空格分隔多个关键字，关键字之间为“或”的关系并优先展示匹配关键字较多的项目
     * @param searchTypes 搜索的文件类型
     * @param tags 搜索标签
     * @param scope 搜索范围，指定搜索的目录，如搜索根目录可指定为空字符串、“/”或不指定该字段
     * @param extname 搜索文件后缀，字符串数组
     * @param creators 搜索创建/更新者
     * @param minFileSize 搜索文件大小范围，单位 Byte
     * @param maxFileSize 搜索文件大小范围，单位 Byte
     * @param modificationTimeStart 搜索更新时间范围，时间戳字符串，与时区无关
     * @param modificationTimeEnd 搜索更新时间范围，时间戳字符串，与时区无关
     * @param orderBy 排序字段，可选参数，当前支持按名称、修改时间、文件大小、创建时间排序具体类型如下：
     * name：按名称排序。
     * modificationTime：按修改时间排序。
     * size：按文件大小排序。
     * creationTime：按创建时间排序。
     * @param orderByType 排序方式，升序为 asc，降序为 desc，可选参数。
     */
    @JvmOverloads
    suspend fun initSearch(
        searchTypes: List<SearchType>,
        keyword: String? = null,
        scope: String? = null,
        tags: List<SearchTag>? = null,
        extname: List<String>? = null,
        creators: List<SearchCreator>? = null,
        minFileSize: Long? = null,
        maxFileSize: Long? = null,
        modificationTimeStart: String? = null,
        modificationTimeEnd: String? = null,
        orderBy: OrderType? = null,
        orderByType: OrderDirection? = null,
        withInode: Boolean = false,
        withFavoriteStatus: Boolean = false,
        searchMode: String? = null,
        labels: List<String>? = null,
        categories: List<String>? = null,
    ): SearchPartContent {

        return runWithBeaconReport("InitSearch", keyword) { accessToken ->
            shared.initSearch(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                userId = userSpace.userId,
                withInode = if(withInode) 1 else 0,
                withFavoriteStatus = if(withFavoriteStatus) 1 else 0,
                initSearch = InitSearchMedia(
                    searchTypes, keyword, scope,
                    tags ?: emptyList(),
                    extname ?: emptyList(),
                    creators ?: emptyList(),
                    minFileSize, maxFileSize, modificationTimeStart, modificationTimeEnd,
                    orderBy, orderByType, searchMode, labels, categories
                )
            ).data
        }

    }

    /**
     * 查询搜索状态
     *
     * @param searchId 搜索的 id 号
     * @param marker 分页标记
     */
    suspend fun searchMore(
        searchId: String,
        marker: Long,
        withInode: Boolean = false,
        withFavoriteStatus: Boolean = false,
    ): SearchPartContent {

        return runWithBeaconReport("GetSearch", searchId) { accessToken ->
            shared.searchMore(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                userId = userSpace.userId,
                searchId = searchId,
                marker = marker,
                withInode = if(withInode) 1 else 0,
                withFavoriteStatus = if(withFavoriteStatus) 1 else 0,
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
            shared.deleteSearch(
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
            shared.listRecycled(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                userId = userSpace.userId,
                page = page,
                pageSize = pageSize,
                orderBy = orderType,
                orderByType = orderDirection
            ).data
        }
    }

    @JvmOverloads
    suspend fun listRecycledWithMarker(
        marker: String?,
        limit: Int? = null,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
        eTag: String? = null
    ): RecycledContents  {
        return runWithBeaconReport("ListRecycledWithMarker", null) { accessToken ->
            shared.listRecycledWithMarker(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                userId = userSpace.userId,
                marker = marker,
                limit = limit,
                eTag = eTag,
                orderBy = orderType,
                orderByType = orderDirection
            ).data
        }
    }


    /**
     * 创建文件夹
     *
     * @param dir 文件夹
     * @param withInode 0 或 1，是否返回 inode，即文件目录 ID，可选，默认不返回；
     * @param conflictStrategy 冲突处理方式
     * @return 执行结果，true 表示创建成功，false 表示创建失败
     */
    suspend fun createDirectory(dir: Directory, withInode: Boolean = false, conflictStrategy: ConflictStrategy? = null): CreateDirectoryResult? {
        val path = checkNotNull(dir.path)
        return runWithBeaconReport("PutDirectory", dir.path) { accessToken ->
            shared.createDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = path,
                withInode = if(withInode) 1 else 0,
                conflictStrategy = conflictStrategy,
                accessToken = accessToken,
                userId = userSpace.userId,
            ).checkSuccess
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
    @JvmOverloads
    suspend fun renameDirectory(target: Directory, source: Directory, conflictStrategy: ConflictStrategy? = null): RenameFileResponse? {
        val targetPath = checkNotNull(target.path)
        val sourcePath = checkNotNull(source.path)
        return runWithBeaconReport("RenameDirectory", sourcePath) { accessToken ->
            shared.renameDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = targetPath,
                accessToken = accessToken,
                userId = userSpace.userId,
                conflictStrategy = conflictStrategy,
                moveAuthority = true,
                from = RenameDirectoryBody(sourcePath)
            ).checkSuccess
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
    @JvmOverloads
    suspend fun renameFile(targetName: String, targetDir: Directory = rootDirectory,
                           sourceName: String, sourceDir: Directory = rootDirectory,
                           conflictStrategy: ConflictStrategy? = null): RenameFileResponse {
        val sourceKey = smhKey(sourceDir.path, sourceName)
        return runWithBeaconReport("RenameFile", sourceKey) { accessToken ->
            val targetKey = smhKey(targetDir.path, targetName)
            shared.renameFile(
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
        val filePath = smhKey(dir.path, name)
        return runWithBeaconReport("HeadFile", filePath) { accessToken ->
            val headFileResponse = shared.headFile(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken,
                userId = null
            )
            headFileResponse.checkSuccess()
            HeadFileContent(headFileResponse.headers())
        }
    }

    /**
     * 删除文件夹
     *
     * @param dir 文件夹
     * @param permanent 是否永久删除，为 true 表示不放到回收站中
     * @return 执行结果
     */
    suspend fun deleteDirectory(
        dir: Directory,
        permanent: Boolean = false
    ): DeleteMediaResult? {
        val path = checkNotNull(dir.path)
        return runWithBeaconReport("DeleteDirectory", dir.path) { accessToken ->
            shared.deleteDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = path,
                permanent = if(permanent) 1 else 0,
                accessToken = accessToken,
                userId = userSpace.userId,
            ).checkSuccess
        }
    }

    /**
     * 获取文件信息
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 所在文件夹，默认是根目录下
     * @param historyId 历史版本号
     * @param purpose 用途
     * @return 文件信息
     */
    @JvmOverloads
    suspend fun getFileInfo(
        name: String,
        dir: Directory = rootDirectory,
        historyId: Long? = null,
        purpose: Purpose? = null,
        trafficLimit: Long? = null
    ): FileInfo {
        val filePath = smhKey(dir.path, name)
        return runWithBeaconReport("GetFileInfo", filePath) { accessToken ->
            shared.getFileInfo(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                historyId = historyId,
                purpose = purpose,
                trafficLimit = trafficLimit,
                accessToken = accessToken,
                userId = null
            ).data.also { fileInfo ->
                fileInfo.metaData = Utils.metaDataTrim(fileInfo.metaData)!!
            }
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
        withInode: Int? = 0,
        withFavoriteStatus: Int? = 0,
    ): DirectoryInfo {
        return runWithBeaconReport("GetDirectoryInfo", dir.path) { accessToken ->
            shared.getDirectoryInfo(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = dir.path?: "",
                withInode = withInode,
                withFavoriteStatus = withFavoriteStatus,
                accessToken = accessToken,
                userId = null
            ).data.also { directoryInfo ->
                directoryInfo.metaData = Utils.metaDataTrim(directoryInfo.metaData)
            }
        }
    }

    /**
     * 获取文件缩略图
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 所在文件夹，默认是根目录下
     * @param size 生成的预览图尺寸
     * @param scale 等比例缩放百分比，可选参数，不传 Size 时生效；
     * @param widthSize 缩放宽度，不传高度时，高度按等比例缩放，不传 Size 和 Scale 时生效；
     * @param heightSize 缩放高度，不传宽度时，宽度按等比例缩放，不传 Size 和 Scale 时生效；
     * @param frameNumber 帧数，针对 gif 的降帧处理；
     * @param purpose 用途，可选参数，列表页传 list、预览页传 preview（默认）；
     */
    @JvmOverloads
    suspend fun getThumbnail(name: String,
                             dir: Directory = rootDirectory,
                             size: Int? = null,
                             scale: Int? = null,
                             widthSize: Int? = null,
                             heightSize: Int? = null,
                             frameNumber: Int? = null,
                             purpose: Purpose? = null
    ): ThumbnailResult {
        val filePath = smhKey(dir.path, name)
        return runWithBeaconReport("GetThumbnail", filePath) { accessToken ->
            val thumbnailResult = shared.getThumbnail(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                size = size,
                scale = scale,
                widthSize = widthSize,
                heightSize = heightSize,
                frameNumber = frameNumber,
                purpose = purpose,
                accessToken = accessToken,
                userId = userSpace.userId
            )
            ThumbnailResult(thumbnailResult.headers())
        }
    }

    /**
     * 快速上传
     * 不建议直接使用，而是使用高级上传，其中包含了quickUpload的逻辑
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 所在文件夹，默认是根目录下
     * @param conflictStrategy 冲突处理方式
     * @param filesize 上传文件大小，单位为字节（Byte），用于判断剩余空间是否足够
     * @return 上传信息 http状态码为202代表文件头hash命中
     */
    @JvmOverloads
    suspend fun quickUpload(
        name: String,
        dir: Directory = rootDirectory,
        uploadRequestBody: UploadRequestBody,
        conflictStrategy: ConflictStrategy? = null,
        meta: Map<String, String>? = null,
        filesize: Long? = null
    ): RawResponse {
        val filePath = smhKey(dir.path, name)
        val mutableMap: MutableMap<String, String> =
            meta?.mapKeys { "${X_SMH_META_KEY_PREFIX}${it.key}" }?.toMutableMap() ?: emptyMap<String, String>().toMutableMap()
        mutableMap["${X_SMH_META_KEY_PREFIX}creation-date"] = Date().formatToUtc()
        return runWithBeaconReport("InitUpload", filePath) { accessToken ->
            val smhResponse = shared.quickUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken,
                uploadRequestBody = uploadRequestBody,
                conflictStrategy = conflictStrategy,
                metaData = mutableMap,
                filesize = filesize,
                userId = null
            )
            val data = smhResponse.data
            data.statusCode = smhResponse.code()
            data
        }
    }

    /**
     * 初始化简单上传
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param meta 文件元数据
     * @param dir 所在文件夹，默认是根目录下
     * @param conflictStrategy 冲突处理方式
     * @param filesize 上传文件大小，单位为字节（Byte），用于判断剩余空间是否足够
     * @return 上传信息
     */
    @JvmOverloads
    suspend fun initUpload(
        name: String,
        meta: Map<String, String>? = null,
        dir: Directory = rootDirectory,
        conflictStrategy: ConflictStrategy? = null,
        filesize: Long? = null,
        uploadRequestBody: UploadRequestBody? = null,
    ): InitUpload {
        val filePath = smhKey(dir.path, name)
        return runWithBeaconReport("InitUpload", filePath) { accessToken ->
            val metaData = meta?.mapKeys { "${X_SMH_META_KEY_PREFIX}${it.key}" }
            shared.initUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken,
                conflictStrategy = conflictStrategy,
                filesize = filesize,
                metaData = metaData ?: emptyMap(),
                uploadRequestBody = uploadRequestBody,
                userId = null
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
     * @param filesize 上传文件大小，单位为字节（Byte），用于判断剩余空间是否足够
     * @return 上传信息
     */
    @JvmOverloads
    suspend fun publicInitMultipartUpload(
        name: String,
        meta: Map<String, String>? = null,
        dir: Directory = rootDirectory,
        conflictStrategy: ConflictStrategy? = null,
        filesize: Long? = null,
        uploadRequestBody: UploadRequestBody? = null,
    ): InitUpload {
        val filePath = smhKey(dir.path, name)
        return runWithBeaconReport("InitMultipartUpload", filePath) { accessToken ->
            val metaData = meta?.mapKeys { "${X_SMH_META_KEY_PREFIX}${it.key}" }
            shared.publicInitMultipartUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken,
                conflictStrategy = conflictStrategy,
                filesize = filesize,
                metaData = metaData ?: emptyMap(),
                uploadRequestBody = uploadRequestBody,
                userId = null
            ).data
        }
    }

    /**
     * 列出分片上传任务信息
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 上传信息
     */
    suspend fun publicListMultipartUpload(confirmKey: String): PublicMultiUploadMetadata {
        val meta = runWithBeaconReport("GetMultipartUploadMetadata", confirmKey) { accessToken ->
            shared.publicListMultipartUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                confirmKey = confirmKey,
                accessToken = accessToken,
                userId = null
            ).data
        }
        meta.confirmKey = confirmKey
        return meta
    }

    /**
     * 用于分块上传任务续期
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 上传信息
     */
    suspend fun publicRenewMultipartUpload(confirmKey: String): InitUpload {
        return runWithBeaconReport("RenewMultipartUpload", confirmKey) { accessToken ->
            shared.publicRenewMultipartUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                confirmKey = confirmKey,
                accessToken = accessToken,
                userId = null
            ).data
        }
    }

    /**
     * 初始化分块上传
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param partNumberRange 格式为逗号分隔的 区段，区段 可以是 单个数字 n，也可以是 由两个数字组成的范围 n-m
     * @param meta 文件元数据
     * @param dir 所在文件夹，默认是根目录下
     * @param conflictStrategy 冲突处理方式
     * @param filesize 上传文件大小，单位为字节（Byte），用于判断剩余空间是否足够
     * @return 上传信息
     */
    @JvmOverloads
    suspend fun initMultipartUpload(
        name: String,
        partNumberRange: String,
        meta: Map<String, String>? = null,
        dir: Directory = rootDirectory,
        conflictStrategy: ConflictStrategy? = null,
        filesize: Long? = null
    ): InitMultipartUpload {
        val filePath = smhKey(dir.path, name)
        return runWithBeaconReport("InitMultipartUpload", filePath) { accessToken ->
            val metaData = meta?.mapKeys { "${X_SMH_META_KEY_PREFIX}${it.key}" }
            shared.initMultipartUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken,
                conflictStrategy = conflictStrategy,
                filesize = filesize,
                metaData = metaData ?: emptyMap(),
                partNumberRange = PartNumberRange(partNumberRange),
                userId = null
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
                shared.listMultipartUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                confirmKey = confirmKey,
                accessToken = accessToken,
                    userId = null
            ).data
        }
        meta.confirmKey = confirmKey
        return meta
    }

    /**
     * 用于分块上传任务续期
     *
     * @param confirmKey 上传任务的 confirmKey
     * @param partNumberRange 格式为逗号分隔的 区段，区段 可以是 单个数字 n，也可以是 由两个数字组成的范围 n-m
     * @return 上传信息
     */
    suspend fun renewMultipartUpload(confirmKey: String, partNumberRange: String): InitMultipartUpload {
        return runWithBeaconReport("RenewMultipartUpload", confirmKey) { accessToken ->
            shared.renewMultipartUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                confirmKey = confirmKey,
                accessToken = accessToken,
                partNumberRange = PartNumberRange(partNumberRange),
                userId = null
            ).data
        }
    }

    /**
     * 取消上传
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 执行结果
     */
    suspend fun cancelUpload(confirmKey: String): Boolean {
        return runWithBeaconReport("CancelUpload", confirmKey) { accessToken ->
            shared.cancelUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                confirmKey = confirmKey,
                accessToken = accessToken,
                userId = null
            ).isSuccess
        }
    }

    /**
     * 完成上传
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 上传结果
     */
    @JvmOverloads
    suspend fun confirmUpload(
        confirmKey: String,
        crc64: String? = null,
        labels: List<String>? = null,
        category: String? = null,
        localCreationTime: String? = null,
        localModificationTime: String? = null,
    ): ConfirmUpload {
        return runWithBeaconReport("ConfirmUpload", confirmKey) { accessToken ->
            shared.confirmUpload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                confirmKey = confirmKey,
                confirmUploadRequestBody = ConfirmUploadRequestBody(
                    crc64,
                    labels = labels,
                    category = category,
                    localCreationTime = localCreationTime,
                    localModificationTime = localModificationTime
                ),
                accessToken = accessToken,
                userId = null
            ).data.also { confirmUpload ->
                confirmUpload.metaData = Utils.metaDataTrim(confirmUpload.metaData)
            }
        }
    }

    /**
     * 高级上传
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 所在文件夹，默认是根目录下
     * @param uri 本地文件 URI
     * @param inputStream 数据源流（数据源流需要外部在上传成功或失败后自行关闭）
     * @param confirmKey 上传任务的 confirmKey
     * @param conflictStrategy 冲突处理方式
     * @param meta 文件元数据
     * @param stateListener 状态监听器
     * @param progressListener 进度监听器
     * @param resultListener 结果监听器
     * @param quickUpload 是否开启秒传（默认开启）
     * @return 返回上传任务
     */
    @JvmOverloads
    fun upload(
        name: String,
        dir: Directory = rootDirectory,
        uri: Uri? = null,
        inputStream: InputStream? = null,
        confirmKey: String? = null,
        conflictStrategy: ConflictStrategy? = null,
        meta: Map<String, String>? = null,
        labels: List<String>? = null,
        category: String? = null,
        localCreationTime: String? = null,
        localModificationTime: String? = null,
        stateListener: SMHStateListener? = null,
        progressListener: SMHProgressListener? = null,
        resultListener: SMHResultListener? = null,
        initMultipleUploadListener: SMHInitMultipleUploadListener? = null,
        quickUpload: Boolean = true
    ): SMHUploadTask {
        val filePath = smhKey(dir.path, name)
        val uploadFileRequest = UploadFileRequest(
            key = filePath,
            localUri = uri,
            inputStream = inputStream,
            confirmKey = confirmKey,
            conflictStrategy = conflictStrategy,
            meta = meta,
            labels = labels,
            category = category,
            localCreationTime = localCreationTime,
            localModificationTime = localModificationTime,
        )
        uploadFileRequest.stateListener = stateListener
        uploadFileRequest.progressListener = progressListener
        uploadFileRequest.resultListener = resultListener
        uploadFileRequest.initMultipleUploadListener = initMultipleUploadListener
        return SMHUploadTask(
            context,
            this,
            uploadFileRequest,
            quickUpload
        )
    }

    /**
     * 初始化下载
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 文件所在文件夹，默认为根目录
     * @return 下载信息
     */
    @JvmOverloads
    suspend fun initDownload(
        name: String,
        dir: Directory = rootDirectory,
    ): InitDownload {
        val filePath = smhKey(dir.path, name)
        val resp = runWithBeaconReport("InitDownload", filePath) { accessToken ->
             shared.initDownload(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken,
                 userId = null
            )
        }
        resp.checkSuccess()
        return InitDownload(
            url = resp.header("Location"),
            metaData = Utils.metaDataTrimByHeaders(resp.headers())
        )
    }

    /**
     * 高级下载
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 所在文件夹，默认是根目录下
     * @param historyId 历史版本ID
     * @param localFullPath 本地文件路径（为null时代表字节流返回形式 result中会返回字节流）
     * @param stateListener 状态监听器
     * @param progressListener 进度监听器
     * @param resultListener 结果监听器
     * @return 返回下载任务
     */
    @JvmOverloads
    fun download(
        name: String,
        dir: Directory = rootDirectory,
        historyId: Long? = null,
        localFullPath: String? = null,
        rangeStart: Long? = null,
        rangeEnd: Long? = null,
        stateListener: SMHStateListener? = null,
        progressListener: SMHProgressListener? = null,
        resultListener: SMHResultListener? = null
    ): SMHDownloadTask {
        val filePath = smhKey(dir.path, name)
        val downloadFileRequest = DownloadFileRequest(
            key = filePath,
            historyId = historyId,
            localFullPath = localFullPath
        )
        rangeStart?.let {
            downloadFileRequest.setRange(it, rangeEnd)
        }
        downloadFileRequest.stateListener = stateListener
        downloadFileRequest.progressListener = progressListener
        downloadFileRequest.resultListener = resultListener
        val downloadTask = SMHDownloadTask(
            context,
            this,
            downloadFileRequest
        )
        return downloadTask
    }

    /**
     * 删除文件
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 文件所在文件夹，默认为根目录
     * @param permanent 是否永久删除，为 true 表示不放到回收站中
     * @return 执行结果
     */
    @JvmOverloads
    suspend fun delete(
        name: String,
        dir: Directory = rootDirectory,
        permanent: Boolean = false,
    ): DeleteMediaResult? {
        val filePath = smhKey(dir.path, name)
        return runWithBeaconReport("DeleteFile", filePath) { accessToken ->
            shared.deleteFile(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                permanent = if(permanent) 1 else 0,
                filePath = filePath,
                accessToken = accessToken,
                userId = null
            ).checkSuccess
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
            shared.deleteRecycledItem(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                itemId = itemId,
                accessToken = accessToken,
                userId = null
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
            shared.deleteRecycledItems(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                itemIds = itemIds,
                accessToken = accessToken,
                userId = null
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
            shared.restoreRecycledItem(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                itemId = itemId,
                accessToken = accessToken,
                userId = null
            ).data.path?.joinToString("/")
        }
    }

    /**
     * 将文件批量从回收站中恢复
     *
     * @param itemIds 回收站文件 id
     * @param queryTaskPolling 是否轮询查询任务结果，默认不轮询
     */
    suspend fun restoreRecycledItems(
        itemIds: List<Long>,
        queryTaskPolling: Boolean = false
    ): BatchResponse {
        var response = runWithBeaconReport("RestoresRecycled", null) { accessToken ->
            shared.restoreRecycledItems(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                itemIds = itemIds,
                accessToken = accessToken,
                userId = null
            ).data
        }
        if(queryTaskPolling){
            response = queryTaskPolling(response)
        }
        return response
    }

    /**
     * 清空回收站
     */
    suspend fun clearRecycledItem(): Boolean {
        return runWithBeaconReport("ClearRecycled", null) { accessToken ->
            shared.clearRecycled(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                userId = null
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
        val filePath = smhKey(dir.path, name)
        return runWithBeaconReport("CreateSynLink", filePath) { accessToken ->
            shared.createSymLink(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken,
                force = if (overrideOnNameConflict) 1 else 0,
                linkTo = SymLinkBody(sourceFileName),
                userId = null
            ).data.also { confirmUpload ->
                confirmUpload.metaData = Utils.metaDataTrim(confirmUpload.metaData)
            }
        }
    }

    /**
     * 批量删除
     *
     * @param items 需要批量删除的文件或文件夹
     * @param queryTaskPolling 是否轮询查询任务结果，默认不轮询
     */
    suspend fun batchDelete(
        items: List<BatchDeleteItem>,
        queryTaskPolling: Boolean = false
    ): BatchResponse {
        var response = runWithBeaconReport("BatchDelete", items.getOrNull(0)?.path) { accessToken ->
            shared.batchDelete(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                items = items,
                userId = null
            ).data
        }
        if(queryTaskPolling){
            response = queryTaskPolling(response)
        }
        return response
    }

    /**
     * 批量复制
     *
     * @param items 需要批量复制的文件或文件夹
     * @param queryTaskPolling 是否轮询查询任务结果，默认不轮询
     */
    suspend fun batchCopy(
        items: List<BatchCopyItem>,
        queryTaskPolling: Boolean = false
    ): BatchResponse {
        var response = runWithBeaconReport("BatchCopy", items.getOrNull(0)?.copyFrom) { accessToken ->
            shared.batchCopy(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                items = items,
                userId = null
            ).data
        }
        if(queryTaskPolling){
            response = queryTaskPolling(response)
        }
        return response
    }

    /**
     * 批量移动
     *
     * @param items 需要批量移动的文件或文件夹
     * @param queryTaskPolling 是否轮询查询任务结果，默认不轮询
     */
    suspend fun batchMove(
        items: List<BatchMoveItem>,
        queryTaskPolling: Boolean = false
    ): BatchResponse {
        var response = runWithBeaconReport("BatchMove", items.getOrNull(0)?.from) { accessToken ->
            shared.batchMove(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                items = items,
                userId = null
            ).data
        }
        if(queryTaskPolling){
            response = queryTaskPolling(response)
        }
        return response
    }

    /**
     * 批量保存至网盘
     *
     * @param shareAccessToken accessToken
     * @param items 需要批量保存至网盘的文件或文件夹
     * @param queryTaskPolling 是否轮询查询任务结果，默认不轮询
     */
    suspend fun batchSaveToDisk(
        shareAccessToken: String,
        items: List<BatchSaveToDiskItem>,
        queryTaskPolling: Boolean = false
    ): BatchResponse {
        var response = runWithBeaconReport("BatchSaveToDisk", shareAccessToken) { accessToken ->
            shared.batchSaveToDisk(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                shareAccessToken = shareAccessToken,
                items = items,
                userId = null
            ).data
        }
        if(queryTaskPolling){
            response = queryTaskPolling(response)
        }
        return response
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
            shared.queryTasks(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                taskIdList = taskIdList,
                userId = null
            ).data
        }
    }

    /**
     * 查询批量任务 单result
     *
     * @param taskIds 任务 id
     */
    suspend fun queryTasksSingleResult(
        taskIds: List<Long>
    ): List<BatchResponseSingleResult> {
        val taskIdList = taskIds.joinToString(",")
        return runWithBeaconReport("QueryTasksSingleResult", taskIds.getOrNull(0).toString()) { accessToken ->
            shared.queryTasksSingleResult(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                taskIdList = taskIdList,
                userId = null
            ).data
        }
    }

    /**
     * 跨空间复制目录
     *
     * @param copyFrom 被复制的源目录或相簿路径
     * @param copyFromSpaceId 被复制的源空间 SpaceId
     * @param dirPath 目标目录路径或相簿名，对于多级目录，使用斜杠(/)分隔，例如 foo/bar_new
     * @param conflictResolutionStrategy 最后一级目录冲突时的处理方式，ask: 冲突时返回 HTTP 409 Conflict 及 SameNameDirectoryOrFileExists 错误码，rename: 冲突时自动重命名最后一级目录，默认为 ask
     */
    suspend fun asyncCopyCrossSpace(
        copyFrom: String,
        copyFromSpaceId: String,
        dirPath: String,
        conflictResolutionStrategy: ConflictStrategy? = null,
    ): AsyncCopyCrossSpaceResult {
        return runWithBeaconReport("AsyncCopyCrossSpace", dirPath) { accessToken ->
            shared.asyncCopyCrossSpace(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                dirPath = dirPath,
                conflictResolutionStrategy = conflictResolutionStrategy,
                request = AsyncCopyCrossSpaceRequest(copyFrom, copyFromSpaceId),
                userId = null
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
            shared.addAuthorizeDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = dirPath,
                accessToken = accessToken,
                authorizeToContent = authorizeToContent
            ).checkSuccess() }
    }

    /**
     * 删除目录分配的授权
     *
     * @param dirPath 目录路径
     * @param authorizeToContent 授权信息
     */
    suspend fun deleteDirectoryAuthority(dirPath: String, authorizeToContent: AuthorizeToContent) {

        return runWithBeaconReport("DeleteDirectoryAuthority", dirPath) { accessToken ->
            shared.deleteAuthorityDirectory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                dirPath = dirPath,
                accessToken = accessToken,
                authorizeToContent = authorizeToContent
            ).checkSuccess() }
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
                shared.getAlbumCoverUrlInAlbum(
                    libraryId = libraryId,
                    spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                    albumName = albumName,
                    accessToken = accessToken,
                    userId = null,
                    size = size
                ).header("Location")
            } else {
                shared.getAlbumCoverUrl(
                    libraryId = libraryId,
                    spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                    size = size,
                    accessToken = accessToken,
                    userId = null
                ).header("Location")
            }
        }
    }

    /**
     * 查看历史版本列表
     *
     * @param dir 文件夹
     * @param page 文件页码
     * @param pageSize 一页的数据量
     * @param orderType 排序方式
     * @param orderDirection 排序方向
     * @return 历史版本列表
     */
    @JvmOverloads
    suspend fun listHistory(
        name: String,
        dir: Directory = rootDirectory,
        page: Int,
        pageSize: Int,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
    ): HistoryMediaContent {
        return runWithBeaconReport("ListHistory", dir.path) { accessToken ->
            val filePath = smhKey(dir.path, name)
            shared.listHistory(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken,
                userId = userSpace.userId,
                page = page,
                pageSize = pageSize,
                orderBy = orderType,
                orderByType = orderDirection
            ).data
        }
    }

    /**
     * 删除文件的历史版本
     *
     * @param historyIds 历史版本 id 号
     */
    suspend fun deleteHistoryMedia(historyIds: List<Long>) {

        runWithBeaconReport("DeleteHistoryMedias", historyIds.getOrNull(0).toString()) { accessToken ->
            shared.deleteHistoryMedia(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                historyIds = historyIds,
                userId = null
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
            shared.restoreHistoryMedia(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                historyId = historyId,
                userId = null
            ).data.also { mediaContent ->
                mediaContent.metaData = Utils.metaDataTrim(mediaContent.metaData)
            }
        }
    }

    /**
     * 查询是否开启了历史版本
     *
     */
    suspend fun getHistoryStatus(): HistoryStatus {

        return runWithBeaconReport("GetLibraryHistory", null) { accessToken ->
            shared.getHistoryStatus(
                libraryId = libraryId,
                accessToken = accessToken).data
        }
    }

    /**
     * 删除目录同步
     */
    suspend fun deleteDirectoryLocalSync(syncId: Int) {

        return runWithBeaconReport("DeleteDirectoryLocalSync", syncId.toString()) { accessToken ->
            shared.deleteDirectoryLocalSync(
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
            shared.putDirectoryLocalSync(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                body = PutDirectoryLocalSyncRequestBody(path, strategy, localPath),
                userId = user.userSpace.userId,
                accessToken = accessToken).data
        }
    }

    /**
     * 指定 URL 下载
     */
    @JvmOverloads
    suspend fun download(request: DownloadRequest, executor: Executor? = null): DownloadResult {
        return execute(request, DownloadResult(), executor?: TaskExecutors.DOWNLOAD_EXECUTOR)
    }

    /**
     * 用模板创建文件时如果文件已存在，则自动重命名
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param meta 文件元数据
     * @param dir 所在文件夹，默认是根目录下
     * @param request 指定模板名字
     * @return 文件信息
     */
    @JvmOverloads
    suspend fun createFileFromTemplate(
        name: String,
        dir: Directory = rootDirectory,
        meta: Map<String, String>? = null,
        request: CreateFileFromTemplateRequest
    ): MediaContent {
        val filePath = smhKey(dir.path, name)
        return runWithBeaconReport("CreateFileFromTemplate", filePath) { accessToken ->
            val metaData = meta?.mapKeys { "${X_SMH_META_KEY_PREFIX}${it.key}" }
            shared.createFileFromTemplate(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                filePath = filePath,
                accessToken = accessToken,
                metaData = metaData ?: emptyMap(),
                request = request,
                userId = null
            ).data
        }
    }

    /**
     * 用于文档编辑
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 所在文件夹，默认是根目录下
     * @return 返回请求文档编辑的url
     */
    @JvmOverloads
    suspend fun officeEditFile(name: String, dir: Directory = rootDirectory, lang: String = "zh-CN"): String {
        val filePath = smhKey(dir.path, name)
        return retryWhenTokenExpired() { accessToken ->
            "${baseUrl()}api/v1/office/${libraryId}/${userSpace.spaceId}/edit/${filePath.cosPathEncode()}?mobile"
                .query("access_token", accessToken).query("user_id", userSpace.userId).query("lang", lang)
        }
    }

    /**
     * 用于文档编辑校验
     */
    @JvmOverloads
    suspend fun officeEditFileCheck(name: String, dir: Directory = rootDirectory, lang: String = "zh-CN") {
        val filePath = smhKey(dir.path, name)
        return runWithBeaconReport("OfficeEditFileCheck", null) { accessToken ->
            shared.officeEditFileCheck(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                path = filePath,
                userId = userSpace.userId,
                accessToken = accessToken,
            ).checkSuccess() }
    }

    /**
     * 用于空间文件数量统计
     */
    @JvmOverloads
    suspend fun getSpaceFileCount(
    ): SpaceFileCount {
        return runWithBeaconReport("GetSpaceFileCount", null) { accessToken ->
            shared.getSpaceFileCount(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
            ).data
        }
    }

    /**
     * 查询最近使用的文件列表
     * @param marker 用于顺序列出分页的标识，可选参数，不传默认第一页
     * @param limit 用于顺序列出分页时本地列出的项目数限制，可选参数，不传则默认20
     * @param filterActionBy 筛选操作方式，可选，不传返回全部，preview 只返回预览操作，modify 返回编辑操作
     * @param type 筛选文件类型，可选参数，字符串数组，当前支持的类型包括：
     * all: 搜索所有文件，当不传 type 或传空时默认为 all；
     * document: 搜索所有文档，文档类型为：['pdf', 'powerpoint', 'excel', 'word' 'text']
     * pdf: 仅搜索 PDF 文档，对应的文件扩展名为 .pdf；
     * powerpoint: 仅搜索演示文稿，如 .ppt、.pptx、.pot、.potx 等；
     * excel: 仅搜索表格文件，如 .xls、.xlsx、.ett、.xltx、.csv 等；
     * word: 仅搜索文档，如 .doc、.docx、.dot、.wps、.wpt 等；
     * text: 仅搜索纯文本，如 .txt、.asp、.htm 等；
     * doc、xls 或 ppt: 仅搜索 Word、Excel 或 Powerpoint 类型文档，对应的文件扩展名为 .doc(x)、.xls(x) 或 .ppt(x)；
     * @param withPath 响应是否带文件路径，默认为 false
     */
    @JvmOverloads
    suspend fun recentlyUsedFile(
        marker: String? = null,
        limit: Int? = null,
        filterActionBy: String? = null,
        type: List<String>? = null,
        withPath: Boolean = false,
        withFavoriteStatus: Boolean = false,
    ): RecentlyUsedFileContents {
        return runWithBeaconReport("RecentlyUsedFile", null) { accessToken ->
            shared.recentlyUsedFile(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                request = RecentlyUsedFileRequest(marker, limit, filterActionBy, type,
                    withPath, withFavoriteStatus),
                withInode = 0
            ).data
        }
    }

    /**
     * 查询 inode 文件信息（返回路径）
     * @param inode 文件 ID
     */
    suspend fun getINodeInfo(
        inode: String
    ): INodeInfo {
        return runWithBeaconReport("GetINodeInfo", inode) { accessToken ->
            shared.getINodeInfo(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                inode = inode
            ).data
        }
    }

    /**
     * 更新目录自定义标签
     * @param dirPath 目录路径
     * @param updateDirectoryLabel 更新目录自定义标签的请求实体
     */
    suspend fun updateDirectoryLabels(dirPath: String, updateDirectoryLabel: UpdateDirectoryLabel)  {
        return runWithBeaconReport("UpdateDirectoryLabels", dirPath) { accessToken ->
            shared.updateDirectoryLabels(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                dirPath = dirPath,
                body = updateDirectoryLabel
            ).checkSuccess()
        }
    }

    /**
     * 更新文件的标签（Labels）或分类（Category）
     * @param filePath 文件路径
     * @param updateFileLabel 更新文件的标签（Labels）或分类（Category）的请求实体
     */
    suspend fun updateFileLabels(filePath: String, updateFileLabel: UpdateFileLabel)  {
        return runWithBeaconReport("UpdateFileLabels", filePath) { accessToken ->
            shared.updateFileLabels(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                filePath = filePath,
                body = updateFileLabel
            ).checkSuccess()
        }
    }

    /**
     * 获取回收站项目预览链接
     * 可用于预览文档、图片、视频等多种文件类型；
     * 预览文档类型的文件时，返回HTML或JPG格式的文档用于预览；
     * 预览视频文件时，返回视频的首帧图片作为视频封面预览；
     * 针对照片或视频封面，优先使用人脸识别智能缩放裁剪为 {Size}px × {Size}px 大小，如果未识别到人脸则居中缩放裁剪为 {Size}px × {Size}px 大小，如果未指定 {Size} 参数则使用照片或视频封面原图，最后 302 跳转到对应的图片的 URL；
     * 可以直接在使用图片的参数中指定该 URL，例如小程序 <image> 标签、 HTML <img> 标签或小程序 wx.previewImage 接口等，该接口将自动 302 跳转到真实的图片 URL；
     * 如果文件不属于可预览的文件类型，则会跳转至文件的下载链接；
     * @param recycledItemId 回收站 ID
     * @param type 文档类型文件的预览方式，可选参数，如果设置为"pic"则以JPG格式预览文档首页，否则以HTML格式预览文档
     * @param size 图片或视频封面的缩放大小
     * @param scale 图片或视频封面的等比例缩放百分比，可选参数，不传 Size 时生效
     * @param widthSize 图片或视频封面的缩放宽度，不传高度时，高度按等比例缩放，不传 Size 和 Scale 时生效；
     * @param heightSize 图片或视频封面的缩放高度，不传宽度时，宽度按等比例缩放，不传 Size 和 Scale 时生效；
     * @param frameNumber gif文件的帧数，针对 gif 的降帧处理，仅在预览gif类型文件时生效
     */
    @JvmOverloads
    suspend fun getRecycledPreviewUrl(
        recycledItemId: String,
        type: String? = null,
        size: Int? = null,
        scale: Int? = null,
        widthSize: Int? = null,
        heightSize: Int? = null,
        frameNumber: Int? = null,
    ): String {
        return retryWhenTokenExpired { accessToken ->
            "${baseUrl()}api/v1/recycled/${libraryId}/${userSpace.spaceId}/${recycledItemId}?preview&mobile"
                .query("type", type)
                .query("size", size)
                .query("scale", scale)
                .query("width_size", widthSize)
                .query("height_size", heightSize)
                .query("frame_number", frameNumber)
                .query("access_token", accessToken)
        }
    }

    /**
     * 用于查看回收站文件详情
     * @param recycledItemId 回收站项目 ID
     */
    suspend fun recycledItemInfo(
        recycledItemId: String
    ): RecycledItemInfo {
        return runWithBeaconReport("RecycledItemInfo", recycledItemId) { accessToken ->
            shared.recycledItemInfo(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                recycledItemId = recycledItemId
            ).data
        }
    }

    /**
     * 收藏文件目录
     * @param favoriteRequest 要收藏的项
     */
    suspend fun favorite(favoriteRequest: FavoriteRequest)  {
        return runWithBeaconReport("Favorite", null) { accessToken ->
            shared.favorite(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                body = favoriteRequest
            ).checkSuccess()
        }
    }

    /**
     * 取消收藏
     * @param favoriteRequest 要取消收藏的项
     */
    suspend fun unFavorite(favoriteRequest: FavoriteRequest)  {
        return runWithBeaconReport("UnFavorite", null) { accessToken ->
            shared.unFavorite(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                body = favoriteRequest
            ).checkSuccess()
        }
    }

    /**
     * 查看指定空间收藏列表
     * @param marker 用于顺序列出分页的标识，可选参数；
     * @param limit 用于顺序列出分页时本地列出的项目数限制，默认为20，可选参数；
     * @param page 分页码，默认第一页，可选参数，不能与 marker 和 limit 参数同时使用；
     * @param pageSize 分页大小，默认 20，可选参数，不能与 marker 和 limit 参数同时使用；
     * @param orderType 排序字段，按收藏时间排序为 favoriteTime（默认），目前仅支持按收藏时间排序，可选参数；
     * @param orderDirection 排序方式，升序为 asc，降序为 desc（默认），可选参数；
     * @param withPath 是否返回 path，返回为 true，不返回为 false（默认），可选参数；
     */
    @JvmOverloads
    suspend fun favoriteList(
        marker: String? = null,
        limit: Int? = null,
        page: Int? = null,
        pageSize: Int? = null,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
        withPath: Boolean? = null,
    ): FavoriteContents {
        return runWithBeaconReport("FavoriteList", null) { accessToken ->
            shared.favoriteList(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                marker = marker,
                limit = limit,
                page = page,
                pageSize = pageSize,
                orderBy = orderType,
                orderByType = orderDirection,
                withPath = withPath,
            ).data
        }
    }

    /**
     * 租户空间限速
     * @param spaceTrafficLimit 空间限速请求实体
     */
    suspend fun trafficLimit(spaceTrafficLimit: SpaceTrafficLimit)  {
        return runWithBeaconReport("TrafficLimit", null) { accessToken ->
            shared.trafficLimit(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                body = spaceTrafficLimit
            ).checkSuccess()
        }
    }

    /**
     * 用于列出空间首页内容，会忽略目录的层级关系，列出空间下所有文件
     * @param marker 用于顺序列出分页的标识，可选参数，翻页时传入此参数；
     * @param limit 用于顺序列出分页时本地列出的项目数限制，可选参数；
     * @param orderType 排序字段，按名称排序为 name（默认），按修改时间排序为 modificationTime，按文件大小排序为 size，按创建时间排序为 creationTime，按上传时间为uploadTime，按照文件对应的本地创建时间排序为 localCreationTime，按照文件对应的本地修改时间排序为 localModificationTime；
     * @param orderDirection 排序方式，升序为 asc（默认），降序为 desc；
     * @param filter 筛选方式，必选，onlyDir 只返回文件夹，onlyFile 只返回文件；
     * @param withPath 是否返回 path，返回为 true，不返回为 false（默认），可选参数；
     * @param userId 用户身份识别，当访问令牌对应的权限为管理员权限且申请访问令牌时的用户身份识别为空时用来临时指定用户身份，详情请参阅生成访问令牌接口，可选参数；
     * @param category 文件自定义的分类,string类型,最大长度16字节， 可选，用户可通过更新文件接口修改文件的分类，也可以根据文件后缀预定义文件的分类信息;
     */
    @JvmOverloads
    suspend fun contentsView(
        filter: DirectoryFilter,
        marker: String? = null,
        limit: Int? = null,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
        withPath: Boolean? = null,
        userId: String? = null,
        category: String? = null,
    ): ContentsView {
        return runWithBeaconReport("ContentsView", null) { accessToken ->
            shared.contentsView(
                libraryId = libraryId,
                spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                accessToken = accessToken,
                marker = marker,
                limit = limit,
                orderBy = orderType,
                orderByType = orderDirection,
                filter = filter,
                withPath = withPath,
                userId = userId,
                category = category,
            ).data
        }
    }


    @Throws
    fun <T1: SMHRequest, T2: SMHResult> buildHttpRequest(request: T1, result: T2): QCloudHttpRequest<T2> {

        val url = request.httpUrl
        val httpRequestBuilder = QCloudHttpRequest.Builder<T2>()
            .method(request.httpMethod)

        if (url != null) {
            httpRequestBuilder.url(URL(url))
        } else {
            httpRequestBuilder.scheme(getProtocol())
            httpRequestBuilder.host(request.httpHost)
            httpRequestBuilder.path(request.httpPath)
            httpRequestBuilder.query(request.httpQueries)
        }
        httpRequestBuilder.addHeaders(request.httpHeaders.mapValues {
            listOf(it.value)
        })

        // 增加 body 解析
        if (request is DownloadRequest && result is DownloadResult) {
            httpRequestBuilder.converter(DownloadResponseConverter { complete, target ->
                request.progressListener?.onProgressChange(request, complete, target)
            })
        }

        return httpRequestBuilder.build()
    }


    /**
     * 执行 SMHRequest 请求
     */
    @Throws
    suspend fun <T1: SMHRequest, T2: SMHResult> execute(request: T1, result: T2, executor: Executor): T2 {

        return executor.runWithSuspend {

            val httpRequest = buildHttpRequest(request, result)

            val httpTask = httpClient.resolveRequest(httpRequest)
            request.httpTask = httpTask
            val httpResult = httpTask.executeNow()

            httpResult.content()
        }
    }

    fun cancel(request: SMHRequest) {
        request.httpTask?.cancel()
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

    private fun smhKey(dir: String?, name: String): String {
        return if (dir.isNullOrEmpty()) {
            name
        } else if (dir.endsWith("/")) {
            "$dir$name"
        } else {
            "$dir/$name"
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

    private suspend fun queryTaskPolling(response: BatchResponse): BatchResponse{
        if(response.taskId == null) {
            return response
        }

        if(!hasRunning(response)) {
            return response
        }

        var result: BatchResponse = response
        var hasRunning = true
        while (hasRunning) {
            // 每3秒轮询一次
            delay(3000)
            val batchResponses: List<BatchResponse> = queryTasks(listOf(response.taskId))
            hasRunning = if(batchResponses.size != 1){
                false
            } else {
                result = batchResponses[0]
                hasRunning(result)
            }
        }
        return result
    }

    enum class TaskStatus {
        SUCCESS,
        FAILED,
        RUNNING
    }

    private fun hasRunning(response: BatchResponse): Boolean{
        if(response.result.isNullOrEmpty()) {
            return true
        }
        // 判断response.result每一项的status是否有RUNNING
        return response.result!!.any { getTaskStatus(it) == TaskStatus.RUNNING }
    }

    private fun getTaskStatus(batchResponseItem: BatchResponseItem): TaskStatus {
        return when(batchResponseItem.status) {
            202 -> TaskStatus.RUNNING
            200, 204 -> TaskStatus.SUCCESS
            // 207,500
            else -> TaskStatus.FAILED
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

 public fun smhKey(directory: String?, name: String): String {
     return if (TextUtils.isEmpty(directory)) name else "$directory/$name"
 }

     /**
      * 上报简单数据_SDK启动
      */
    private fun reportSdkStart() {
         val params: MutableMap<String, String> = HashMap()
         try {
             params["sdk_name"] = "smh"
             params["sdk_version_name"] = BuildConfig.SMH_VERSION_NAME
             params["sdk_version_code"] = BuildConfig.SMH_VERSION_CODE.toString()
             QCloudTrackService.getInstance()
                 .reportSimpleData(Constants.SIMPLE_DATA_EVENT_CODE_START, params)
         } catch (_: Exception) {  }
     }
