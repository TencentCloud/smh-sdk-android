package com.tencent.cloud.smh.ext

import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val UTC_LENGTH = 5

/**
 * 将 UTC 时间转换为本地时区时间。
 * @return 如果转换失败，则返回 null
 */
fun String?.utc2normalTimeMillis(pattern: String = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"): Long? {
    if (this == null || TextUtils.isEmpty(this)) {
        return null
    }
    try {
        var utc = this
        val index = utc.lastIndexOf('.')
        if (index < 0) {
            utc = utc.replace('Z', '.') + "000Z"
        } else {
            val abs = utc.length - index
            if (abs < UTC_LENGTH) {
                var sub = ""
                for (i in 0 until UTC_LENGTH - abs) {
                    sub += '0'
                }
                utc = utc.replace("Z", sub + "Z")
            }
        }
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        /** 修改默认时区为 UTC 零时区  */
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.parse(utc)?.time
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
