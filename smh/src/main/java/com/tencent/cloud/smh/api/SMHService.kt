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

package com.tencent.cloud.smh.api

import com.tencent.cloud.smh.BuildConfig
import com.tencent.cloud.smh.X_SMH_META_KEY_PREFIX
import com.tencent.cloud.smh.api.model.AccessToken
import com.tencent.cloud.smh.api.model.AsyncCopyCrossSpaceRequest
import com.tencent.cloud.smh.api.model.AsyncCopyCrossSpaceResult
import com.tencent.cloud.smh.api.model.AuthorizeToContent
import com.tencent.cloud.smh.api.model.AuthorizedContent
import com.tencent.cloud.smh.api.model.BatchCopyItem
import com.tencent.cloud.smh.api.model.BatchDeleteItem
import com.tencent.cloud.smh.api.model.BatchMoveItem
import com.tencent.cloud.smh.api.model.BatchResponse
import com.tencent.cloud.smh.api.model.BatchResponseSingleResult
import com.tencent.cloud.smh.api.model.BatchSaveToDiskItem
import com.tencent.cloud.smh.api.model.ConfirmUpload
import com.tencent.cloud.smh.api.model.ConfirmUploadRequestBody
import com.tencent.cloud.smh.api.model.ConflictStrategy
import com.tencent.cloud.smh.api.model.ContentsView
import com.tencent.cloud.smh.api.model.CreateDirectoryResult
import com.tencent.cloud.smh.api.model.CreateFileFromTemplateRequest
import com.tencent.cloud.smh.api.model.DEFAULT_SPACE_ID
import com.tencent.cloud.smh.api.model.DeleteMediaResult
import com.tencent.cloud.smh.api.model.DirectoryContents
import com.tencent.cloud.smh.api.model.DirectoryFilter
import com.tencent.cloud.smh.api.model.DirectoryInfo
import com.tencent.cloud.smh.api.model.FavoriteContents
import com.tencent.cloud.smh.api.model.FavoriteRequest
import com.tencent.cloud.smh.api.model.FavoriteResult
import com.tencent.cloud.smh.api.model.FileInfo
import com.tencent.cloud.smh.api.model.HistoryMediaContent
import com.tencent.cloud.smh.api.model.HistoryStatus
import com.tencent.cloud.smh.api.model.INodeInfo
import com.tencent.cloud.smh.api.model.InitMultipartUpload
import com.tencent.cloud.smh.api.model.InitSearchMedia
import com.tencent.cloud.smh.api.model.InitUpload
import com.tencent.cloud.smh.api.model.MediaContent
import com.tencent.cloud.smh.api.model.MultiUploadMetadata
import com.tencent.cloud.smh.api.model.OrderDirection
import com.tencent.cloud.smh.api.model.OrderType
import com.tencent.cloud.smh.api.model.PartNumberRange
import com.tencent.cloud.smh.api.model.PublicMultiUploadMetadata
import com.tencent.cloud.smh.api.model.Purpose
import com.tencent.cloud.smh.api.model.PutDirectoryLocalSyncRequestBody
import com.tencent.cloud.smh.api.model.PutDirectoryLocalSyncResponseBody
import com.tencent.cloud.smh.api.model.UploadRequestBody
import com.tencent.cloud.smh.api.model.QuotaBody
import com.tencent.cloud.smh.api.model.QuotaCapacity
import com.tencent.cloud.smh.api.model.QuotaResponse
import com.tencent.cloud.smh.api.model.RawResponse
import com.tencent.cloud.smh.api.model.RecentlyUsedFileContents
import com.tencent.cloud.smh.api.model.RecentlyUsedFileRequest
import com.tencent.cloud.smh.api.model.RecycledContents
import com.tencent.cloud.smh.api.model.RecycledItemInfo
import com.tencent.cloud.smh.api.model.RenameDirectoryBody
import com.tencent.cloud.smh.api.model.RenameFileBody
import com.tencent.cloud.smh.api.model.RenameFileResponse
import com.tencent.cloud.smh.api.model.RestorePath
import com.tencent.cloud.smh.api.model.Role
import com.tencent.cloud.smh.api.model.SearchPartContent
import com.tencent.cloud.smh.api.model.SpaceFileCount
import com.tencent.cloud.smh.api.model.SpaceSize
import com.tencent.cloud.smh.api.model.SpaceTrafficLimit
import com.tencent.cloud.smh.api.model.SymLinkBody
import com.tencent.cloud.smh.api.model.ThumbnailResult
import com.tencent.cloud.smh.api.model.UpdateDirectoryLabel
import com.tencent.cloud.smh.api.model.UpdateFileLabel
import com.tencent.cloud.smh.api.retrofit.CallResultAdapterFactory
import com.tencent.cloud.smh.api.retrofit.SMHResponse
import com.tencent.cloud.smh.api.retrofit.converter.EnumConverterFactory
import com.tencent.cloud.smh.api.retrofit.converter.GsonConverter
import com.tencent.cloud.smh.api.retrofit.converter.RawConverterFactory
import com.tencent.cloud.smh.ext.formatToUtc
import com.tencent.cloud.smh.interceptor.InetAddressInterceptor
import com.tencent.cloud.smh.interceptor.RetryInterceptor
import com.tencent.cos.xml.common.VersionInfo
import com.tencent.qcloud.core.http.CallMetricsListener
import com.tencent.qcloud.core.http.HttpConstants
import com.tencent.qcloud.core.http.HttpLogger
import com.tencent.qcloud.core.http.HttpLoggingInterceptor
import com.tencent.qcloud.core.http.QCloudHttpClient
import com.tencent.qcloud.core.logger.QCloudLogger
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Date
import java.util.concurrent.TimeUnit


