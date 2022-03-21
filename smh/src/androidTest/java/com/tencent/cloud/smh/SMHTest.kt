package com.tencent.cloud.smh

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.tencent.cloud.smh.api.SMHService
import com.tencent.cloud.smh.api.dataOrNull
import com.tencent.cloud.smh.api.model.Directory
import com.tencent.cloud.smh.api.model.QuotaBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Exception
import java.lang.Thread.sleep
import kotlin.random.Random


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class SMHTest {

    lateinit var smh: SMHCollection
    lateinit var user: SMHUser
    lateinit var context: Context
    private val defaultDirectory = Directory("default")
    private val assistDirectory = Directory("assist")

    @Rule
    @JvmField
    var mRuntimePermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        user = StaticUser(
                libraryId = BuildConfig.SMH_ID,
                librarySecret = BuildConfig.SMH_KEY
        )
        smh = SMHCollection(
            context = context,
            user = user
        )
    }

    @Test
    fun testOneByOne() {
        testDirectory()
        testSingleUpload()
        testAlbumCover()
        testHead()
        testDeleteResource()
        testMultipartUpload()
        testDownload()
        testSymlink()
        testSMHList()
        testDeleteAllResources()
    }

    @Test
    fun testUserSpace() {
        runBlocking {
            val createResponse = SMHService.shared.createQuota(
                    user.libraryId,
                    user.provideAccessToken().token,
                    QuotaBody(
                            capacity = "1099511627776",
                            removeWhenExceed = true,
                            removeAfterDays = 30,
                            removeNewest = false
                    )
            )

            // val state = user.getSpaceState().dataOrNull

            val state = smh.getUserSpaceState()

            Assert.assertNotNull(state)
            Assert.assertNotNull(state.capacity)
            Assert.assertNotNull(state.capacityNumber)
            Assert.assertNotNull(state.size)
            Assert.assertNotNull(state.sizeNumber)
            Assert.assertNotNull(state.usagePercent)
            Assert.assertNotNull(state.remainSize)
        }
    }

    @Test
    fun testAlbumCover() {
        runBlocking {
            val coverUrl = smh.getAlbumCoverUrl(defaultDirectory.path!!)
            Assert.assertNotNull(coverUrl)
        }
    }

    @Test
    fun testDirectory() {
        runBlocking {
            val albumName = "testAlbum"
            val newName = "anotherAlbum"

            try {
                smh.createDirectory(Directory(albumName))
            } catch (e: SMHException) {
                Assert.assertTrue(e.statusCode == 409)
            }

            try {
                smh.renameDirectory(Directory(newName), Directory(albumName))
            } catch (e: SMHException) {
                Assert.assertTrue(false)
            }

            try {
                smh.deleteDirectory(Directory(newName))
            } catch (e: SMHException) {
                Assert.assertTrue(false)
            }

            val directories = smh.listAll(Directory(newName))

        }
    }

    @Test
    fun testMultipartUpload() {
        runBlocking {
            val (uri, name, size) = findUploadResource()

            // create default directory
            try {
                smh.createDirectory(defaultDirectory)
            } catch (e: SMHException) {
                Assert.assertTrue(e.statusCode == 409)
            }

            val meta = mapOf("date" to "2021-1-1")
            // multipart upload
            val initUpload = smh.initMultipartUpload(
                name = name,
                dir = defaultDirectory,
                meta = meta
            )
            val metadata = smh.listMultipartUpload(initUpload.confirmKey)
            val eTag = smh.multipartUpload(metadata, uri, size)
            Assert.assertNotNull(eTag)
            val confirm = smh.confirmUpload(initUpload.confirmKey)
            Assert.assertNotNull(confirm.key)
            Assert.assertEquals(confirm.fileName, name)

            // list again
            val directoryContents = smh.listAll(dir = defaultDirectory)
            Assert.assertNotNull(directoryContents.contents.find {
                it.name == "${defaultDirectory.path}/$name"
            })

            val initDownload = smh.initDownload(name, dir = defaultDirectory)
            meta.forEach { (t, u) ->
                Assert.assertNotNull(initDownload.metaData?.get(t))
                Assert.assertTrue(initDownload.metaData?.get(t)?.get(0) == u)
            }
        }
    }

    @Test
    fun testSingleUpload() {
        runBlocking {
            val (uri, name, size) = findUploadResource()

            // create default directory
            try {
                smh.createDirectory(defaultDirectory)
            } catch (e: SMHException) {
                Assert.assertTrue(e.statusCode == 409)
            }

            try {
                smh.createDirectory(assistDirectory)
            } catch (e: SMHException) {
            }

            val meta = mapOf("date" to "2021-1-1")
            // single upload
            val initUpload = smh.initUpload(
                name = name,
                dir = defaultDirectory,
                meta = meta
            )
            val eTag = smh.upload(initUpload, uri)
            Assert.assertNotNull(eTag)
            val confirm = smh.confirmUpload(initUpload.confirmKey)
            Assert.assertNotNull(confirm.key)
            Assert.assertEquals(confirm.fileName, name)

            // list again
            var directoryContents = smh.listAll(dir = defaultDirectory)
            Assert.assertNotNull(directoryContents.contents.find {
                it.name == "${defaultDirectory.path}/$name"
            })

            // 生成缩略图
            val previewThumbnail = smh.getThumbnail(name = directoryContents.contents.first().name, size = 50)
            Assert.assertNotNull(previewThumbnail)
            Assert.assertNotNull(previewThumbnail.location)

            // move file
            val newName = "target_${System.currentTimeMillis()}.jpg"
            val content = directoryContents.contents.first()
            val renameFileResponse = smh.renameFile(targetDir = assistDirectory, targetName = newName, sourceName = content.name)
            Assert.assertNotNull(renameFileResponse)
            Assert.assertNotNull(renameFileResponse.path)

            smh.renameFile(targetName = content.name, sourceDir = assistDirectory, sourceName = newName)

            // move targetDir not exist
            try {
                val targetDirNotExistResponse = smh.renameFile(targetDir = Directory("not_exist"), targetName = newName, sourceName = content.name)
            } catch (e: SMHException) {
                Assert.assertEquals(e.statusCode, 404)
            }

            // head file
            val headFileResponse = smh.headFile(name, dir = defaultDirectory)
            Assert.assertNotNull(headFileResponse)
            Assert.assertNotNull(headFileResponse.contentType)
            Assert.assertNotNull(headFileResponse.size)
            Assert.assertNotNull(headFileResponse.creationTime)
            Assert.assertNotNull(headFileResponse.eTag)
            Assert.assertNotNull(headFileResponse.crc64)
            Assert.assertNotNull(headFileResponse.type)
//            meta.forEach { (t, u) ->
//                Assert.assertNotNull(headFileResponse.metas?.get(t))
//                Assert.assertTrue(headFileResponse.metas?.get(t) == u)
//            }

            // head file not exist
            try {
                smh.headFile("not_exist", defaultDirectory)
            } catch (e: SMHException) {
                Assert.assertEquals(e.statusCode, 404)
            }

            // download after rename
//            val initDownload = smh.initDownload(name, dir = defaultDirectory)
//            // val initDownload =  smh.initDownload("ori2.jpg", dir = defaultDirectory)
//            meta.forEach { (t, u) ->
//                Assert.assertNotNull(initDownload.metaData?.get(t))
//                Assert.assertTrue(initDownload.metaData?.get(t)?.get(0) == u)
//            }
        }
    }




    private suspend fun findUploadResource(): Triple<Uri, String, Long> {
        val assets = MSHelper.fetchMediaLists(
            context,
            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        Assert.assertTrue(assets.isNotEmpty())
        return assets[Random.nextInt(0, assets.count())]
    }

    @Test
    fun testHead() {
        runBlocking {
            val directoryContents = smh.listAll(dir = defaultDirectory)
            Assert.assertTrue(directoryContents.contents.isNotEmpty())
            val content = directoryContents.contents.first()
            val info = smh.getFileInfo(content.name, Directory())
            Assert.assertNotNull(info)
            Assert.assertNotNull(info?.cosUrl)
        }
    }

    @Test
    fun testDownload() {
        runBlocking {
            val directoryContents = smh.listAll(dir = defaultDirectory)
            val assets = directoryContents.contents
            Assert.assertTrue(assets.count() > 0)
            val download = assets[Random.nextInt(0, assets.count())]

            val initDownload = smh.initDownload(
                name = download.name
            )
            val rawName = defaultDirectory.path?.let {
                download.name.replace("${it}/", "")
            } ?: download.name
            val fileName = "${System.currentTimeMillis()}_$rawName"
            val url = requireNotNull(initDownload.url)
            val metadata = requireNotNull(initDownload.metaData)
            val contentUri = requireNotNull(MSHelper.createNewPendingAsset(
                context,
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                assetName = fileName,
            ))

            try {
                smh.download(url, contentUri = contentUri)
                MSHelper.endAssetPending(context, contentUri)

                val (size, name, path) = requireNotNull(MSHelper.getResourceInfo(context, contentUri))
                Assert.assertEquals(name, fileName)
            } finally {
                // delete file
                MSHelper.removeResourceUri(context, contentUri)
            }

        }
    }

    @Test
    fun testSMHList() {
        runBlocking {
            val remoteDirs = smh.listAll(Directory(""))
        }
    }

    @Test
    fun testSymlink() {
        runBlocking {
            val newAlbumName = "album_${System.currentTimeMillis()}"
            val newAlbum = Directory(newAlbumName)

            // create temp directory
            try {
                smh.createDirectory(newAlbum)
            } catch (e: SMHException) {
                Assert.assertTrue(e.statusCode == 409)
            }

            // pick random source
            val directoryContents = smh.listAll(dir = defaultDirectory)
            val assets = directoryContents.contents
            Assert.assertTrue(assets.count() > 0)
            val source = assets[Random.nextInt(0, assets.count())]
            val rawName = defaultDirectory.path?.let {
                source.name.replace("${it}/", "")
            } ?: source.name

            // create sym link
            smh.createSymLink(rawName, newAlbum, source.name)
            val newResourceName = "${newAlbumName}/${rawName}"
            val newAlbumContents = smh.listAll(newAlbum)
            Assert.assertNotNull(newAlbumContents.contents.find { it.name == newResourceName })

            // delete sym link
            smh.delete(newResourceName)
            val newAlbumContentsAgain = smh.listAll(newAlbum)
            Assert.assertTrue(newAlbumContentsAgain.contents.isEmpty())

            // delete directory
            smh.deleteDirectory(newAlbum)
        }
    }

    @Test
    fun testDeleteResource() {
        runBlocking {
            val directoryContents = smh.listAll(dir = defaultDirectory)
            Assert.assertTrue(directoryContents.contents.isNotEmpty())

            val count = directoryContents.contents.count()

            val delete = directoryContents.contents[Random.nextInt(
                0,
                directoryContents.contents.count()
            )]
            try {
                smh.delete(delete.name)

                val after = smh.listAll(dir = defaultDirectory)
                Assert.assertNull(after.contents.find { it.name == delete.name })
                Assert.assertEquals(count - 1, after.contents.count())
            } catch (e: SMHException) {
                Assert.assertTrue(e.statusCode == 404)
            }
        }
    }

    @Test
    fun testDeleteAllResources() {
        runBlocking {
            val directories = smh.listAll(Directory(""))
        }
    }

    @Test
    fun testUploadTask() {



    }
}