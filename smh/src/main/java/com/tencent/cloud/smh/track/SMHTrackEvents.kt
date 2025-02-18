package com.tencent.cloud.smh.track
import com.google.gson.annotations.SerializedName
import com.tencent.cloud.smh.BuildConfig
import com.tencent.cloud.smh.SMHClientException
import com.tencent.cloud.smh.SMHException
import com.tencent.cloud.smh.SMHUser

/**
 * <p>
 * Created by rickenwang on 2021/8/11.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */

const val ApiRequestEventCode = "api_request_android"
const val UploadEventCode = "upload_android"
const val DownloadEventCode = "download_android"
const val SMHBeaconKey = "0DOU06CCWK49FGC7"

const val ResultSuccess = "Success"
const val ResultFailure = "Failure"

const val ClientError = "Client"
const val ServerError = "Server"

class SMHSuccessRequestTrackEvent(
    requestName: String,
    smhUser: SMHUser,
    smhPath: String?,
    tookTime: Long,
): SMHSuccessTrackEvent(
    eventCode = ApiRequestEventCode,
    requestName, smhUser, smhPath, tookTime
)

class SMHFailureRequestTrackEvent(
    requestName: String,
    smhUser: SMHUser,
    smhPath: String?,
    tookTime: Long,
    exception: Exception
): SMHFailureTrackEvent(
    eventCode = ApiRequestEventCode,
    requestName, smhUser, smhPath, tookTime, exception
)

open class SMHSuccessTrackEvent(
    eventCode: String,
    requestName: String,
    smhUser: SMHUser,
    smhPath: String?,
    tookTime: Long,
): ApiTrackEvent(
    requestName = requestName,
    libraryId = smhUser.libraryId,
    spaceId = smhUser.userSpace.spaceId?: "default",
    userId = smhUser.userSpace.userId,
    smhPath = smhPath,
    tookTime = tookTime,
    requestResult = ResultSuccess,
    requestId = null,
    smhRequestId = null,
    errorType = null,
    errorMessage = null,
    errorCode = null,
    versionCode = BuildConfig.SMH_VERSION_CODE,
    versionName = BuildConfig.SMH_VERSION_NAME,
    eventCode = eventCode,
)

open class SMHFailureTrackEvent(
    eventCode: String,
    requestName: String,
    smhUser: SMHUser,
    smhPath: String?,
    tookTime: Long,
    exception: Exception
): ApiTrackEvent(
    requestName = requestName,
    libraryId = smhUser.libraryId,
    spaceId = smhUser.userSpace.spaceId?: "default",
    userId = smhUser.userSpace.userId,
    smhPath = smhPath,
    tookTime = tookTime,
    requestResult = ResultFailure,
    requestId = extractRequestId(exception),
    smhRequestId = extractSMHRequestId(exception),
    errorType = extractErrorType(exception),
    errorMessage = extractErrorMessage(exception),
    errorCode = extractErrorCode(exception),
    versionCode = BuildConfig.SMH_VERSION_CODE,
    versionName = BuildConfig.SMH_VERSION_NAME,
    eventCode = eventCode,
)

class SuccessDownloadTrackEvent(
    smhUser: SMHUser,
    smhPath: String,
    localUri: String,
    tookTime: Long,
    contentLength: Long,
): SuccessTransferTrackEvent(
    eventCode = DownloadEventCode, smhUser, smhPath, localUri, tookTime, contentLength
)

class SuccessUploadTrackEvent(
    smhUser: SMHUser,
    smhPath: String,
    localUri: String,
    tookTime: Long,
    contentLength: Long,
): SuccessTransferTrackEvent(
    eventCode = UploadEventCode, smhUser, smhPath, localUri, tookTime, contentLength
)

open class SuccessTransferTrackEvent(
    eventCode: String,
    smhUser: SMHUser,
    smhPath: String,
    localUri: String,
    tookTime: Long,
    contentLength: Long,
): TransferTrackEvent(
    libraryId = smhUser.libraryId,
    spaceId = smhUser.userSpace.spaceId?: "default",
    userId = smhUser.userSpace.userId,
    smhPath = smhPath,
    localUri = localUri,
    tookTime = tookTime,
    contentLength = contentLength,
    transferSpeed = contentLength / tookTime,
    requestResult = ResultSuccess,
    requestId = null,
    smhRequestId = null,
    errorType = null,
    errorMessage = null,
    errorCode = null,
    versionCode = BuildConfig.SMH_VERSION_CODE,
    versionName = BuildConfig.SMH_VERSION_NAME,
    eventCode = eventCode,
)

class FailureUploadTrackEvent(
    smhUser: SMHUser,
    smhPath: String,
    localUri: String,
    tookTime: Long,
    contentLength: Long,
    exception: Exception
): FailureTransferTrackEvent(
    eventCode = UploadEventCode, smhUser, smhPath, localUri, tookTime, contentLength, exception
)

