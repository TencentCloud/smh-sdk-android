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

import com.tencent.cloud.smh.api.adapter.CallResultAdapterFactory
import com.tencent.cloud.smh.api.adapter.SMHResponse
import com.tencent.cloud.smh.api.model.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*


/**
 * <p>
 * </p>
 */
interface SMHService {

    companion object {

        val shared: SMHService

        init {
//            val gson = GsonBuilder()
//                .registerTypeHierarchyAdapter(SMHResponse::class.java, ResponseSerializer())
//                .create()

            val logging = HttpLoggingInterceptor()
            logging.level = HttpLoggingInterceptor.Level.BODY

            val httpClient = OkHttpClient.Builder()
            httpClient.addInterceptor(logging)
            httpClient.followRedirects(false)

            val retrofit = Retrofit.Builder()
                .baseUrl("https://smh.tencentcs.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(CallResultAdapterFactory())
                .client(httpClient.build())
                .build()

            shared = retrofit.create(SMHService::class.java)
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
    ): SMHResponse<Unit>

    @PUT("api/v1/directory/{libraryId}/{spaceId}/{dirPath}")
    suspend fun renameDirectory(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("dirPath") dirPath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Body from: RenameDirectoryBody
    ): SMHResponse<Unit>

    @GET("api/v1/directory/{libraryId}/{spaceId}/{dirPath}")
    suspend fun listDirectory(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("dirPath") dirPath: String = "",
        @Query("marker") marker: Int? = null,
        @Query("limit") limit: String? = null,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<DirectoryContents>

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
        @Path("filePath", encoded = true) filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Query("force") force:Int = 0,
        @HeaderMap metaData: Map<String, String> = emptyMap(),
    ): SMHResponse<InitUpload>

    @POST("api/v1/file/{libraryId}/{spaceId}/{filePath}?multipart")
    suspend fun initMultipartUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath", encoded = true) filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Query("force") force:Int = 0,
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

    @POST("api/v1/file/{libraryId}/{spaceId}/{confirmKey}?confirm")
    suspend fun confirmUpload(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("confirmKey") confirmKey: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
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
        @Path("filePath", encoded = true) filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<Unit>

    @GET("api/v1/file/{libraryId}/{spaceId}/{filePath}?info")
    suspend fun getFileInfo(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath", encoded = true) filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
    ): SMHResponse<FileInfo>

    @PUT("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun createSymLink(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath", encoded = true) filePath: String,
        @Query("access_token") accessToken: String,
        @Query("user_id") userId: String? = null,
        @Query("force") force:Int = 0,
        @Body linkTo: SymLinkBody
    ): SMHResponse<ConfirmUpload>

    @DELETE("api/v1/file/{libraryId}/{spaceId}/{filePath}")
    suspend fun deleteFile(
        @Path("libraryId") libraryId: String,
        @Path("spaceId") spaceId: String = DEFAULT_SPACE_ID,
        @Path("filePath", encoded = true) filePath: String,
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
}