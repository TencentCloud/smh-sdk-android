## SMH Android SDK

### 集成 SDK

```groovy
implementation 'com.qcloud.cos:smh-android:1.+'
```

### 开始使用

#### 1. 初始化资源库

```
SMHUser user = new StaticUser("libraryId", "librarySecret");
SMHCollectionFuture smh = new SMHCollection(context, user).future();
```

#### 2. 上传文件

```
CompletableFuture<ConfirmUpload> cf = smh.initMultipartUpload(
     "myFileName", metadata, isOverride, directory
).thenCompose(initUpload -> {
     // 列出已上传的分片，适用于续传场景
     return smh.listMultipartUpload(initUpload.confirmKey);
}).thenCompose(multiUploadMetadata -> smh.multipartUpload(multiUploadMetadata,
     localUri, fileSize).thenApply(etag -> new String[]{multiUploadMetadata.confirmKey, etag})
).thenCompose(uploadInfo -> smh.confirmUpload(uploadInfo[0]));
```

#### 3. 下载文件

```
Uri contentUri = ...;
CompletableFuture<Uri> cf = smh.initDownload(content.name).thenCompose(downloadInfo -> {
   // 执行下载
   return smh.download(downloadInfo.url, contentUri).thenApply(Void -> contentUri);
});
```

### 4. 其他接口

参考 [API 文档](javadoc.zip)