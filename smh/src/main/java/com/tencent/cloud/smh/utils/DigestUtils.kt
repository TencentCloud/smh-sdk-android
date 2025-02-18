package com.tencent.cloud.smh.utils

import com.tencent.cloud.smh.ClientInternalException
import com.tencent.cloud.smh.InvalidArgumentException
import java.security.MessageDigest

/**
 * @author Created by jordanqin.
 *
 * @date Created in 2022/4/22 11:05 上午
 *
 * @description：
 *
 * Copyright 2010-2022 Tencent Cloud. All Rights Reserved.
 */
object DigestUtils {
    /**
     * 获取sha256摘要字节数组
     */
    fun getSHA256FromBytes(data: ByteArray): ByteArray {
        return if (data.isNotEmpty()) {
            try {
                val messageDigest =
                    MessageDigest.getInstance("SHA-256")
                messageDigest.update(data)
                messageDigest.digest()
            } catch (var6: OutOfMemoryError) {
                throw ClientInternalException("OOM")
            }
        } else {
            throw InvalidArgumentException
        }
    }
}