class FailureDownloadTrackEvent(
    smhUser: SMHUser,
    smhPath: String,
    localUri: String,
    tookTime: Long,
    contentLength: Long,
    exception: Exception
): FailureTransferTrackEvent(
    eventCode = DownloadEventCode, smhUser, smhPath, localUri, tookTime, contentLength, exception
)

open class FailureTransferTrackEvent(
    eventCode: String,
    smhUser: SMHUser,
    smhPath: String,
    localUri: String,
    tookTime: Long,
    contentLength: Long,
    exception: Exception
): TransferTrackEvent(
    libraryId = smhUser.libraryId,
    spaceId = smhUser.userSpace.spaceId?: "default",
    userId = smhUser.userSpace.userId,
    smhPath = smhPath,
    localUri = localUri,
    tookTime = tookTime,
    contentLength = contentLength,
    transferSpeed = null,
    requestResult = ResultFailure,
    requestId = extractRequestId(exception),
    smhRequestId = extractSMHRequestId(exception),
    errorType = extractErrorType(exception),
    errorMessage = extractErrorMessage(exception),
    errorCode = extractErrorCode(exception),
    versionCode = BuildConfig.SMH_VERSION_CODE,
    versionName = BuildConfig.SMH_VERSION_NAME,
    eventCode = eventCode,
)


open class TransferTrackEvent(
    @SerializedName("library_id") val libraryId: String?,
    @SerializedName("space_id") val spaceId: String,
    @SerializedName("smh_path") val smhPath: String,
    @SerializedName("local_uri") val localUri: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("took_time") val tookTime: Long,
    @SerializedName("content_length") val contentLength: Long,
    @SerializedName("transfer_speed") val transferSpeed: Long?,
    @SerializedName("request_result") val requestResult: String,
    @SerializedName("request_id") val requestId: String?,
    @SerializedName("smh_request_id") val smhRequestId: String?,
    @SerializedName("error_type") val errorType: String?,
    @SerializedName("error_message") val errorMessage: String?,
    @SerializedName("error_code") val errorCode: String?,
    @SerializedName("smh_version_code") val versionCode: Int,
    @SerializedName("smh_version_name") val versionName: String,
    eventCode: String,
): TrackEvent(eventCode) {

    override fun commonParams(): Map<String, String> {
        return mapOf()
    }

    override fun getBeaconKey(): String{
        return SMHBeaconKey
    }
}

open class ApiTrackEvent(
    @SerializedName("request_name") val requestName: String,
    @SerializedName("library_id") val libraryId: String?,
    @SerializedName("space_id") val spaceId: String,
    @SerializedName("smh_path") val smhPath: String?,
    @SerializedName("user_id") val userId: String,
    @SerializedName("consume_time") val tookTime: Long,
    @SerializedName("request_result") val requestResult: String,
    @SerializedName("request_id") val requestId: String?,
    @SerializedName("smh_request_id") val smhRequestId: String?,
    @SerializedName("error_type") val errorType: String?,
    @SerializedName("error_message") val errorMessage: String?,
    @SerializedName("error_code") val errorCode: String?,
    @SerializedName("smh_version_code") val versionCode: Int,
    @SerializedName("smh_version_name") val versionName: String,
    eventCode: String,
): TrackEvent(eventCode) {

    override fun commonParams(): Map<String, String> {
        return mapOf()
    }

    override fun getBeaconKey(): String{
        return SMHBeaconKey
    }
}

fun extractErrorCode(exception: Exception): String {

    return if (exception is SMHException) {
        exception.errorCode?: exception.innerCode?: "unknown"
    } else if (exception is SMHClientException) {
        exception.code
    } else {
        briefErrorMessage(exception)
    }
}

fun briefErrorMessage(exception: Exception): String {

    val maxSize = 20
    val errorMessage = extractErrorMessage(exception)
    val messageLength = errorMessage.length
    return if (messageLength <= maxSize) {
        errorMessage
    } else {
        "${errorMessage.substring(0, maxSize / 2)}...${errorMessage.substring(messageLength - maxSize / 2, messageLength)}"
    }
}

fun extractErrorMessage(exception: Exception): String {
    return if (exception is SMHException) {
        exception.errorMessage?: exception.message?: "unknown"
    } else {
        exception.message?: "unknown"
    }
}

fun extractRequestId(exception: Exception): String? {

    return if (exception is SMHException) {
        exception.requestId
    } else {
        null
    }
}

fun extractSMHRequestId(exception: Exception): String? {
    return if (exception is SMHException) {
        exception.smhRequestId
    } else {
        null
    }
}

fun extractErrorType(exception: Exception): String {
    return if (exception is SMHException) {
        ServerError
    } else {
        ClientError
    }
}