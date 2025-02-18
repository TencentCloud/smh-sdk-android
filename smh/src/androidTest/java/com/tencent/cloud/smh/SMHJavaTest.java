package com.tencent.cloud.smh;

import static com.tencent.cloud.smh.TestUtilsKt.createFile;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tencent.cloud.smh.api.model.Directory;
import com.tencent.cloud.smh.transfer.DownloadFileRequest;
import com.tencent.cloud.smh.transfer.DownloadFileResult;
import com.tencent.cloud.smh.transfer.DownloadRequest;
import com.tencent.cloud.smh.transfer.DownloadResult;
import com.tencent.cloud.smh.transfer.SMHDownloadTask;
import com.tencent.cloud.smh.transfer.SMHProgressListener;
import com.tencent.cloud.smh.transfer.SMHRequest;
import com.tencent.cloud.smh.transfer.SMHUploadTask;
import com.tencent.cloud.smh.transfer.TransferTaskFuture;
import com.tencent.cloud.smh.transfer.UploadFileRequest;
import com.tencent.qcloud.core.logger.QCloudLogger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import kotlin.Unit;

/**
 * <p>
 *     SMHJavaTest
 * </p>
 */
@RunWith(AndroidJUnit4.class)
public class SMHJavaTest {

    private Context context;
    private SMHCollectionFuture smh;

    private Directory defaultDirectory = new Directory("default");

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getContext();
        smh = new SMHCollection(
                context,
                new MySMHSimpleUser()
        ).future();
    }

    @Test
    public void testFutureListDirectory() throws ExecutionException, InterruptedException, SMHException, IOException {
//        CompletableFuture<Void> cf = smh.list(1, 50).thenCompose(directories -> {
//            List<CompletableFuture<DirectoryContents>> cs = new ArrayList<>();
//            for (Directory dir : directories) {
//                cs.add(smh.list(dir).whenComplete((contents, error) -> {
//                    Assert.assertNull(error);
//                    contents.contents.forEach(content -> {
//                        Assert.assertNotNull(content.name);
//                        Assert.assertNotNull(content.creationTime);
//                        Assert.assertNotNull(content.type);
//                    });
//                }));
//            }
//            return CompletableFuture.allOf(cs.toArray(new CompletableFuture[0]));
//        });
//        cf.get();
    }

