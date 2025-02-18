package com.tencent.cloud.smh

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.tencent.cloud.smh.api.SMHService
import com.tencent.cloud.smh.api.model.ConflictStrategy
import com.tencent.cloud.smh.api.model.CreateFileFromTemplateRequest
import com.tencent.cloud.smh.api.model.Directory
import com.tencent.cloud.smh.api.model.MediaType
import com.tencent.cloud.smh.api.model.OrderDirection
import com.tencent.cloud.smh.api.model.OrderType
import com.tencent.cloud.smh.api.model.QuotaBody
import com.tencent.cloud.smh.api.model.SearchType
import com.tencent.cloud.smh.transfer.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.random.Random


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class SMHTest {
    val TAG: String = "SMHTest"

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
        user = MySMHSimpleUser()
        smh = SMHCollection(
            context = context,
            user = user,
            customHost = "api.test.tencentsmh.cn",
            isDebuggable = true
        )
    }

    @Test
    fun testOneByOne() {
        testDirectory()
//        testSingleUpload()
        testAlbumCover()
        testHead()
        testDeleteResource()
//        testMultipartUpload()
//        testDownload()
        testSymlink()
        testSMHList()
        testDeleteAllResources()
        testGetThumbnail()
    }

    /**
     * 删除文件
     */
    @Test
    fun testDeleteFile() {

        runBlocking {

            // 1. 直接使用 key 删除文件
            val key = "${System.currentTimeMillis()}"
            uploadFile(key, 100)
            val result1 = smh.delete(key)

            // 未开通回收站时，result 为空
            // checkNotNull(result)
            // checkNotNull(result.recycledItemId)

            // 2. 使用 dir + name 删除文件
            val dir1 = ""
            val name = "${System.currentTimeMillis()}"
            uploadFile("$dir1/$name", 100)
            val result2 = smh.delete(name = name, dir = Directory(dir1))

            // 3. dir 以/ 结尾
            uploadFile("$dir1/$name", 100)
            val result3 = smh.delete(name = name, dir = Directory("$dir1/"))

            // 4. 永久删除文件
            uploadFile(key, 100)
            val result4 = smh.delete(key, permanent = true)
            check(result4 == null)

            // 5. 删除一个不存在的文件
            try {
                val result5 = smh.delete(key, permanent = true)
            } catch (e: Exception) {
                Assert.assertTrue(e is SMHException && e.statusCode == 404)
            }

        }
    }



    /**
     * 重命名文件
     */
    @Test
    fun testRenameFile() {

        runBlocking {
            val targetName = "${System.currentTimeMillis()}"
            uploadFile(targetName, 100)

            // 1. 重命名文件-覆盖
            val name = "${System.currentTimeMillis()}"
            uploadFile(name, 100)
            val result = smh.renameFile(targetName = targetName, sourceName = name, conflictStrategy = ConflictStrategy.OVERWRITE)

            val name2 = "${System.currentTimeMillis()}"
            uploadFile(name2, 100)
            smh.renameFile(targetName = targetName, sourceName = name, conflictStrategy = ConflictStrategy.RENAME)
        }
    }

    /**
     * 重命名文件夹
     */
    @Test
    fun testRenameDirectoryOverride() {

        runBlocking {

            val targetName = "${System.currentTimeMillis()}"
            smh.createDirectory(Directory(targetName))

            // 1. 重命名文件夹-覆盖
            val name1 = "${System.currentTimeMillis()}"
            smh.createDirectory(Directory(name1))
            val result1 = smh.renameDirectory(
                target = Directory(targetName),
                source = Directory(name1),
                conflictStrategy = ConflictStrategy.RENAME)
            checkNotNull(result1)

            val name2 = "${System.currentTimeMillis()}"
            smh.createDirectory(Directory(name2))

            try {
                val result2 = smh.renameDirectory(
                    target = Directory(targetName),
                    source = Directory(name2),
                    conflictStrategy = ConflictStrategy.ASK)
                Assert.assertFalse(true)
            } catch (e: Exception) {
                check(e is SMHException && e.statusCode == 409)
            }

            try {

                val result3 = smh.renameDirectory(
                    target = Directory(targetName),
                    source = Directory(name2 + "not-found"),
                    conflictStrategy = ConflictStrategy.ASK)
                Assert.assertFalse(true)
            } catch (e: Exception) {
                check(e is SMHException && e.statusCode == 404)
            }
        }

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

//            val directories = smh.listAll(Directory(newName))

        }
    }