/**
 * <p>
 * </p>
 */
interface SMHService {
    companion object {
        val httpClient: OkHttpClient
        private val mEventListenerFactory =
            EventListener.Factory { call -> CallMetricsListener(call) }
        var dnsFetch: QCloudHttpClient.QCloudDnsFetch? = null
        private val dns: Dns = Dns { hostname->
            var dns: List<InetAddress> = listOf()
            // 使用自定义Dns获取器
            dnsFetch?.let {
                try {
                    dns = it.fetch(hostname)
                } catch (ignored: UnknownHostException) {
                }
            }
            // 然后使用系统的 dns
            if (dns.isEmpty()) {
                try {
                    dns = Dns.SYSTEM.lookup(hostname)
                } catch (e: UnknownHostException) {
                    QCloudLogger.w(QCloudHttpClient.HTTP_LOG_TAG, "system dns failed.")
                }
            }
            dns
        }

        var userAgent: String = ""

        init {
            VersionInfo.sdkName = "smh"
            VersionInfo.versionName = BuildConfig.SMH_VERSION_NAME
            VersionInfo.versionCode = BuildConfig.SMH_VERSION_CODE
            userAgent = "app:null/null-sdk:${VersionInfo.getUserAgent()}"

            // 是否打印由QCloudLogger控制
            val httpLogger = HttpLogger(true, "SMHService")
            val logInterceptor = HttpLoggingInterceptor(httpLogger)
            logInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            val httpClientBuilder = OkHttpClient.Builder()
                .eventListenerFactory(mEventListenerFactory)
                .dns(dns)
                .addInterceptor(Interceptor { chain ->
                    val originalRequest = chain.request()
                    val newRequest = originalRequest.newBuilder()
                        .header(HttpConstants.Header.USER_AGENT, userAgent)
                        .build()
                    chain.proceed(newRequest)
                })
                .addInterceptor(RetryInterceptor())
                .addInterceptor(logInterceptor)
                .addNetworkInterceptor(InetAddressInterceptor())
                .followRedirects(false)
                .readTimeout(20, TimeUnit.SECONDS)
            httpClient = httpClientBuilder.build()
        }

        fun shared(baseUrl: String): SMHService {
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(RawConverterFactory())
                .addConverterFactory(GsonConverter.buildGsonConverterFactory())
                .addConverterFactory(EnumConverterFactory())
                .addCallAdapterFactory(CallResultAdapterFactory())
                .client(httpClient)
                .build()
            return retrofit.create(SMHService::class.java)
        }
    }

    @GET("api/v1/token")
    suspend fun getAccessToken(
        @Query("library_id") libraryId: String,
        @Query("library_secret") librarySecret: String,
        @Query("space_id") spaceId: String? = null,
        @Query("client_id") clientId: String? = null,
        @Query("user_id") userId: String? = null,
        @Query("period") period: Int = 86400,
        @Query("grant") grant: String? = null,
    ): SMHResponse<AccessToken>

