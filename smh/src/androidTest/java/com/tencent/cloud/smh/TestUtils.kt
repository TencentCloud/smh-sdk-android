package com.tencent.cloud.smh

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.*

/**
 * <p>
 * Created by rickenwang on 2021/7/23.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */


@Throws(IOException::class)
fun createFile(file: File, fileLength: Long): Boolean {
    val parentFile = file.parentFile
    if (file.exists() && file.length() == fileLength) {
        return true
    }
    file.delete()
    if (!parentFile.exists()) {
        parentFile.mkdirs()
    }
    if (!file.exists()) {
        file.createNewFile()
    }
    val accessFile = RandomAccessFile(file.absolutePath, "rws")
    accessFile.setLength(fileLength)
    accessFile.seek(fileLength / 2)
    accessFile.write(Random().nextInt(200))
    accessFile.seek(fileLength - 1)
    accessFile.write(Random().nextInt(200))
    accessFile.close()
    return true
}

val smhSmallMediaKey = "smhSmallMedia.txt"
val smhBigMediaKey = "smhBigMedia.txt"
val smhHugeMediaKey = "smhHugeMediaKey.txt"

val smhDirectoryName = "smhDirectory"

val smhSmallMediaSize = 100L
val smhBigMediaSize = 1024 * 1024 *20L + 100
val smhHugeMediaSize = 1024 * 1024 * 1000L + 100