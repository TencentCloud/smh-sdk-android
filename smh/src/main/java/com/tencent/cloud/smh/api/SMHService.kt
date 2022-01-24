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

import android.util.Log
import com.tencent.cloud.smh.BuildConfig
import com.tencent.cloud.smh.api.SMHService.Companion.customHost
import com.tencent.cloud.smh.api.adapter.CallResultAdapterFactory
import com.tencent.cloud.smh.api.adapter.SMHResponse
import com.tencent.cloud.smh.api.model.*
import com.tencent.cloud.smh.interceptor.RetryInterceptor
import com.tencent.qcloud.core.logger.QCloudLogger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit


/**
 * <p>
 * </p>
 */
interface SMHService {


    companion object {

        val shared: SMHService
        val httpClient: OkHttpClient
        val retrofit: Retrofit

        const val defaultHost = "api.tencentsmh.cn"
        const val customHost: String = BuildConfig.CUSTOM_HOST


        init {

            val logging = HttpLoggingInterceptor()
            logging.level = HttpLoggingInterceptor.Level.HEADERS

            val httpClientBuilder = OkHttpClient.Builder()
                // .addInterceptor(EnvSwitcherInterceptor())
                .addInterceptor(RetryInterceptor())
                .addInterceptor(logging)
                .followRedirects(false)

            httpClient = httpClientBuilder.build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl())
                .addConverterFactory(GsonConverterFactory.create())
                .addConverterFactory(EnumConverterFactory())
                .addCallAdapterFactory(CallResultAdapterFactory())
                .client(httpClient)
                .build()

            shared = retrofit.create(SMHService::class.java)
        }

        fun host() = if (customHost.isNotEmpty()) {
            customHost
        } else {
            defaultHost
        }

        fun baseUrl() = "https://${host()}/"

        /**
         * 是否为正式环境
         */
        fun isReleaseHost() = host() == defaultHost
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

    @POST("api/v1/token")
    suspend fun refreshAccessToken(
        @Path("libraryId") libraryId: String,
        @Path("AccessToken") accessToken: String
    ): SMHResponse<AccessToken>

    @PUT("api/v1/directory/{libraryId}/{spaceId}/{dirPath}")
    suspend fun createDirectory(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("dirPath") dirPath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<CreateDirectoryResult>

    @PUT("api/v1/directory/{libraryId}/{spaceId}/{dirPath}")
    suspend fun renameDirectory(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("dirPath") dirPath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Query("move_authority") moveAuthority: Boolean = false,
        @Query("conflict_resolution_strategy") conflictStrategy: ConflictStrategy? = null,
        @Body from: RenameDirectoryBody
    ): SMHResponse<Unit>

    @PUT("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun renameFile(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath") filePath: String = "",
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Query("conflict_resolution_strategy") conflictStrategy: ConflictStrategy? = null,
        @Body from: RenameFileBody
    ): SMHResponse<RenameFileResponse>