    @POST("api/v1/token/{libraryId}/{AccessToken}")
    suspend fun refreshAccessToken(
        @Path("libraryId") libraryId: String,
        @Path("AccessToken") accessToken: String
    ): SMHResponse<AccessToken>

    @PUT("api/v1/directory/{libraryId}/{spaceId}/{dirPath}")
    suspend fun createDirectory(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("dirPath") dirPath: String,
        @Query("conflict_resolution_strategy") conflictStrategy: ConflictStrategy?,
        @Query("with_inode") withInode: Int?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<CreateDirectoryResult?>

    @PUT("api/v1/directory/{libraryId}/{spaceId}/{dirPath}")
    suspend fun renameDirectory(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("dirPath") dirPath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Query("move_authority") moveAuthority: Boolean,
        @Query("conflict_resolution_strategy") conflictStrategy: ConflictStrategy?,
        @Body from: RenameDirectoryBody
    ): SMHResponse<RenameFileResponse?>

    @PUT("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun renameFile(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Query("conflict_resolution_strategy") conflictStrategy: ConflictStrategy?,
        @Body from: RenameFileBody
    ): SMHResponse<RenameFileResponse>


    @HEAD("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun headFile(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<Void>

    /**
     * 通过 page + page_size 的方式来查询分页
     *
     * 支持排序
     */
    @GET("api/v1/directory/{libraryId}/{spaceId}/{dirPath}")
    suspend fun listDirectoryByPageSize(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("dirPath") dirPath: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("orderBy") orderBy: OrderType?,
        @Query("orderByType") orderByType: OrderDirection?,
        @Query("filter") directoryFilter: DirectoryFilter?,
        @Query("sort_type") sortType: String?,
        @Query("with_inode") withInode: Int?,
        @Query("with_favorite_status") withFavoriteStatus: Int?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<DirectoryContents>

    /**
     * 通过 offset + limit 的方式来查询目录
     *
     * 支持排序
     */
    @GET("api/v1/directory/{libraryId}/{spaceId}/{dirPath}")
    suspend fun listDirectoryByOffsetLimit(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("dirPath") dirPath: String,
        @Query("offset") offset: Long,
        @Query("limit") limit: Int,
        @Query("orderBy") orderBy: OrderType?,
        @Query("orderByType") orderByType: OrderDirection?,
        @Query("filter") directoryFilter: DirectoryFilter?,
        @Query("sort_type") sortType: String?,
        @Query("with_inode") withInode: Int?,
        @Query("with_favorite_status") withFavoriteStatus: Int?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<DirectoryContents>

    /**
     * 通过 marker + limit 的方式来查询目录
     *
     * 不支持排序
     */
    @GET("api/v1/directory/{libraryId}/{spaceId}/{dirPath}")
    suspend fun listDirectoryByMarkerLimit(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("dirPath") dirPath: String,
        @Header("If-None-Match") eTag: String?,
        @Query("marker") marker: String?,
        @Query("limit") limit: Int?,
        @Query("orderBy") orderBy: OrderType?,
        @Query("orderByType") orderByType: OrderDirection?,
        @Query("filter") directoryFilter: DirectoryFilter?,
        @Query("sort_type") sortType: String?,
        @Query("with_inode") withInode: Int?,
        @Query("with_favorite_status") withFavoriteStatus: Int?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<DirectoryContents>

    @GET("api/v1/directory-history/{libraryId}/{spaceId}/history-list/{filePath}")
    suspend fun listHistory(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("orderBy") orderBy: OrderType?,
        @Query("orderByType") orderByType: OrderDirection?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<HistoryMediaContent>

    @POST("api/v1/directory-history/{libraryId}/{spaceId}/delete")
    suspend fun deleteHistoryMedia(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Body historyIds: List<Long>,
    ): SMHResponse<Unit>

    @POST("api/v1/directory-history/{libraryId}/{spaceId}/latest-version/{historyId}")
    suspend fun restoreHistoryMedia(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("historyId") historyId: Long,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<MediaContent>

    @GET("api/v1/authority/{libraryId}/role-list")
    suspend fun getRoleList(
        @Path("libraryId") libraryId: String,
        @Query("access_token") accessToken: String,
    ): SMHResponse<List<Role>>

    @GET("api/v1/authority/{libraryId}/authorized-directory")
    suspend fun getMyAuthorizedDirectory(
        @Path("libraryId") libraryId: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("order_by") orderBy: OrderType?,
        @Query("order_by_type") orderByType: OrderDirection?,
        @Query("access_token") accessToken: String,
    ): SMHResponse<AuthorizedContent>

    @GET("api/v1/authority/{libraryId}/authorized-directory")
    suspend fun getMyAuthorizedDirectoryWithMarker(
        @Path("libraryId") libraryId: String,
        @Header("If-None-Match") eTag: String?,
        @Query("marker") marker: String?,
        @Query("limit") limit: Int?,
        @Query("orderBy") orderBy: OrderType?,
        @Query("orderByType") orderByType: OrderDirection?,
        @Query("access_token") accessToken: String,
    ): SMHResponse<AuthorizedContent>

    @POST("api/v1/search/{libraryId}/{spaceId}/space-contents")
    suspend fun initSearch(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Query("with_inode") withInode: Int?,
        @Query("with_favorite_status") withFavoriteStatus: Int?,
        @Body initSearch: InitSearchMedia,
    ): SMHResponse<SearchPartContent>

    @GET("api/v1/search/{libraryId}/{spaceId}/{searchId}")
    suspend fun searchMore(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("searchId") searchId: String,
        @Query("marker") marker: Long,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Query("with_inode") withInode: Int?,
        @Query("with_favorite_status") withFavoriteStatus: Int?,
    ): SMHResponse<SearchPartContent>

    @DELETE("api/v1/search/{libraryId}/{spaceId}/{searchId}")
    suspend fun deleteSearch(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("searchId") searchId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<Unit>

    @GET("api/v1/recycled/{libraryId}/{spaceId}")
    suspend fun listRecycled(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("orderBy") orderBy: OrderType?,
        @Query("orderByType") orderByType: OrderDirection?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<RecycledContents>

    @GET("api/v1/recycled/{libraryId}/{spaceId}")
    suspend fun listRecycledWithMarker(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Header("If-None-Match") eTag: String?,
        @Query("marker") marker: String?,
        @Query("limit") limit: Int?,
        @Query("orderBy") orderBy: OrderType?,
        @Query("orderByType") orderByType: OrderDirection?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<RecycledContents>

    @GET("api/v1/recycled/{libraryId}/{spaceId}/{recycledItemId}?info")
    suspend fun recycledItemInfo(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("recycledItemId") recycledItemId: String,
        @Query("access_token") accessToken: String,
    ): SMHResponse<RecycledItemInfo>

    @DELETE("api/v1/directory/{libraryId}/{spaceId}/{dirPath}")
    suspend fun deleteDirectory(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("dirPath") dirPath: String,
        @Query("permanent") permanent: Int?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<DeleteMediaResult?>

    @PUT("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun quickUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Query("conflict_resolution_strategy") conflictStrategy: ConflictStrategy?,
        @Query("filesize") filesize: Long?,
        @Body uploadRequestBody: UploadRequestBody,
        @HeaderMap metaData: Map<String, String>,
    ): SMHResponse<RawResponse>

    @PUT("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun initUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Query("conflict_resolution_strategy") conflictStrategy: ConflictStrategy?,
        @Query("filesize") filesize: Long?,
        @Body uploadRequestBody: UploadRequestBody?,
        @HeaderMap metaData: Map<String, String>,
    ): SMHResponse<InitUpload>

    @POST("api/v1/file/{libraryId}/{spaceId}/{filePath}?multipart")
    suspend fun publicInitMultipartUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Query("conflict_resolution_strategy") conflictStrategy: ConflictStrategy?,
        @Query("filesize") filesize: Long?,
        @Body uploadRequestBody: UploadRequestBody?,
        @HeaderMap metaData: Map<String, String>,
    ): SMHResponse<InitUpload>

    @GET("api/v1/file/{libraryId}/{spaceId}/{confirmKey}?upload")
    suspend fun publicListMultipartUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("confirmKey") confirmKey: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<PublicMultiUploadMetadata>

    @POST("api/v1/file/{libraryId}/{spaceId}/{confirmKey}?renew")
    suspend fun publicRenewMultipartUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("confirmKey") confirmKey: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<InitUpload>

    @POST("api/v1/file/{libraryId}/{spaceId}/{filePath}?multipart")
    suspend fun initMultipartUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Query("conflict_resolution_strategy") conflictStrategy: ConflictStrategy?,
        @Query("filesize") filesize: Long?,
        @HeaderMap metaData: Map<String, String>,
        @Body partNumberRange: PartNumberRange
    ): SMHResponse<InitMultipartUpload>

    @GET("api/v1/file/{libraryId}/{spaceId}/{confirmKey}?upload&no_upload_part_info=1")
    suspend fun listMultipartUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("confirmKey") confirmKey: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<MultiUploadMetadata>

    @POST("api/v1/file/{libraryId}/{spaceId}/{confirmKey}?renew")
    suspend fun renewMultipartUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("confirmKey") confirmKey: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Body partNumberRange: PartNumberRange
    ): SMHResponse<InitMultipartUpload>

    // 文件缩略图
    @GET("api/v1/file/{libraryId}/{spaceId}/{filePath}?preview&mobile")
    suspend fun getThumbnail(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("size") size: Int?,
        @Query("scale") scale: Int?,
        @Query("width_size") widthSize: Int?,
        @Query("height_size") heightSize: Int?,
        @Query("frame_number") frameNumber: Int?,
        @Query("purpose") purpose: Purpose?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<ThumbnailResult>

    @POST("api/v1/file/{libraryId}/{spaceId}/{confirmKey}?confirm")
    suspend fun confirmUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("confirmKey") confirmKey: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Body confirmUploadRequestBody: ConfirmUploadRequestBody?,
    ): SMHResponse<ConfirmUpload>

    @DELETE("api/v1/file/{libraryId}/{spaceId}/{confirmKey}?upload")
    suspend fun cancelUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("confirmKey") confirmKey: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<Unit>

    @GET("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun initDownload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<Unit>

    @GET("api/v1/file/{libraryId}/{spaceId}/{filePath}?info")
    suspend fun getFileInfo(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("history_id") historyId: Long?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Query("purpose") purpose: Purpose?,
        @Query("traffic_limit") trafficLimit: Long?
    ): SMHResponse<FileInfo>

    @GET("api/v1/directory/{libraryId}/{spaceId}/{dirPath}?info")
    suspend fun getDirectoryInfo(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("dirPath") dirPath: String,
        @Query("with_inode") withInode: Int?,
        @Query("with_favorite_status") withFavoriteStatus: Int?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<DirectoryInfo>

    @PUT("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun createSymLink(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Query("force") force:Int,
        @Body linkTo: SymLinkBody
    ): SMHResponse<ConfirmUpload>

    @DELETE("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun deleteFile(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("permanent") permanent: Int?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<DeleteMediaResult?>

    @DELETE("api/v1/recycled/{libraryId}/{spaceId}/{itemId}")
    suspend fun deleteRecycledItem(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("itemId") itemId: Long,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<Unit>

    @POST("api/v1/recycled/{libraryId}/{spaceId}?delete")
    suspend fun deleteRecycledItems(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Body itemIds: List<Long>
    ): SMHResponse<Unit>

    @POST("api/v1/recycled/{libraryId}/{spaceId}/{itemId}?restore")
    suspend fun restoreRecycledItem(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("itemId") itemId: Long,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<RestorePath>

    @POST("api/v1/recycled/{libraryId}/{spaceId}?restore")
    suspend fun restoreRecycledItems(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Body itemIds: List<Long>
    ): SMHResponse<BatchResponse>

    @POST("api/v1/batch/{libraryId}/{spaceId}?delete")
    suspend fun batchDelete(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Body items: List<BatchDeleteItem>
    ): SMHResponse<BatchResponse>

    @POST("api/v1/batch/{libraryId}/{spaceId}?copy")
    suspend fun batchCopy(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Body items: List<BatchCopyItem>
    ): SMHResponse<BatchResponse>

    @POST("api/v1/batch/{libraryId}/{spaceId}?move")
    suspend fun batchMove(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Body items: List<BatchMoveItem>
    ): SMHResponse<BatchResponse>

    @POST("api/v1/batch/{libraryId}/{spaceId}?copy")
    suspend fun batchSaveToDisk(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("share_access_token") shareAccessToken: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Body items: List<BatchSaveToDiskItem>
    ): SMHResponse<BatchResponse>

    @PUT("api/v1/cross-space/directory/{libraryId}/{spaceId}/copy/{dirPath}")
    suspend fun asyncCopyCrossSpace(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("dirPath") dirPath: String,
        @Query("conflict_resolution_strategy") conflictResolutionStrategy: ConflictStrategy?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Body request: AsyncCopyCrossSpaceRequest
    ): SMHResponse<AsyncCopyCrossSpaceResult>

    @GET("api/v1/task/{libraryId}/{spaceId}/{taskIdList}")
    suspend fun queryTasks(
        @Path("taskIdList") taskIdList: String,
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<List<BatchResponse>>

    @GET("api/v1/task/{libraryId}/{spaceId}/{taskIdList}")
    suspend fun queryTasksSingleResult(
        @Path("taskIdList") taskIdList: String,
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<List<BatchResponseSingleResult>>

    @POST("api/v1/authority/{libraryId}/{spaceId}/authorize/{dirPath}")
    suspend fun addAuthorizeDirectory(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("dirPath") dirPath: String,
        @Query("access_token") accessToken: String,
        @Body authorizeToContent: AuthorizeToContent,

        ): SMHResponse<Unit>

    @POST("api/v1/authority/{libraryId}/{spaceId}/authorize/{dirPath}?delete")
    suspend fun deleteAuthorityDirectory(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("dirPath") dirPath: String,
        @Query("access_token") accessToken: String,
        @Body authorizeToContent: AuthorizeToContent,

        ): SMHResponse<Unit>



    @DELETE("api/v1/recycled/{libraryId}/{spaceId}")
    suspend fun clearRecycled(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
    ): SMHResponse<Unit>


    @GET("api/v1/album/{libraryId}/{spaceId}/cover")
    suspend fun getAlbumCoverUrl(
            @Path("libraryId") libraryId: String,
            @Path("spaceId") spaceId: String,
            @Query("size") size: String?,
            @Query("access_token") accessToken: String,
            @Query("user_id") userId: String?,
    ): SMHResponse<Unit>

    @GET("api/v1/album/{libraryId}/{spaceId}/cover/{albumName}")
    suspend fun getAlbumCoverUrlInAlbum(
            @Path("libraryId") libraryId: String,
            @Path("spaceId") spaceId: String,
            @Path("albumName") albumName: String,
            @Query("size") size: String?,
            @Query("access_token") accessToken: String,
            @Query("user_id") userId: String?,
    ): SMHResponse<Unit>

    @GET("api/v1/quota/{libraryId}/{spaceId}")
    suspend fun getQuotaCapacity(
            @Path("libraryId") libraryId: String,
            @Path("spaceId") spaceId: String,
            @Query("access_token") accessToken: String,
    ): SMHResponse<QuotaCapacity>

    @POST("api/v1/quota/{libraryId}/")
    suspend fun createQuota(
            @Path("libraryId") libraryId: String,
            @Query("access_token") accessToken: String,
            @Body quotaBody: QuotaBody,
    ): SMHResponse<QuotaResponse>

    @GET("api/v1/space/{libraryId}/{spaceId}/size")
    suspend fun getSpaceSize(
            @Path("libraryId") libraryId: String,
            @Path("spaceId") spaceId: String,
            @Query("access_token") accessToken: String,
            @Query("user_id") userId: String?,
    ): SMHResponse<SpaceSize>

    @GET("api/v1/space/{libraryId}/{spaceId}/file-count")
    suspend fun getSpaceFileCount(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String
    ): SMHResponse<SpaceFileCount>

    @GET("api/v1/office/{libraryId}/{spaceId}/edit/{path}?preflight&mobile")
    suspend fun officeEditFileCheck(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("path") path: String,
        @Query("user_id") userId: String?,
        @Query("access_token") accessToken: String
    ): SMHResponse<Unit>

    @GET("api/v1/directory-history/{libraryId}/library-history")
    suspend fun getHistoryStatus(
        @Path("libraryId") libraryId: String,
        @Query("access_token") accessToken: String,
    ): SMHResponse<HistoryStatus>

    @PUT("api/v1/directory-local-sync/{libraryId}/{spaceId}")
    suspend fun putDirectoryLocalSync(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Body body: PutDirectoryLocalSyncRequestBody
    ): SMHResponse<PutDirectoryLocalSyncResponseBody>

    @DELETE("api/v1/directory-local-sync/{libraryId}/{spaceId}/{syncId}")
    suspend fun deleteDirectoryLocalSync(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("syncId") syncId: Int,
        @Query("access_token") accessToken: String,
    ): SMHResponse<Unit>

    @PUT("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun createFileFromTemplate(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String?,
        @Body request: CreateFileFromTemplateRequest,
        @HeaderMap metaData: Map<String, String>,
    ): SMHResponse<MediaContent>

    @POST("/api/v1/recent/{libraryId}/{spaceId}/recently-used-file")
    suspend fun recentlyUsedFile(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("with_inode") withInode: Int?,
        @Query("access_token") accessToken: String,
        @Body request: RecentlyUsedFileRequest,
    ): SMHResponse<RecentlyUsedFileContents>

    @GET("/api/v1/inode/{libraryId}/{spaceId}/{inode}")
    suspend fun getINodeInfo(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("inode") inode: String,
        @Query("access_token") accessToken: String,
    ): SMHResponse<INodeInfo>

    @POST("api/v1/directory/{libraryId}/{spaceId}/{dirPath}?update")
    suspend fun updateDirectoryLabels(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("dirPath") dirPath: String,
        @Query("access_token") accessToken: String,
        @Body body: UpdateDirectoryLabel,
    ): SMHResponse<Unit>

    @POST("api/v1/directory/{libraryId}/{spaceId}/{filePath}?update")
    suspend fun updateFileLabels(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Body body: UpdateFileLabel,
    ): SMHResponse<Unit>

    @POST("api/v1/favorite/{libraryId}/{spaceId}")
    suspend fun favorite(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Body body: FavoriteRequest,
    ): SMHResponse<FavoriteResult>

    @POST("api/v1/favorite/{libraryId}/{spaceId}/?cancel")
    suspend fun unFavorite(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Body body: FavoriteRequest,
    ): SMHResponse<Unit>

    @GET("api/v1/favorite/{libraryId}/{spaceId}/list")
    suspend fun favoriteList(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("marker") marker: String?,
        @Query("limit") limit: Int?,
        @Query("page") page: Int?,
        @Query("page_size") pageSize: Int?,
        @Query("order_by") orderBy: OrderType?,
        @Query("order_by_type") orderByType: OrderDirection?,
        @Query("with_path") withPath: Boolean?,
        @Query("access_token") accessToken: String,
    ): SMHResponse<FavoriteContents>

    @POST("api/v1/space/{libraryId}/{spaceId}/traffic-limit")
    suspend fun trafficLimit(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Body body: SpaceTrafficLimit,
    ): SMHResponse<Unit>

    @GET("/api/v1/space/{libraryId}/{spaceId}/contents-view")
    suspend fun contentsView(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String,
        @Query("access_token") accessToken: String,
        @Query("marker") marker: String?,
        @Query("limit") limit: Int?,
        @Query("order_by") orderBy: OrderType?,
        @Query("order_by_type") orderByType: OrderDirection?,
        @Query("filter") filter: DirectoryFilter?,
        @Query("with_path") withPath: Boolean?,
        @Query("user_id") userId: String?,
        @Query("category") category: String?
    ): SMHResponse<ContentsView>
}


//class EnvSwitcherInterceptor: Interceptor {
//
//    override fun intercept(chain: Interceptor.Chain): Response {
//        val originalRequest = chain.request()
//        return if (customHost && originalRequest.url().host().contentEquals("smh.tencentcs.com")) {
//            val devPath = "${SMHService.envPathPrefix}${originalRequest.url().encodedPath()}"
//            val rwRequest = originalRequest.newBuilder()
//                .url(originalRequest.url().newBuilder().encodedPath(devPath).build())
//                .build()
//            chain.proceed(rwRequest)
//        } else {
//            chain.proceed(originalRequest)
//        }
//    }
//}

