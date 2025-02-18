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

package com.tencent.cloud.smh.transfer

import android.content.Context
import android.net.Uri
import com.tencent.cloud.smh.api.SMHService
import com.tencent.cloud.smh.api.model.InitUpload
import com.tencent.cloud.smh.ext.cosService
import com.tencent.cloud.smh.ext.suspendBlock
import com.tencent.cos.xml.CosXmlBaseService
import com.tencent.cos.xml.listener.CosXmlProgressListener
import com.tencent.cos.xml.model.`object`.*
import java.net.URL

class COSFileTransfer constructor(
 private val context: Context,
 isDebuggable: Boolean = false
) {
 private val cos: CosXmlBaseService = cosService(context = context) {
  configuration {
   setRegion("ap-guangzhou") // 一个有效值即可
   isHttps(SMHService.isHttps())
   setDebuggable(isDebuggable)
   setDomainSwitch(false)
  }
 }

 suspend fun upload(uploader: InitUpload, uri: Uri, progressListener: CosXmlProgressListener? = null): String? {
  return suspendBlock<BasePutObjectResult> {
   val putRequest = BasePutObjectRequest(
    "",
    uploader.path,
    uri
   )
   putRequest.setRequestHeaders(uploader.headers.mapValues {
    listOf(it.value)
   })
   putRequest.requestURL = "${SMHService.getProtocol()}://${uploader.domain}${uploader.path}"
   putRequest.requestHeaders["Host"] = listOf(uploader.domain)
   //私有化 暂时不支持MD5校验
   if(SMHService.isPrivate) {
    putRequest.isNeedMD5 = false
   }
   putRequest.progressListener = progressListener
   cos.basePutObjectAsync(putRequest, it)
  }.takeIf { it.httpCode in 200..299 }?.eTag
 }

 suspend fun download(url: String, contentUri: Uri, offset: Long, cancelHandler: CancelHandler? = null,
                      cosXmlProgressListener: CosXmlProgressListener? = null): GetObjectResult {
  cancelHandler?.cos = cos
  val httpUrl = URL(url)
  val getRequest = GetObjectRequest(
   "",
   "",
   contentUri
  )
  getRequest.requestURL = url
  getRequest.requestHeaders["Host"] = listOf(httpUrl.host)
  return download(url, getRequest, offset, cancelHandler, cosXmlProgressListener)
 }

 suspend fun download(url: String, fullPath: String, offset: Long, cancelHandler: CancelHandler? = null,
                      cosXmlProgressListener: CosXmlProgressListener? = null): GetObjectResult {
  cancelHandler?.cos = cos
  val httpUrl = URL(url)
  val lastIndex = fullPath.lastIndexOf("/")
  val getRequest = GetObjectRequest(
   "",
   "",
   fullPath.substring(0, lastIndex + 1),
   fullPath.substring(lastIndex + 1)
  )
  getRequest.requestURL = url
  getRequest.requestHeaders["Host"] = listOf(httpUrl.host)
  return download(url, getRequest, offset, cancelHandler, cosXmlProgressListener)
 }

 suspend fun download(url: String, getRequest: GetObjectRequest, offset: Long, cancelHandler: CancelHandler? = null,
                      cosXmlProgressListener: CosXmlProgressListener? = null): GetObjectResult {
  return suspendBlock<GetObjectResult> {
   val httpUrl = URL(url)
   getRequest.progressListener = CosXmlProgressListener { complete, target ->
    cosXmlProgressListener?.onProgress(complete + offset, target + offset)
   }
   getRequest.setQueryEncodedString(httpUrl.query)
   if (offset > 0) {
    getRequest.setRange(offset)
   }
   getRequest.fileOffset = offset
   cancelHandler?.addRequests(listOf(getRequest))

   cos.getObjectAsync(getRequest, it)
  }
 }
}