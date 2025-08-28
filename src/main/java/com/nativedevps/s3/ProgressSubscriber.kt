package com.nativedevps.s3

import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import software.amazon.awssdk.core.async.AsyncRequestBody

class ProgressSubscriber(
    private val delegate: Subscriber<in ByteBuffer>,
    private val onProgress: (Long, Long) -> Unit
) : Subscriber<ByteBuffer> {

    private val totalBytes = AtomicLong(0)
    private val bytesWritten = AtomicLong(0)
    private var subscription: Subscription? = null

    override fun onSubscribe(s: Subscription) {
        subscription = s
        delegate.onSubscribe(s)
    }

    override fun onNext(byteBuffer: ByteBuffer) {
        bytesWritten.addAndGet(byteBuffer.remaining().toLong())
        onProgress(bytesWritten.get(), totalBytes.get())
        delegate.onNext(byteBuffer)
    }

    override fun onError(t: Throwable) {
        delegate.onError(t)
    }

    override fun onComplete() {
        delegate.onComplete()
    }
}

// Progress tracking request body
class ProgressAsyncRequestBody(
    private val delegate: AsyncRequestBody,
    private val onProgress: (Long, Long) -> Unit
) : AsyncRequestBody by delegate {

    override fun subscribe(subscriber: org.reactivestreams.Subscriber<in ByteBuffer>) {
        delegate.subscribe(ProgressSubscriber(subscriber, onProgress))
    }
}