//    @Test
//    fun testMultipartUpload() {
//        runBlocking {
//            val (uri, name, size) = findUploadResource()
//
//            // create default directory
//            try {
//                smh.createDirectory(defaultDirectory)
//            } catch (e: SMHException) {
//                Assert.assertTrue(e.statusCode == 409)
//            }
//
//            val meta = mapOf("date" to "2021-1-1")
//            // multipart upload
//            val initUpload = smh.initMultipartUpload(
//                name = name,
//                dir = defaultDirectory,
//                meta = meta
//            )
//            val metadata = smh.listMultipartUpload(initUpload.confirmKey)
//            val eTag = smh.multipartUpload(metadata, uri, size)
//            Assert.assertNotNull(eTag)
//            val confirm = smh.confirmUpload(initUpload.confirmKey)
//            Assert.assertNotNull(confirm.key)
//            Assert.assertEquals(confirm.fileName, name)
//
//            // list again
//            val directoryContents = smh.listAll(dir = defaultDirectory)
//            Assert.assertNotNull(directoryContents.contents.find {
//                it.name == "${defaultDirectory.path}/$name"
//            })
//
//            val initDownload = smh.initDownload(name, dir = defaultDirectory)
//            meta.forEach { (t, u) ->
//                Assert.assertNotNull(initDownload.metaData?.get(t))
//                Assert.assertTrue(initDownload.metaData?.get(t) == u)
//            }
//        }
//    }
//
//    @Test
//    fun testSingleUpload() {
//        runBlocking {
//            val (uri, name, size) = findUploadResource()
//
//            // create default directory
//            try {
//                smh.createDirectory(defaultDirectory)
//            } catch (e: SMHException) {
//                Assert.assertTrue(e.statusCode == 409)
//            }
//
//            try {
//                smh.createDirectory(assistDirectory)
//            } catch (e: SMHException) {
//            }
//
//            val meta = mapOf("date" to "2021-1-1")
//            // single upload
//            val initUpload = smh.initUpload(
//                name = name,
//                dir = defaultDirectory,
//                meta = meta
//            )
//            val eTag = smh.upload(initUpload, uri)
//            Assert.assertNotNull(eTag)
//            val confirm = smh.confirmUpload(initUpload.confirmKey)
//            Assert.assertNotNull(confirm.key)
//            Assert.assertEquals(confirm.fileName, name)
//
//            // list again
//            var directoryContents = smh.listAll(dir = defaultDirectory)
//            Assert.assertNotNull(directoryContents.contents.find {
//                it.name == "${defaultDirectory.path}/$name"
//            })
//
//            // 生成缩略图
//            val previewThumbnail = smh.getThumbnail(name = directoryContents.contents.first().name, size = 50)
//            Assert.assertNotNull(previewThumbnail)
//            Assert.assertNotNull(previewThumbnail.location)
//
//            // move file
//            val newName = "target_${System.currentTimeMillis()}.jpg"
//            val content = directoryContents.contents.first()
//            val renameFileResponse = smh.renameFile(targetDir = assistDirectory, targetName = newName, sourceName = content.name)
//            Assert.assertNotNull(renameFileResponse)
//            Assert.assertNotNull(renameFileResponse.path)
//
//            smh.renameFile(targetName = content.name, sourceDir = assistDirectory, sourceName = newName)
//
//            // move targetDir not exist
//            try {
//                val targetDirNotExistResponse = smh.renameFile(targetDir = Directory("not_exist"), targetName = newName, sourceName = content.name)
//            } catch (e: SMHException) {
//                Assert.assertEquals(e.statusCode, 404)
//            }
//
//            // head file
//            val headFileResponse = smh.headFile(name, dir = defaultDirectory)
//            Assert.assertNotNull(headFileResponse)
//            Assert.assertNotNull(headFileResponse.contentType)
//            Assert.assertNotNull(headFileResponse.size)
//            Assert.assertNotNull(headFileResponse.creationTime)
//            Assert.assertNotNull(headFileResponse.eTag)
//            Assert.assertNotNull(headFileResponse.crc64)
//            Assert.assertNotNull(headFileResponse.type)
////            meta.forEach { (t, u) ->
////                Assert.assertNotNull(headFileResponse.metas?.get(t))
////                Assert.assertTrue(headFileResponse.metas?.get(t) == u)
////            }
//
//            // head file not exist
//            try {
//                smh.headFile("not_exist", defaultDirectory)
//            } catch (e: SMHException) {
//                Assert.assertEquals(e.statusCode, 404)
//            }
//
//            // download after rename
////            val initDownload = smh.initDownload(name, dir = defaultDirectory)
////            // val initDownload =  smh.initDownload("ori2.jpg", dir = defaultDirectory)
////            meta.forEach { (t, u) ->
////                Assert.assertNotNull(initDownload.metaData?.get(t))
////                Assert.assertTrue(initDownload.metaData?.get(t)?.get(0) == u)
////            }
//        }
//    }




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

