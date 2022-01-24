package com.tencent.cloud.smh.transfer

import com.tencent.cloud.smh.SMHClientException
import com.tencent.cloud.smh.SMHException

/**
 * <p>
 * Created by rickenwang on 2021/8/2.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
interface SMHProgressListener {

    fun onProgressChange(request: SMHRequest, progress: Long, target: Long)
}

interface SMHStateListener {

    fun onStateChange(request: SMHRequest, state: SMHTransferState)
}

interface SMHResultListener {

    fun onSuccess(request: SMHRequest, result: SMHResult)

    fun onFailure(request: SMHRequest, smhException: SMHException?, smhClientException: SMHClientException?)
}