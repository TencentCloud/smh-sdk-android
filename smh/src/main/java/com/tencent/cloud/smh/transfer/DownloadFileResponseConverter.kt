package com.tencent.cloud.smh.transfer

import com.tencent.cloud.smh.SMHClientException
import com.tencent.qcloud.core.common.QCloudProgressListener
import com.tencent.qcloud.core.http.HttpResponse
import com.tencent.qcloud.core.http.ResponseBodyConverter
import com.tencent.qcloud.core.http.ResponseInputStreamConverter
import com.tencent.qcloud.core.http.SelfCloseConverter

class DownloadResponseConverter<T>(
    val result: DownloadResult = DownloadResult(),
    var progressListener: QCloudProgressListener? = null,
): SelfCloseConverter, ResponseBodyConverter<T>(

) {

    override fun convert(response: HttpResponse<T>?): T {

        response?: throw SMHClientException("HttpResponseIsNull")

        val inputStreamConverter = ResponseInputStreamConverter<T>()
        inputStreamConverter.progressListener = progressListener
        inputStreamConverter.convert(response)
        val inputStream = inputStreamConverter.inputStream

        result.parseResponseBody(response)
        result.inputStream = inputStream

        return result as T
    }

}