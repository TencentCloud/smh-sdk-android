package com.tencent.cloud.smh.utils

import com.tencent.cloud.smh.X_SMH_META_KEY_PREFIX
import com.tencent.cloud.smh.api.retrofit.headers
import java.util.Locale

/**
 * @author Created by jordanqin.
 *
 * @date Created in 2022/6/14 8:52 下午
 *
 * @description：
 *
 * Copyright 2010-2022 Tencent Cloud. All Rights Reserved.
 */
class Utils {
    companion object {
        /**
         * 修剪meta 去除前缀x-smh-meta-
         */
        fun metaDataTrim(metas: Map<String, String>?): Map<String, String>?{
            return metas
                ?.filterKeys { it.toLowerCase(Locale.ROOT).startsWith(X_SMH_META_KEY_PREFIX) }
                ?.mapKeys { it.key.substring(X_SMH_META_KEY_PREFIX.length) }
        }

        /**
         * 修剪meta 去除前缀x-smh-meta-
         * 数据源为headers
         */
        fun metaDataTrimByHeaders(headers: Map<String, List<String>>): Map<String, String>{
            return headers
                .filterKeys { it.toLowerCase(Locale.ROOT).startsWith(X_SMH_META_KEY_PREFIX) }
                .filterValues { it.isNotEmpty() }
                .mapKeys { it.key.substring(X_SMH_META_KEY_PREFIX.length) }
                .mapValues { it.value.first() }
        }

        fun baseUrl(host: String): String {
            return if(host.startsWith("http://", true) ||
                host.startsWith("https://", true)){
                return if(host.endsWith("/")){
                    host
                } else {
                    "${host}/"
                }
            } else {
                "https://${host}/"
            }
        }
    }
}