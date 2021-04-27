package com.tencent.cloud.smh;

import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tencent.cloud.smh.api.model.ConfirmUpload;
import com.tencent.cloud.smh.api.model.Directory;
import com.tencent.cloud.smh.api.model.DirectoryContents;
import com.tencent.cloud.smh.api.model.MediaContent;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import kotlin.Triple;

/**
 * <p>
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
                new StaticUser("your_library_id",
                        "your_library_secret"
                )
        ).future();
    }

    @Test
    public void testFutureListDirectory() throws ExecutionException, InterruptedException, SMHException, IOException {
        CompletableFuture<Void> cf = smh.listDirectory().thenCompose(directories -> {
            List<CompletableFuture<DirectoryContents>> cs = new ArrayList<>();
            for (Directory dir : directories) {
                cs.add(smh.list(dir).whenComplete((contents, error) -> {
                    Assert.assertNull(error);
                    contents.contents.forEach(content -> {
                        Assert.assertNotNull(content.name);
                        Assert.assertNotNull(content.creationTime);
                        Assert.assertNotNull(content.type);
                    });
                }));
            }
            return CompletableFuture.allOf(cs.toArray(new CompletableFuture[0]));
        });
        cf.get();
    }

    @Test
    public void testFutureUpload() throws ExecutionException, InterruptedException, SMHException, IOException {
        List<Triple<Uri, String, Long>> assets = (List<Triple<Uri, String, Long>>) MSHelper.fetchMediaLists(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        );
        Triple<Uri, String, Long> asset = assets.get(new Random().nextInt(assets.size()));

        try {
            smh.createDirectory(defaultDirectory).get();
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof SMHException);
            Assert.assertTrue(((SMHException) e.getCause()).statusCode == 409);
        }

        Map<String, String> meta = new HashMap<>();
        meta.put("mkey", "mvalue");
        CompletableFuture<ConfirmUpload> cf = smh.initMultipartUpload(
                asset.component2(),
                meta, true, defaultDirectory
        ).thenCompose(initUpload -> smh.listMultipartUpload(initUpload.confirmKey)
        ).thenCompose(multiUploadMetadata -> smh.multipartUpload(multiUploadMetadata,
                asset.component1(), asset.component3()).thenApply(
                        etag -> new String[]{multiUploadMetadata.confirmKey, etag})
        ).thenCompose(uploadInfo -> smh.confirmUpload(uploadInfo[0]));
        // wait for finish
        cf.get();

        // list again
        DirectoryContents directoryContents = smh.list(defaultDirectory, false).get();
        Boolean contains = false;
        for (MediaContent content : directoryContents.contents) {
            if (content.name.equals(defaultDirectory.path + "/" + asset.component2())) {
                contains = true;
                break;
            }
        }
        Assert.assertTrue(contains);
    }

    @Test
    public void testFutureDownload() throws ExecutionException, InterruptedException {
        DirectoryContents directoryContents = smh.list(defaultDirectory, false).get();
        MediaContent content = directoryContents.contents.get(0);
        CompletableFuture<Uri> cf = smh.initDownload(content.name).thenCompose(downloadInfo -> {
            String rawName = System.currentTimeMillis() + "_" + content.name.replace(
                    defaultDirectory.path + "/", "");
            Uri contentUri = MSHelper.createNewPendingAsset(
                    context,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    rawName);
            Assert.assertNotNull(downloadInfo.metaData);
            return smh.download(downloadInfo.url, contentUri).thenApply(Void -> contentUri);
        }).whenComplete((contentUri, error) -> {
            Assert.assertNull(error);
            MSHelper.endAssetPending(context, contentUri);
        });

        Uri contentUri = cf.join();
        Assert.assertNotNull(contentUri);
        MSHelper.removeResourceUri(context, contentUri);
    }
}
