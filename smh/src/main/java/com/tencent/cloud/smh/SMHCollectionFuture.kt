package com.tencent.cloud.smh

import android.net.Uri
import com.tencent.cloud.smh.api.model.*
import com.tencent.cloud.smh.api.retrofit.SMHResponse
import com.tencent.cloud.smh.transfer.*
import com.tencent.cos.xml.listener.CosXmlProgressListener
import com.tencent.qcloud.core.logger.QCloudLogger
import com.tencent.qcloud.core.task.TaskExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import java.io.InputStream
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

/**
 * <p>
 *     SMH 资源库封装类，提供适合 Java8 CompletableFuture 风格的 API
 * </p>
 */
class SMHCollectionFuture internal constructor(
    private val smh: SMHCollection,
    private val context: CoroutineContext,
    private val scope: CoroutineScope
) {

    private val rootDirectory = smh.rootDirectory


    /**
     * 查询服务是否可用
     *
     */
    fun checkCloudServiceEnableState() = call { smh.checkCloudServiceEnableState() }

    /**
     * 获取剩余可用配额空间
     *
     * @return 可用配额空间，单位是 Byte
     */
    fun getSpaceQuotaRemainSize(): CompletableFuture<BigDecimal?> =
        call { smh.getSpaceQuotaRemainSize() }

    /**
     * 获取用户空间状态
     */
    fun getUserSpaceState(): CompletableFuture<UserSpaceState> =
        call { smh.getUserSpaceState() }

    /**
     * 列出所有的文件列表
     */
    @JvmOverloads
    fun listAll(
        dir: Directory,
        pageSize: Int = 100,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
        directoryFilter: DirectoryFilter? = null,
    ): CompletableFuture<DirectoryContents> = call { smh.listAll(dir, pageSize, orderType, orderDirection, directoryFilter) }

    /**
     * 通过 marker + limit 的方式列出所有的文件列表
     */
    @JvmOverloads
    fun listAllWithMarker(
        dir: Directory,
        limit: Int = 100,
    ): CompletableFuture<DirectoryContents> = call { smh.listAllWithMarker(dir, limit) }

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
    fun list(
        dir: Directory = rootDirectory,
        page: Int,
        pageSize: Int,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
        directoryFilter: DirectoryFilter? = null,
    ): CompletableFuture<DirectoryContents> = call { smh.list(dir, page, pageSize, orderType, orderDirection, directoryFilter) }

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
    fun listWithMarker(
        dir: Directory,
        marker: String? = null,
        limit: Int? = null,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
        directoryFilter: DirectoryFilter? = null,
        eTag: String? = null,
    ): CompletableFuture<DirectoryContents> = call { smh.listWithMarker(dir, marker, limit, orderType, orderDirection, directoryFilter, eTag) }

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
    fun listWithOffset(
        dir: Directory,
        offset: Long,
        limit: Int,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
        directoryFilter: DirectoryFilter? = null,
    ): CompletableFuture<DirectoryContents> = call { smh.listWithOffset(dir, offset, limit, orderType, orderDirection, directoryFilter) }

    /**
     * 获取角色列表，不同的角色对应不同的权限
     */
    fun getRoleList(): CompletableFuture<List<Role>> = call { smh.getRoleList() }

    /**
     * 获取下载链接
     *
     * @param path 文件路径
     * @param historyId 历史版本号
     * @param encode 文件路径是否已经 url 编码
     */
    @JvmOverloads
    fun getDownloadAccessUrl(path: String, historyId: Long? = null, encode: Boolean = false): CompletableFuture<String>
        = call { smh.getDownloadAccessUrl(path, historyId, encode) }

    /**
     * 获取文档预览链接
     * @param filePath 文件路径
     * @param historyId 历史版本号
     */
    @JvmOverloads
    fun getPreviewAccessUrl(
        filePath: String,
        historyId: Long? = null,
        purpose: Purpose? = null,
        lang: String = "zh-CN"
    ): CompletableFuture<String>
        = call { smh.getPreviewAccessUrl(filePath, historyId, purpose, lang) }

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
    fun getThumbnailAccessUrl(
        name: String,
        dir: Directory = rootDirectory,
        historyId: Long? = null,
        size: Int? = null,
        scale: Int? = null,
        widthSize: Int? = null,
        heightSize: Int? = null,
        frameNumber: Int? = null,
        purpose: Purpose? = null
    ): CompletableFuture<String>
         = call { smh.getThumbnailAccessUrl(name, dir, historyId, size, scale, widthSize, heightSize, frameNumber, purpose) }

    /**
     * 获取我共享的文件夹
     *
     * @param page 文件页码
     * @param pageSize 一页的数据量
     * @param orderType 排序方式
     * @param orderDirection 排序方向
     */
    @JvmOverloads
    fun getMyAuthorizedDirectory(page: Int, pageSize: Int, orderType: OrderType? = null,
                                         orderDirection: OrderDirection? = null): CompletableFuture<AuthorizedContent>
        = call { smh.getMyAuthorizedDirectory(page, pageSize, orderType, orderDirection) }

    @JvmOverloads
    fun getMyAuthorizedDirectoryWithMarker(
        marker: String?, limit: Int, orderType: OrderType?, orderDirection: OrderDirection?, eTag: String?
    ): CompletableFuture<AuthorizedContent>
        = call { smh.getMyAuthorizedDirectoryWithMarker(marker, limit, orderType, orderDirection, eTag) }

    /**
     * 初始化搜索，可能会返回一定量的搜索结果
     *
     * @param keyword 搜索关键字
     * @param scope 搜索范围
     * @param searchTypes 搜索的文件类型
     */
    @JvmOverloads
    fun initSearch(
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
    ): CompletableFuture<SearchPartContent>
        = call {
        smh.initSearch(
            searchTypes, keyword, scope,
            tags, extname, creators,
            minFileSize, maxFileSize, modificationTimeStart, modificationTimeEnd,
            orderBy, orderByType)
        }

    /**
     * 查询搜索状态
     *
     * @param searchId 搜索的 id 号
     * @param marker 分页标记
     */
    fun searchMore(searchId: String, marker: Long): CompletableFuture<SearchPartContent>
        = call { smh.searchMore(searchId, marker) }

    /**
     * 完成搜索
     *
     * @param searchId 搜索的 id 号
     */
    fun deleteSearch(searchId: String) = call { smh.deleteSearch(searchId) }

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
    fun listRecycled(
        page: Int,
        pageSize: Int,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
    ): CompletableFuture<RecycledContents> = call { smh.listRecycled(page, pageSize, orderType, orderDirection) }

    @JvmOverloads
    fun listRecycledWithMarker(
        marker: String?,
        limit: Int? = null,
        orderType: OrderType? = null,
        orderDirection: OrderDirection? = null,
        eTag: String? = null
    ): CompletableFuture<RecycledContents> = call { smh.listRecycledWithMarker(marker, limit, orderType, orderDirection, eTag) }

    /**
     * 创建文件夹
     *
     * @param dir 文件夹
     * @return 执行结果，true 表示创建成功，false 表示创建失败
     */
    fun createDirectory(dir: Directory): CompletableFuture<CreateDirectoryResult> =
        call { smh.createDirectory(dir) }

    /**
     * 重命名文件夹
     *
     * @param target 目标文件夹
     * @param source 源文件夹
     * @param conflictStrategy 冲突处理方式
     * @return 执行结果，true 表示重命名成功，false 表示重命名失败
     */
    @JvmOverloads
    fun renameDirectory(target: Directory, source: Directory, conflictStrategy: ConflictStrategy? = null): CompletableFuture<RenameFileResponse?> =
        call { smh.renameDirectory(target, source, conflictStrategy) }

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
    fun renameFile(targetName: String, targetDir: Directory = rootDirectory,
                   sourceName: String, sourceDir: Directory = rootDirectory,
                   conflictStrategy: ConflictStrategy?): CompletableFuture<RenameFileResponse?> =
        call { smh.renameFile(targetName, targetDir, sourceName, sourceDir, conflictStrategy) }

    /**
     * 检查文件状态
     *
     * @param name 文件名称
     * @param dir 文件所在的文件夹
     */
    fun headFile( name: String,
                  dir: Directory = rootDirectory): CompletableFuture<HeadFileContent> =
        call { smh.headFile(name, dir) }

    /**
     * 删除文件夹
     *
     * @param dir 文件夹
     * @return 执行结果，true 表示成功，false 表示失败
     */
    fun deleteDirectory(dir: Directory): CompletableFuture<Boolean> =
        call { smh.deleteDirectory(dir) }

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
    fun getFileInfo(
        name: String,
        dir: Directory = rootDirectory,
        historyId: Long? = null,
        purpose: Purpose? = null
    ): CompletableFuture<FileInfo?> = call { smh.getFileInfo(name, dir, historyId, purpose) }

    /**
     * 获取文件夹信息
     *
     * @param dir 所在文件夹，默认是根目录下
     */
    @JvmOverloads
    fun getDirectoryInfo(
        dir: Directory = rootDirectory,
    ): CompletableFuture<DirectoryInfo> = call { smh.getDirectoryInfo(dir) }

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
    fun getThumbnail(name: String,
                     dir: Directory = rootDirectory,
                     size: Int? = null,
                     scale: Int? = null,
                     widthSize: Int? = null,
                     heightSize: Int? = null,
                     frameNumber: Int? = null,
                     purpose: Purpose? = null
    ): CompletableFuture<ThumbnailResult> = call { smh.getThumbnail(name, dir, size, scale, widthSize, heightSize, frameNumber, purpose) }

    /**
     * 快速上传
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 所在文件夹，默认是根目录下
     * @param uri 本地文件 URI
     * @param conflictStrategy 冲突处理方式
     * @return 上传信息 http状态码为202代表文件头hash命中
     */
    @JvmOverloads
    fun quickUpload(
        name: String,
        dir: Directory = rootDirectory,
        quickUpload: QuickUpload,
        conflictStrategy: ConflictStrategy? = null,
        meta: Map<String, String>? = null
    ): CompletableFuture<RawResponse> =
        call { smh.quickUpload(name, dir, quickUpload, conflictStrategy, meta) }

    /**
     * 初始化简单上传
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param meta 文件元数据
     * @param dir 所在文件夹，默认是根目录下
     * @param conflictStrategy 存在重名文件时是覆盖还是重命名，true 表示覆盖，false 表示重命名
     * @param filesize 上传文件大小，单位为字节（Byte），用于判断剩余空间是否足够
     * @return 上传信息
     */
    @JvmOverloads
    fun initUpload(
        name: String,
        meta: Map<String, String>? = null,
        conflictStrategy: ConflictStrategy? = null,
        filesize: Long? = null,
        dir: Directory = rootDirectory
    ): CompletableFuture<InitUpload> =
        call { smh.initUpload(name, meta, dir, conflictStrategy, filesize) }

    /**
     * 初始化分块上传。一般整个上传的代码如下：
     * ```
     * CompletableFuture<ConfirmUpload> cf = smh.initMultipartUpload(
     *      "myFileName", metadata, isOverride, directory
     * ).thenCompose(initUpload -> {
     *      // 列出已上传的分片，适用于续传场景
     *      return smh.listMultipartUpload(initUpload.confirmKey);
     * }).thenCompose(multiUploadMetadata -> smh.multipartUpload(multiUploadMetadata,
     *      localUri, fileSize).thenApply(etag -> new String[]{multiUploadMetadata.confirmKey, etag})
     * ).thenCompose(uploadInfo -> smh.confirmUpload(uploadInfo[0]));
     * ```
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
    fun initMultipartUpload(
        name: String,
        partNumberRange: String,
        meta: Map<String, String>? = null,
        conflictStrategy: ConflictStrategy? = null,
        filesize: Long? = null,
        dir: Directory = rootDirectory
    ): CompletableFuture<InitMultipartUpload> =
        call { smh.initMultipartUpload(name, partNumberRange, meta, dir, conflictStrategy, filesize) }

    /**
     * 用于分块上传任务续期
     *
     * @param confirmKey 上传任务的 confirmKey
     * @param partNumberRange 格式为逗号分隔的 区段，区段 可以是 单个数字 n，也可以是 由两个数字组成的范围 n-m
     * @return 上传信息
     */
    fun renewMultipartUpload(confirmKey: String, partNumberRange: String): CompletableFuture<InitMultipartUpload> =
        call { smh.renewMultipartUpload(confirmKey, partNumberRange) }

    /**
     * 列出分片上传任务信息
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 上传信息
     */
    fun listMultipartUpload(confirmKey: String): CompletableFuture<MultiUploadMetadata> =
        call { smh.listMultipartUpload(confirmKey) }

    /**
     * 取消上传
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 执行结果
     */
    fun cancelUpload(confirmKey: String): CompletableFuture<Boolean> = call {
        smh.cancelUpload(confirmKey)
    }

    /**
     * 完成上传
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 上传结果
     */
    fun confirmUpload(confirmKey: String, crc64: String): CompletableFuture<ConfirmUpload> = call {
        smh.confirmUpload(confirmKey, crc64)
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
        stateListener: SMHStateListener? = null,
        progressListener: SMHProgressListener? = null,
        resultListener: SMHResultListener? = null,
        initMultipleUploadListener: SMHInitMultipleUploadListener? = null,
        quickUpload: Boolean = true
    ): CompletableFuture<SMHUploadTask> =
        call { smh.upload(name, dir, uri, inputStream, confirmKey, conflictStrategy, meta, stateListener, progressListener, resultListener, initMultipleUploadListener, quickUpload) }

    /**
     * 初始化下载。一般整个下载的代码如下：
     *
     *```
     *   Uri contentUri = ...;
     *   CompletableFuture<Uri> cf = smh.initDownload(content.name).thenCompose(downloadInfo -> {
     *      // 执行下载
     *      return smh.download(downloadInfo.url, contentUri).thenApply(Void -> contentUri);
     *   });
     *```
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 文件所在文件夹，默认为根目录
     * @return 下载信息
     */
    fun initDownload(name: String, dir: Directory = rootDirectory): CompletableFuture<InitDownload> = call {
        QCloudLogger.i("Test", "initDownload")
        val result = smh.initDownload(name, dir)
        QCloudLogger.i("Test", "after initDownload")
        result
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
    ): CompletableFuture<SMHDownloadTask> = call {
        smh.download(name, dir, historyId, localFullPath, rangeStart, rangeEnd, stateListener, progressListener, resultListener)
    }

    /**
     * 指定 URL 下载
     */
    @JvmOverloads
    fun download(request: DownloadRequest, executor: Executor? = null): CompletableFuture<DownloadResult> = call {
        QCloudLogger.i("Test", "download")
        val result = smh.download(request, executor)
        QCloudLogger.i("Test", "after download")
        result
    }

    /**
     * 删除文件
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 文件所在文件夹，默认为根目录
     * @param permanent 是否永久删除，为 true 表示不放到回收站中
     * @return 执行结果 如果未开通回收站，或者 permanent 为true 表示
     */
    fun delete(name: String,
               dir: Directory = rootDirectory,
               permanent: Boolean = false): CompletableFuture<DeleteMediaResult?> = call {
        smh.delete(name, dir, permanent)
    }

    /**
     * 删除回收站文件
     *
     * @param itemId 回收站文件 id
     */
    fun deleteRecycledItem(
        itemId: Long
    ): CompletableFuture<Boolean> = call { smh.deleteRecycledItem(itemId) }

    /**
     * 批量删除回收站文件
     *
     * @param itemIds 回收站文件 id
     */
    fun deleteRecycledItems(
        itemIds: List<Long>
    ): CompletableFuture<Boolean> = call { smh.deleteRecycledItems(itemIds) }

    /**
     * 将文件从回收站中恢复
     *
     * @param itemId 回收站文件 id
     */
    fun restoreRecycledItem(
        itemId: Long
    ): CompletableFuture<String?> = call { smh.restoreRecycledItem(itemId) }

    /**
     * 将文件批量从回收站中恢复
     *
     * @param itemIds 回收站文件 id
     */
    fun restoreRecycledItems(
        itemIds: List<Long>
    ): CompletableFuture<BatchResponse> = call { smh.restoreRecycledItems(itemIds) }

    /**
     * 清空回收站
     */
    fun clearRecycledItem(): CompletableFuture<Boolean> = call { smh.clearRecycledItem() }

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
    fun createSymLink(
        name: String,
        dir: Directory = rootDirectory,
        sourceFileName: String,
        overrideOnNameConflict: Boolean = false
    ): CompletableFuture<ConfirmUpload> =
        call { smh.createSymLink(name, dir, sourceFileName, overrideOnNameConflict) }

    /**
     * 批量删除
     *
     * @param items 需要批量删除的文件或文件夹
     */
    fun batchDelete(
        items: List<BatchDeleteItem>
    ): CompletableFuture<BatchResponse> = call { smh.batchDelete(items) }

    /**
     * 批量复制
     *
     * @param items 需要批量复制的文件或文件夹
     */
    fun batchCopy(
        items: List<BatchCopyItem>,
    ): CompletableFuture<BatchResponse> = call { smh.batchCopy(items) }

    /**
     * 批量移动
     *
     * @param items 需要批量移动的文件或文件夹
     */
    fun batchMove(
        items: List<BatchMoveItem>,
    ): CompletableFuture<BatchResponse> = call { smh.batchMove(items) }

    /**
     * 批量保存至网盘
     *
     * @param shareAccessToken accessToken
     * @param items 需要批量保存至网盘的文件或文件夹
     */
    fun batchSaveToDisk(
        shareAccessToken: String,
        items: List<BatchSaveToDiskItem>,
    ): CompletableFuture<BatchResponse> = call { smh.batchSaveToDisk(shareAccessToken, items) }

    /**
     * 跨空间复制目录
     *
     * @param copyFrom 被复制的源目录或相簿路径
     * @param copyFromSpaceId 被复制的源空间 SpaceId
     * @param dirPath 目标目录路径或相簿名，对于多级目录，使用斜杠(/)分隔，例如 foo/bar_new
     * @param conflictResolutionStrategy 最后一级目录冲突时的处理方式，ask: 冲突时返回 HTTP 409 Conflict 及 SameNameDirectoryOrFileExists 错误码，rename: 冲突时自动重命名最后一级目录，默认为 ask
     */
    fun asyncCopyCrossSpace(
        copyFrom: String,
        copyFromSpaceId: String,
        dirPath: String,
        conflictResolutionStrategy: ConflictStrategy? = null,
    ): CompletableFuture<AsyncCopyCrossSpaceResult> = call { smh.asyncCopyCrossSpace(copyFrom, copyFromSpaceId, dirPath, conflictResolutionStrategy) }

    /**
     * 查询批量任务
     *
     * @param taskIds 任务 id
     */
    fun queryTasks(
        taskIds: List<Long>
    ): CompletableFuture<List<BatchResponse>> = call { smh.queryTasks(taskIds) }

    /**
     * 查询批量任务 单result
     *
     * @param taskIds 任务 id
     */
    fun queryTasksSingleResult(
        taskIds: List<Long>
    ): CompletableFuture<List<BatchResponseSingleResult>> = call { smh.queryTasksSingleResult(taskIds) }

    /**
     * 给目录分配权限
     *
     * @param dirPath 目录路径
     * @param authorizeToContent 授权信息
     */
    fun addAuthorityDirectory(dirPath: String, authorizeToContent: AuthorizeToContent)
         = call { smh.addAuthorityDirectory(dirPath, authorizeToContent) }

    /**
     * 删除目录分配的授权
     *
     * @param dirPath 目录路径
     * @param authorizeToContent 授权信息
     */
    fun deleteDirectoryAuthority(dirPath: String, authorizeToContent: AuthorizeToContent)
        = call { smh.deleteDirectoryAuthority(dirPath, authorizeToContent) }

    /**
     * 获取相簿封面链接
     *
     * @param albumName 相簿名，分相簿媒体库必须指定该参数，不分相簿媒体库不能指定该参数
     * @param size 图片缩放大小
     * @return 封面图片下载链接
     */
    fun getAlbumCoverUrl(
        albumName: String? = null,
        size: String? = null
    ): CompletableFuture<String?> = call { smh.getAlbumCoverUrl(albumName, size) }

    /**
     * 删除文件的历史版本
     *
     * @param historyIds 历史版本 id 号
     */
    fun deleteHistoryMedia(historyIds: List<Long>)
        = call { smh.deleteHistoryMedia(historyIds) }

    /**
     * 将指定的历史版本恢复为最新版本
     *
     * @param historyId 历史版本 id 号
     */
    fun restoreHistoryMedia(historyId: Long): CompletableFuture<MediaContent>
        = call { smh.restoreHistoryMedia(historyId) }

    /**
     * 查询是否开启了历史版本
     *
     */
    fun getHistoryStatus(): CompletableFuture<HistoryStatus>
        = call { smh.getHistoryStatus() }

    /**
     * 删除目录同步
     */
    fun deleteDirectoryLocalSync(syncId: Int) = call { smh.deleteDirectoryLocalSync(syncId) }

    /**
     * 设置目录为同步盘
     */
    fun putDirectoryLocalSync(path: String, strategy: DirectoryLocalSyncStrategy, localPath: String): CompletableFuture<PutDirectoryLocalSyncResponseBody>
        = call { smh.putDirectoryLocalSync(path, strategy, localPath) }

    /**
     * 用模板创建文件时如果文件已存在，则自动重命名
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param meta 文件元数据
     * @param dir 所在文件夹，默认是根目录下
     * @param request 指定模板名字
     * @return 文件信息
     */
    fun createFileFromTemplate(name: String,
                               dir: Directory = rootDirectory,
                               meta: Map<String, String>? = null,
                               request: CreateFileFromTemplateRequest): CompletableFuture<MediaContent>
            = call { smh.createFileFromTemplate(name, dir, meta, request) }

    /**
     * 用于文档编辑
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 所在文件夹，默认是根目录下
     * @return 返回请求文档编辑的url
     */
    fun officeEditFile(
        name: String,
        dir: Directory = rootDirectory,
        lang: String = "zh-CN"
    ): CompletableFuture<String>
            = call { smh.officeEditFile(name, dir, lang) }

    /**
     * 用于空间文件数量统计
     */
    fun getSpaceFileCount(
    ): CompletableFuture<SpaceFileCount>
            = call { smh.getSpaceFileCount() }

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
    fun recentlyUsedFile(
        marker: String? = null,
        limit: Int? = null,
        filterActionBy: String? = null,
        type: List<String>? = null,
        withPath: Boolean = false
    ): CompletableFuture<RecentlyUsedFileContents>
            = call { smh.recentlyUsedFile(marker, limit, filterActionBy, type, withPath) }

    /**
     * 查询 inode 文件信息（返回路径）
     * @param inode 文件 ID
     */
    fun getINodeInfo(
        inode: String
    ): CompletableFuture<INodeInfo>
            = call { smh.getINodeInfo(inode) }

    private inline fun <T> call(
        crossinline action: suspend () -> T
    ): CompletableFuture<T> {
        return scope.future(context) {
            action()
        }
    }

}