//    @Test
//    fun testDownload() {
//        runBlocking {
//            val directoryContents = smh.listAll(dir = defaultDirectory)
//            val assets = directoryContents.contents
//            Assert.assertTrue(assets.count() > 0)
//            val download = assets[Random.nextInt(0, assets.count())]
//
//            val initDownload = smh.initDownload(
//                name = download.name
//            )
//            val rawName = defaultDirectory.path?.let {
//                download.name.replace("${it}/", "")
//            } ?: download.name
//            val fileName = "${System.currentTimeMillis()}_$rawName"
//            val url = requireNotNull(initDownload.url)
//            val metadata = requireNotNull(initDownload.metaData)
//            val contentUri = requireNotNull(MSHelper.createNewPendingAsset(
//                context,
//                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//                assetName = fileName,
//            ))
//
//            try {
//                smh.download(url, contentUri = contentUri)
//                MSHelper.endAssetPending(context, contentUri)
//
//                val (size, name, path) = requireNotNull(MSHelper.getResourceInfo(context, contentUri))
//                Assert.assertEquals(name, fileName)
//            } finally {
//                // delete file
//                MSHelper.removeResourceUri(context, contentUri)
//            }
//
//        }
//    }

    @Test
    fun testSMHList() {
        runBlocking {
            val remoteDirs = smh.listAll(Directory("AndroidUT"))
            remoteDirs.contents.forEach {
                Log.i("listAll", it.metaData?.entries?.joinToString())
            }
        }
    }

    @Test
    fun testSMHListRecycled() {
        runBlocking {
            val remoteDirs = smh.listRecycledWithMarker(null, 50)
            remoteDirs.contents.forEach {
                Log.i("listAll", it.name)
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


    private suspend fun uploadFile(key: String, size: Long): UploadFileResult {

        val file = File.createTempFile("read", ".txt")
        createFile(file, size)
        val uploadFileRequest = UploadFileRequest(
            key = key,
            Uri.fromFile(file)
        )

        val uploadTask = SMHUploadTask(
            context,
            smh,
            uploadFileRequest
        )

        uploadTask.start()

        return uploadTask.getResultOrThrow() as UploadFileResult
    }


    /**
     * 获取缩略图
     */
    @Test
    fun testGetThumbnail() {
        runBlocking {
            val result = smh.getThumbnail(
                name = "img.jpeg",
                dir = Directory("ut"),
//                widthSize = 100,
//                heightSize = 100
//                scale = 50
//                size = 100,
//                purpose = "list"
            )
            Log.d(TAG, "location: ${result.location}")
            Assert.assertTrue(result.location?.isNotEmpty() == true)
        }
    }

    @Test
    fun testSearch() {
        runBlocking {
            val result = smh.initSearch(
                searchTypes = listOf(SearchType.Video),
                keyword = "a",
                orderBy = OrderType.NAME,
                orderByType = OrderDirection.ASC
            )
            Log.d(TAG, "searchId: ${result.searchId}")
            Assert.assertTrue(true)
        }
    }

    @Test
    fun testOfficeFile() {
        runBlocking {
            val result = smh.createFileFromTemplate(
                name = "test哈.docx",
                dir = Directory("AndroidUT"),
                mapOf("meta1" to "meta1Value","meta2" to "meta2Value","meta3" to "meta3Value"),
                CreateFileFromTemplateRequest("word.docx")
            )
            Log.d(TAG, result.toString())

            val editResult = smh.officeEditFile(
                name = "test哈.docx",
                dir = Directory("AndroidUT")
            )
            Log.d(TAG, "html: $editResult")
            Assert.assertTrue(true)
        }
    }

    @Test
    fun testGetSpaceFileCount() {
        runBlocking {
            val result = smh.getSpaceFileCount()
            Log.d(TAG, "fileNum: ${result.fileNum} dirNum: ${result.dirNum} recycledFileNum: ${result.recycledFileNum} recycledDirNum: ${result.recycledDirNum} historyFileNum: ${result.historyFileNum}")
            Assert.assertTrue(true)
        }
    }

    @Test
    fun testRecentlyUsedFile() {
        runBlocking {
            val recentlyUsedFile = smh.recentlyUsedFile(type = listOf("text"))
            recentlyUsedFile.contents.forEach {
                Log.i("recentlyUsedFile", it.name)
            }
            val inodeInfo = smh.getINodeInfo(recentlyUsedFile.contents[0].inode)
            Log.i("inodeInfo", inodeInfo.path.toString())
            Assert.assertTrue(true)
        }
    }
}