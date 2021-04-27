package com.tencent.cloud.smh

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.tencent.cloud.smh.api.model.Directory
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class SMHTest {

    lateinit var smh: SMHCollection
    lateinit var context: Context
    private val defaultDirectory = Directory("default")

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
        smh = SMHCollection(
            context,
            user = StaticUser(
                libraryId = "your_library_id",
                librarySecret = "your_library_secret"
            )
        )
    }

    @Test
    fun testOneByOne() {
        testDirectory()
        testSingleUpload()
        testHead()
        testDeleteResource()
        testMultipartUpload()
        testDownload()
        testSymlink()
        testSMHList()
        testDeleteAllResources()
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

            val directories = smh.listDirectory()
            Assert.assertNull(directories.find {
                it.path == newName
            })

            Assert.assertNull(directories.find {
                it.path == albumName
            })

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
                overrideOnNameConflict = true,
                meta = meta
            )
            val metadata = smh.listMultipartUpload(initUpload.confirmKey)
            val eTag = smh.multipartUpload(metadata, uri, size)
            Assert.assertNotNull(eTag)
            val confirm = smh.confirmUpload(initUpload.confirmKey)
            Assert.assertNotNull(confirm.key)
            Assert.assertEquals(confirm.fileName, name)

            // list again
            val directoryContents = smh.list(dir = defaultDirectory, paging = false)
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

            val meta = mapOf("date" to "2021-1-1")
            // single upload
            val initUpload = smh.initUpload(
                name = name,
                dir = defaultDirectory,
                overrideOnNameConflict = true,
                meta = meta
            )
            val eTag = smh.upload(initUpload, uri)
            Assert.assertNotNull(eTag)
            val confirm = smh.confirmUpload(initUpload.confirmKey)
            Assert.assertNotNull(confirm.key)
            Assert.assertEquals(confirm.fileName, name)

            // list again
            val directoryContents = smh.list(dir = defaultDirectory, paging = false)
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
            val directoryContents = smh.list(dir = defaultDirectory, paging = false)
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
            val directoryContents = smh.list(dir = defaultDirectory, paging = false)
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
            val remoteDirs = smh.listDirectory()
            remoteDirs.forEach {
                val directoryContents = smh.list(it, paging = false)
                directoryContents.contents.forEach {
                    Assert.assertNotNull(it.name)
                    Assert.assertNotNull(it.creationTime)
                    Assert.assertNotNull(it.type)
                }
            }
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
            val directoryContents = smh.list(dir = defaultDirectory, paging = false)
            val assets = directoryContents.contents
            Assert.assertTrue(assets.count() > 0)
            val source = assets[Random.nextInt(0, assets.count())]
            val rawName = defaultDirectory.path?.let {
                source.name.replace("${it}/", "")
            } ?: source.name

            // create sym link
            smh.createSymLink(rawName, newAlbum, source.name)
            val newResourceName = "${newAlbumName}/${rawName}"
            val newAlbumContents = smh.list(newAlbum, paging = false)
            Assert.assertNotNull(newAlbumContents.contents.find { it.name == newResourceName })

            // delete sym link
            smh.delete(newResourceName)
            val newAlbumContentsAgain = smh.list(newAlbum, paging = false)
            Assert.assertTrue(newAlbumContentsAgain.contents.isEmpty())

            // delete directory
            smh.deleteDirectory(newAlbum)
        }
    }

    @Test
    fun testDeleteResource() {
        runBlocking {
            val directoryContents = smh.list(dir = defaultDirectory, paging = false)
            Assert.assertTrue(directoryContents.contents.isNotEmpty())

            val count = directoryContents.contents.count()

            val delete = directoryContents.contents[Random.nextInt(
                0,
                directoryContents.contents.count()
            )]
            try {
                smh.delete(delete.name)

                val after = smh.list(dir = defaultDirectory, paging = false)
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
            val directories = smh.listDirectory()
            directories.forEach {
                val directoryContents = smh.list(dir = it, paging = false)
                directoryContents.contents.forEach {
                    try {
                        smh.delete(it.name)
                    } catch (e: SMHException) {
                        Assert.assertTrue(e.statusCode == 404)
                    }
                }
                smh.deleteDirectory(it)
            }

            val after = smh.listDirectory()
            Assert.assertTrue(after.isEmpty())
        }
    }
}