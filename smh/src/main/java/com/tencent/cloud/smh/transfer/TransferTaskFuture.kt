package com.tencent.cloud.smh.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext

class TransferTaskFuture internal constructor(
    private val mTransferTask: SMHTransferTask,
    private val mContext: CoroutineContext,
    private val mScope: CoroutineScope,
){


    fun start(): CompletableFuture<Unit> {
        return call {
            mTransferTask.start()
        }
    }

    fun resume(): CompletableFuture<Unit> {
        return call {
            mTransferTask.resume()
        }
    }

    private inline fun <T> call(
        crossinline action: suspend () -> T
    ): CompletableFuture<T> {
        return mScope.future(mContext) {
            action()
        }
    }
}