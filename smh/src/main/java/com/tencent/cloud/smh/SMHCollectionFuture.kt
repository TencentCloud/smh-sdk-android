package com.tencent.cloud.smh

import android.net.Uri
import com.tencent.cloud.smh.api.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext

/**
 * <p>
 *     SMH 资源库封装类，提供适合 Java8 CompletableFuture 风格的 API
 * </p>
 * Created by wjielai on 4/20/21.
 * Copyright 2010-2020 Tencent Cloud. All Rights Reserved.
 */
class SMHCollectionFuture internal constructor(
    private val smh: SMHCollection,
    private val context: CoroutineContext,
    private val scope: CoroutineScope
) {

    private val rootDirectory = smh.rootDirectory

    /**
     * 列出根文件夹列表
     *
     * @return 文件夹列表
     */
    fun listDirectory(): CompletableFuture<List<Directory>> = call { smh.listDirectory() }

    /**
     * 创建文件夹
     *
     * @param dir 文件夹
     * @return 执行结果，true 表示创建成功，false 表示创建失败
     */
    fun createDirectory(dir: Directory): CompletableFuture<Boolean> =
        call { smh.createDirectory(dir) }

    /**
     * 重命名文件夹
     *
     * @param target 目标文件夹
     * @param source 源文件夹
     * @return 执行结果，true 表示重命名成功，false 表示重命名失败
     */
    fun renameDirectory(target: Directory, source: Directory): CompletableFuture<Boolean> =
        call { smh.renameDirectory(target, source) }

    /**
     * 删除文件夹
     *
     * @param dir 文件夹
     * @return 执行结果，true 表示成功，false 表示失败
     */
    fun deleteDirectory(dir: Directory): CompletableFuture<Boolean> =
        call { smh.deleteDirectory(dir) }

    /**
     * 列出文件列表
     *
     * @param dir 文件夹
     * @param paging 是否分页，默认是分页列出
     * @return 文件列表
     */
    @JvmOverloads
    fun list(
        dir: Directory = rootDirectory,
        paging: Boolean = true
    ): CompletableFuture<DirectoryContents> = call { smh.list(dir, paging) }

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
     * 获取文件信息
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 所在文件夹，默认是根目录下
     * @return 文件信息
     */
    @JvmOverloads
    fun getFileInfo(
        name: String,
        dir: Directory = rootDirectory,
    ): CompletableFuture<FileInfo?> = call { smh.getFileInfo(name, dir) }

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
    fun initUpload(
        name: String,
        meta: Map<String, String>? = null,
        overrideOnNameConflict: Boolean = false,
        dir: Directory = rootDirectory
    ): CompletableFuture<InitUpload> =
        call { smh.initUpload(name, meta, dir, overrideOnNameConflict) }

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
     * @param meta 文件元数据
     * @param dir 所在文件夹，默认是根目录下
     * @param overrideOnNameConflict 存在重名文件时是覆盖还是重命名，true 表示覆盖，false 表示重命名
     * @return 上传信息
     */
    @JvmOverloads
    fun initMultipartUpload(
        name: String,
        meta: Map<String, String>? = null,
        overrideOnNameConflict: Boolean = false,
        dir: Directory = rootDirectory
    ): CompletableFuture<InitUpload> =
        call { smh.initMultipartUpload(name, meta, dir, overrideOnNameConflict) }

    /**
     * 列出分片上传任务信息
     *
     * @param confirmKey 上传任务的 confirmKey
     * @return 上传信息
     */
    fun listMultipartUpload(confirmKey: String): CompletableFuture<MultiUploadMetadata> =
        call { smh.listMultipartUpload(confirmKey) }

    /**
     * 分片上传
     *
     * @param metadata 文件元数据
     * @param uri 本地文件 URI
     * @param size 本地文件大小
     * @return 上传成功返回的 eTag
     */
    fun multipartUpload(
        metadata: MultiUploadMetadata,
        uri: Uri,
        size: Long
    ): CompletableFuture<String?> =
        call { smh.multipartUpload(metadata, uri, size) }

    /**
     * 简单上传
     *
     * @param uploader 上传信息
     * @param uri 本地文件 URI
     * @return 上传成功返回的 eTag
     */
    fun upload(uploader: InitUpload, uri: Uri): CompletableFuture<String?> =
        call { smh.upload(uploader, uri) }

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
    fun confirmUpload(confirmKey: String): CompletableFuture<ConfirmUpload> = call {
        smh.confirmUpload(confirmKey)
    }

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
    fun initDownload(name: String): CompletableFuture<InitDownload> = call {
        smh.initDownload(name)
    }

    /**
     * 下载文件
     *
     * @param url 文件下载地址
     * @param contentUri 本地文件 URI
     * @param offset 请求文件偏移
     */
    @JvmOverloads
    fun download(url: String, contentUri: Uri, offset: Long = 0L) = call {
        smh.download(url, contentUri, offset)
    }

    /**
     * 删除文件
     *
     * @param name 文件名，如果没有设置文件夹，则为文件路径
     * @param dir 文件所在文件夹，默认为根目录
     * @return 执行结果
     */
    fun delete(name: String): CompletableFuture<Boolean> = call {
        smh.delete(name)
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
    fun createSymLink(
        name: String,
        dir: Directory = rootDirectory,
        sourceFileName: String,
        overrideOnNameConflict: Boolean = false
    ): CompletableFuture<ConfirmUpload> =
        call { smh.createSymLink(name, dir, sourceFileName, overrideOnNameConflict) }

    private inline fun <T> call(
        crossinline action: suspend () -> T
    ): CompletableFuture<T> {
        return scope.future(context) {
            action()
        }
    }
}