    @HEAD("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun headFile(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<Unit>

    @GET("api/v1/directory/{libraryId}/{spaceId}/{dirPath}")
    suspend fun listDirectory(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("dirPath") dirPath: String = "",
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("orderBy") orderBy: OrderType? = null,
        @Query("orderByType") orderByType: OrderDirection? = null,
        @Query("filter") directoryFilter: DirectoryFilter? = null,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<DirectoryContents>


    @POST("api/v1/directory-history/{libraryId}/{spaceId}/delete")
    suspend fun deleteHistoryMedia(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Body historyIds: List<Long>,
    ): SMHResponse<Unit>

    @POST("api/v1/directory-history/{libraryId}/{spaceId}/latest-version/{historyId}")
    suspend fun restoreHistoryMedia(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("historyId") historyId: Long,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<MediaContent>

    @GET("api/v1/authority/{libraryId}/getRoleList")
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

    @POST("api/v1/search/{libraryId}/{spaceId}/space-contents")
    suspend fun initSearch(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Body initSearch: InitSearchMedia,
    ): SMHResponse<SearchPartContent>

    @GET("api/v1/search/{libraryId}/{spaceId}/{searchId}")
    suspend fun searchMore(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("searchId") searchId: String,
        @Query("marker") marker: Long,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<SearchPartContent>

    @DELETE("api/v1/search/{libraryId}/{spaceId}/{searchId}")
    suspend fun deleteSearch(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("searchId") searchId: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<Unit>

    @GET("api/v1/recycled/{libraryId}/{spaceId}")
    suspend fun listRecycled(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("orderBy") orderBy: OrderType? = null,
        @Query("orderByType") orderByType: OrderDirection? = null,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<RecycledContents>

    @DELETE("api/v1/directory/{libraryId}/{spaceId}/{dirPath}")
    suspend fun deleteDirectory(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("dirPath") dirPath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<Unit>

    @PUT("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun initUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Query("conflict_resolution_strategy") conflictStrategy: ConflictStrategy?,
        @HeaderMap metaData: Map<String, String> = emptyMap(),
    ): SMHResponse<InitUpload>

    @POST("api/v1/file/{libraryId}/{spaceId}/{filePath}?multipart")
    suspend fun initMultipartUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Query("conflict_resolution_strategy") conflictStrategy: ConflictStrategy?,
        @HeaderMap metaData: Map<String, String> = emptyMap(),
    ): SMHResponse<InitUpload>

    @GET("api/v1/file/{libraryId}/{spaceId}/{confirmKey}?upload")
    suspend fun listMultipartUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("confirmKey") confirmKey: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<MultiUploadMetadata>

    // 文件缩略图
    @GET("api/v1/file/{libraryId}/{spaceId}/{filePath}?preview")
    suspend fun getThumbnail(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath") filePath: String,
        @Query("size") size: Int? = null,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<ThumbnailResult>

    @POST("api/v1/file/{libraryId}/{spaceId}/{confirmKey}?confirm")
    suspend fun confirmUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("confirmKey") confirmKey: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Body confirmUploadRequestBody: ConfirmUploadRequestBody,
    ): SMHResponse<ConfirmUpload>

    @DELETE("api/v1/file/{libraryId}/{spaceId}/{confirmKey}?upload")
    suspend fun cancelUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("confirmKey") confirmKey: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<Unit>

    @GET("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun initDownload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<Unit>

    @GET("api/v1/file/{libraryId}/{spaceId}/{filePath}?info")
    suspend fun getFileInfo(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath") filePath: String,
        @Query("history_id") historyId: Long?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<FileInfo>

    @GET("api/v1/directory/{libraryId}/{spaceId}/{dirPath}?info")
    suspend fun getDirectoryInfo(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("dirPath") dirPath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<DirectoryInfo>

    @PUT("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun createSymLink(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath") filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Query("force") force:Int = 0,
        @Body linkTo: SymLinkBody
    ): SMHResponse<ConfirmUpload>

    @DELETE("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun deleteFile(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath") filePath: String,
        @Query("permanent") permanent: Boolean?,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<DeleteMediaResult>

    @DELETE("api/v1/recycled/{libraryId}/{spaceId}/{itemId}")
    suspend fun deleteRecycledItem(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("itemId") itemId: Long,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<Unit>

    @POST("api/v1/recycled/{libraryId}/{spaceId}?delete")
    suspend fun deleteRecycledItems(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Body itemIds: List<Long>
    ): SMHResponse<Unit>

    @POST("api/v1/recycled/{libraryId}/{spaceId}/{itemId}?restore")
    suspend fun restoreRecycledItem(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("itemId") itemId: Long,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<RestorePath>

    @POST("api/v1/recycled/{libraryId}/{spaceId}?restore")
    suspend fun restoreRecycledItems(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Body itemIds: List<Long>
    ): SMHResponse<BatchResponse>

    @POST("api/v1/batch/{libraryId}/{spaceId}?delete")
    suspend fun batchDelete(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Body items: List<BatchDeleteItem>
    ): SMHResponse<BatchResponse>

    @POST("api/v1/batch/{libraryId}/{spaceId}?copy")
    suspend fun batchCopy(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Body items: List<BatchCopyItem>
    ): SMHResponse<BatchResponse>

    @POST("api/v1/batch/{libraryId}/{spaceId}?move")
    suspend fun batchMove(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Body items: List<BatchMoveItem>
    ): SMHResponse<BatchResponse>

    @POST("api/v1/batch/{libraryId}/{spaceId}?copy")
    suspend fun batchSaveToDisk(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Query("share_access_token") shareAccessToken: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Body items: List<BatchSaveToDiskItem>
    ): SMHResponse<BatchResponse>

    @GET("api/v1/task/{libraryId}/{spaceId}/{taskIdList}")
    suspend fun queryTasks(
        @Path("taskIdList") taskIdList: String,
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,

    ): SMHResponse<List<BatchResponse>> //

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
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<Unit>


    @GET("api/v1/album/{libraryId}/{spaceId}/cover")
    suspend fun getAlbumCoverUrl(
            @Path("libraryId") libraryId: String,
            @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
            @Query("size") size: String? = null,
            @Query("access_token") accessToken: String,
            @Query("user_id") userId: String? = null,
    ): SMHResponse<Unit>

    @GET("api/v1/album/{libraryId}/{spaceId}/cover/{albumName}")
    suspend fun getAlbumCoverUrlInAlbum(
            @Path("libraryId") libraryId: String,
            @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
            @Path("albumName") albumName: String,
            @Query("size") size: String? = null,
            @Query("access_token") accessToken: String,
            @Query("user_id") userId: String? = null,
    ): SMHResponse<Unit>

    @GET("api/v1/quota/{libraryId}/{spaceId}")
    suspend fun getQuotaCapacity(
            @Path("libraryId") libraryId: String,
            @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
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
            @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
            @Query("access_token") accessToken: String,
            @Query("user_id") userId: String? = null,
    ): SMHResponse<SpaceSize>

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

