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

import android.app.Activity
import com.tencent.cloud.smh.api.SMHResult
import com.tencent.cloud.smh.api.SMHService
import com.tencent.cloud.smh.api.adapter.data
import com.tencent.cloud.smh.api.model.*
import java.lang.Exception

/**
 * SMH 用户
 *
 */
interface SMHUser {

    /**
     * 获取 Library ID
     */
    val libraryId: String

    /**
     * 获取用户空间
     */
    val userSpace: UserSpace

    /**
     * 获取用户空间状态
     *
     * @return 空间状态
     */
    suspend fun getSpaceState(): SMHResult<UserSpaceState>

    /**
     * 获取访问凭证
     *
     * @return 访问凭证
     */
    suspend fun provideAccessToken(): AccessToken

    /**
     * 查询登录状态
     *
     * @return true 表示登录成功，false 表示失败
     */
    suspend fun isLogin(): Boolean

    /**
     * 是否曾经登录过
     */
    fun usedToHaveLogin(): Boolean

    /**
     * 发起用户登录
     *
     * @param activity 当前 Activity
     * @return 登录状态
     */
    suspend fun login(activity: Activity): SMHResult<Unit>
}

/**
 * SMH 用户抽象类，可以扩展它，实现最基础的功能
 *
 */
abstract class SMHSimpleUser : SMHUser {

    override fun usedToHaveLogin(): Boolean = false

    override suspend fun isLogin(): Boolean = false

    override suspend fun login(activity: Activity): SMHResult<Unit> {
        return SMHResult.Failure(UnsupportedOperationException())
    }

    override suspend fun getSpaceState(): SMHResult<UserSpaceState> {
        // 从 SMH Server 获取配额信息

        val accessToken = provideAccessToken()

        try {
            val capacity = SMHService.shared.getQuotaCapacity(
                    libraryId = libraryId,
                    spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                    accessToken = accessToken.token
            ).data.capacity

            val size = SMHService.shared.getSpaceSize(
                    libraryId = libraryId,
                    spaceId = userSpace.spaceId ?: DEFAULT_SPACE_ID,
                    accessToken = accessToken.token
            ).data.size

            return SMHResult.Success(UserSpaceState(capacity = capacity, size = size))
        } catch (e: Exception) {
            e.printStackTrace()
            return SMHResult.Failure(e)
        }
    }
}

/**
 * 静态用户，使用固定密钥初始化
 *
 * @param libraryId
 * @param librarySecret
 */
class StaticUser(libraryId: String, librarySecret: String) : SMHSimpleUser() {

    private var library = Library(libraryId, librarySecret)

    override val libraryId: String
        get() = library.libraryId

    override val userSpace: UserSpace
        get() = EmptyUseSpace

    private var validAccessToken: AccessToken? = null

    override suspend fun provideAccessToken(): AccessToken {
        if (library.LibrarySecret.isEmpty() || libraryId.isEmpty()) {
            throw SMHNoUserException
        }

        val et = validAccessToken
        if (et != null) {
            return if (et.isValid()) {
                et
            } else {
                validAccessToken = SMHService.shared.refreshAccessToken(
                    libraryId = libraryId,
                    accessToken = et.token
                ).data.now()
                validAccessToken!!
            }
        } else {
            validAccessToken = SMHService.shared.getAccessToken(
                libraryId = libraryId,
                librarySecret = library.LibrarySecret,
                grant = "admin"
            ).data.now()
            return validAccessToken!!
        }
    }

    fun update(libraryId: String, librarySecret: String) {
        this.library = Library(libraryId, librarySecret)
    }

    override suspend fun isLogin(): Boolean {
        return library.libraryId.isNotEmpty() && library.LibrarySecret.isNotEmpty()
    }

    override fun usedToHaveLogin(): Boolean {
        return library.libraryId.isNotEmpty() && library.LibrarySecret.isNotEmpty()
    }
}

/**
 * 空用户，表示未登录状态
 */
object NullSMHUser : SMHSimpleUser() {
    override val libraryId: String
        get() = throw SMHNoUserException

    override val userSpace: UserSpace
        get() = throw SMHNoUserException

    override suspend fun provideAccessToken(): AccessToken {
        throw SMHNoUserException
    }
}

/**
 * 登录用户变化观察者
 *
 */
interface SMHUserObserver {
    /**
     * 登录用户变化时通知
     *
     * @param user 新登录的用户
     */
    fun onUserChange(user: SMHUser)
}

/**
 * 用户中心工具类，可以用来管理观察者
 *
 */
class UserCenter {
    companion object {
        val g = UserCenter()
    }

    private val observers = mutableSetOf<SMHUserObserver>()

    @Synchronized
    fun addObserver(observer: SMHUserObserver) {
        observers.add(observer)
    }

    @Synchronized
    fun removeObserver(observer: SMHUserObserver) {
        observers.remove(observer)
    }

    fun notifyAll(user: SMHUser) {
        val observers = observers.toSet()
        observers.forEach {
            it.onUserChange(user)
        }
    }
}