//    @Test
//    public void testFutureUpload() throws ExecutionException, InterruptedException, SMHException, IOException {
//        List<Triple<Uri, String, Long>> assets = (List<Triple<Uri, String, Long>>) MSHelper.fetchMediaLists(
//                context,
//                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//        );
//        Triple<Uri, String, Long> asset = assets.get(new Random().nextInt(assets.size()));
//
//        try {
//            smh.createDirectory(defaultDirectory).get();
//        } catch (Exception e) {
//            Assert.assertTrue(e.getCause() instanceof SMHException);
//            Assert.assertTrue(((SMHException) e.getCause()).statusCode == 409);
//        }
//
//        Map<String, String> meta = new HashMap<>();
//        meta.put("mkey", "mvalue");
//        CompletableFuture<ConfirmUpload> cf = smh.initMultipartUpload(
//                asset.component2(),
//                meta, null, defaultDirectory
//        ).thenCompose(initUpload -> smh.listMultipartUpload(initUpload.confirmKey)
//        ).thenCompose(multiUploadMetadata -> smh.multipartUpload(
//                multiUploadMetadata, asset.component1(), asset.component3(), null, null).thenApply(
//                        etag -> new String[]{multiUploadMetadata.confirmKey, etag})
//        ).thenCompose(uploadInfo -> smh.confirmUpload(uploadInfo[0], ""));
//        // wait for finish
//        cf.get();
//
//        // list again
//        DirectoryContents directoryContents = smh.list(defaultDirectory, 1, 50).get();
//        Boolean contains = false;
//        for (MediaContent content : directoryContents.contents) {
//            if (content.name.equals(defaultDirectory.path + "/" + asset.component2())) {
//                contains = true;
//                break;
//            }
//        }
//        Assert.assertTrue(contains);
//    }
//
//    @Test
//    public void testFutureDownload() throws ExecutionException, InterruptedException {
//        // DirectoryContents directoryContents = smh.list(defaultDirectory, 1, 50).get();
//        // MediaContent content = directoryContents.contents.get(0);
//        CompletableFuture<Uri> cf = smh.initDownload("123", new Directory()).thenCompose(downloadInfo -> {
//            String rawName = System.currentTimeMillis() + "_" + "123".replace(
//                    defaultDirectory.path + "/", "");
//            Uri contentUri = MSHelper.createNewPendingAsset(
//                    context,
//                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//                    rawName);
//            Assert.assertNotNull(downloadInfo.metaData);
//            return smh.download(downloadInfo.url, contentUri).thenApply(empty -> contentUri);
//        }).whenComplete((contentUri, error) -> {
//            Assert.assertNull(error);
//            MSHelper.endAssetPending(context, contentUri);
//        });
//
//        Uri contentUri = cf.join();
//        Assert.assertNotNull(contentUri);
//        MSHelper.removeResourceUri(context, contentUri);
//    }



    @Test
    public void testDownload() {

        CompletableFuture<DownloadResult> cf = smh.initDownload("123", new Directory()).thenCompose(downloadInfo -> {
            DownloadRequest downloadRequest = new DownloadRequest(downloadInfo.url, (request, progress, target) -> {
                Log.i("Test", "Progress change " + progress + "/" + target);
            });
            return smh.download(downloadRequest);
        });

        DownloadResult downloadResult = cf.join();
        int len = 0;
        int count = 0;
        byte[] buffer = new byte[8192];
        InputStream inputStream = downloadResult.getInputStream();

        try {

            while (true) {
                len = inputStream.read(buffer, 0, buffer.length);
                QCloudLogger.i("Test", "read len " + len);
                if (len < 0) {
                    break;
                } else {
                    count += len;
                }
            }

        } catch (Exception e) {
            QCloudLogger.i("Test", "exception " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        QCloudLogger.i("Test", "read count is " + count);
    }

    @Test
    public void testUploadTask() throws IOException {
        File file = File.createTempFile("read", ".txt");
        createFile(file, 1024 * 1024 *5);
        UploadFileRequest uploadFileRequest = new UploadFileRequest(
                "123.jpg", Uri.fromFile(file), null, null, null, null);
        uploadFileRequest.setProgressListener(new SMHProgressListener() {
            @Override
            public void onProgressChange(@NonNull SMHRequest request, long progress, long target) {
                Log.i("Test", "Progress change " + progress+ "/" + target);
            }
        });

        SMHCollection smh = new SMHCollection(
                context,
                new MySMHSimpleUser()
        );

        SMHUploadTask uploadTask = new SMHUploadTask(context, smh, uploadFileRequest, true);
        TransferTaskFuture taskFuture = uploadTask.future();
        CompletableFuture<Unit> cf = taskFuture.start().whenComplete((unit, error) -> {
            Assert.assertNull(error);
        });
        cf.join();
    }


    @Test
    public void testDownloadTask() {

        DownloadFileRequest downloadFileRequest = new DownloadFileRequest(
                "123", null, null);
        downloadFileRequest.setRange(100, null);
        downloadFileRequest.setProgressListener(new SMHProgressListener() {
            @Override
            public void onProgressChange(@NonNull SMHRequest request, long progress, long target) {
                Log.i("Test", "Progress change " + progress + "/" + target);
            }
        });

        SMHCollection smh = new SMHCollection(
                context,
                new MySMHSimpleUser()
        );

        SMHDownloadTask downloadTask = new SMHDownloadTask(
                context,
                smh,
                downloadFileRequest
        );

        TransferTaskFuture taskFuture = downloadTask.future();

        CompletableFuture<Unit> cf = taskFuture.start().whenComplete((unit, error) -> {
            Assert.assertNull(error);
        });
        cf.join();
        DownloadFileResult result = (DownloadFileResult) downloadTask.getResultOrThrow();
        InputStream inputStream = result.getContent();

        try {
            byte[] buffer = new byte[8192];
            int len = -1;
            int count = 0;
            while (true) {
                len = inputStream.read(buffer, 0, buffer.length);
                QCloudLogger.i("Test", "read len " + len);
                if (len <= 0) {
                    break;
                } else {
                    count += len;
                }
            }
            inputStream.close();
            QCloudLogger.i("Test", "read count is " + count);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
