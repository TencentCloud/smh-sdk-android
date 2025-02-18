package com.tencent.cloud.smh.api.model

/**
 * 用于空间文件数量统计
 * <p>
 * </p>
 * Created by jordanqin on 11/13/23.
 * Copyright 2010-2023 Tencent Cloud. All Rights Reserved.
 */
/**
 * 用于空间文件数量统计
 * @property fileNum 总文件数量（包括回收站和历史版本）
 * @property dirNum 总文件夹数量（包括回收站)
 * @property recycledFileNum 回收站文件数量
 * @property recycledDirNum 回收站文件夹数量
 * @property historyFileNum 历史版本文件数量
 */
data class SpaceFileCount(
    val fileNum: String,
    val dirNum: String,
    val recycledFileNum: String,
    val recycledDirNum: String,
    val historyFileNum: String
)