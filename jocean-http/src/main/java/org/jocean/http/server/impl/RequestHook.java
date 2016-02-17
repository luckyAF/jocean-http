package org.jocean.http.server.impl;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jocean.http.server.HttpServer.HttpTrade;
import org.jocean.idiom.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;

final class RequestHook implements Observer<HttpObject>, OutputChannel {
    
    private static final Logger LOG =
            LoggerFactory.getLogger(RequestHook.class);
    
    /**
     * 
     */
    private final DefaultHttpServer _defaultHttpServer;
    private final AtomicBoolean _requestCompleted = new AtomicBoolean(false);
    private final AtomicBoolean _isKeepAlive = new AtomicBoolean(false);
    private final AtomicBoolean _isRecycled = new AtomicBoolean(false);
    private final Subscriber<? super HttpTrade> _subscriber;
    private final Channel _channel;
    Subscription _removeHandlers;

    RequestHook(
            DefaultHttpServer defaultHttpServer, final Channel channel, 
            final Subscriber<? super HttpTrade> subscriber) {
        this._defaultHttpServer = defaultHttpServer;
        this._channel = channel;
        this._subscriber = subscriber;
    }

    @Override
    public void onCompleted() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("inner request onCompleted({})");
        }
        this._requestCompleted.set(true);
    }

    @Override
    public void onError(final Throwable e) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("inner request onError({})", 
                    ExceptionUtils.exception2detail(e));
        }
        onTradeFinished(false);
    }

    @Override
    public void onNext(final HttpObject httpobj) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("inner request onNext({})", httpobj);
        }
        if (httpobj instanceof HttpRequest) {
            this._isKeepAlive.set(HttpHeaders.isKeepAlive((HttpRequest)httpobj));
        }
    }
    
    @Override
    public synchronized void output(final Object msg) {
        if ( !this._isRecycled.get()) {
            this._channel.write(ReferenceCountUtil.retain(msg));
        } else {
            LOG.warn("output msg{} on recycled channel({})",
                msg, _channel);
        }
    }

    @Override
    public synchronized void onTradeFinished(final boolean isResponseCompleted) {
        if (this._isRecycled.compareAndSet(false, true)) {
            //  reference: https://github.com/netty/netty/commit/5112cec5fafcec8724b2225507da33bbb9bc47f3
            //  Detail:
            //  Bypass the encoder in case of an empty buffer, so that the following idiom works:
            //
            //     ch.write(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            //
            // See https://github.com/netty/netty/issues/2983 for more information.
            if (null != this._removeHandlers) {
                //  TODO, unsubscribe execute in eventloop?
                // RxNettys.removeHandlersSubscription(channel, diff.call());
                this._removeHandlers.unsubscribe();
            }
            
            if (this._requestCompleted.get() 
                && isResponseCompleted 
                && this._isKeepAlive.get() 
                && !this._subscriber.isUnsubscribed()) {
                this._channel.flush();
                if (this._channel.eventLoop().inEventLoop()) {
                    this._subscriber.onNext(this._defaultHttpServer.createHttpTrade(this._channel, this._subscriber));
                } else {
                    this._channel.eventLoop().submit(new Runnable() {
                        @Override
                        public void run() {
                            _subscriber.onNext(RequestHook.this._defaultHttpServer.createHttpTrade(_channel, _subscriber));
                        }});
                }
            } else {
                this._channel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            LOG.warn("onResponseCompleted on recycled channel({})", _channel);
        }
    }
}