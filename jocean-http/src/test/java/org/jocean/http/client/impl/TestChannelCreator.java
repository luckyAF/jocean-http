package org.jocean.http.client.impl;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jocean.http.util.RxNettys.DoOnUnsubscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ChannelFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import rx.Single;
import rx.SingleSubscriber;
import rx.subscriptions.Subscriptions;

public class TestChannelCreator implements ChannelCreator {

    private static final Logger LOG =
            LoggerFactory.getLogger(TestChannelCreator.class);
    
    final class TestChannel extends LocalChannel {
        
        private final AbstractUnsafe _unsafe0 = super.newUnsafe();
        @Override
        protected AbstractUnsafe newUnsafe() {
            return new AbstractUnsafe() {
                @Override
                public void connect(final SocketAddress remoteAddress,
                        final SocketAddress localAddress, 
                        final ChannelPromise promise) {
                    if (null!=_connectException) {
                        promise.tryFailure(_connectException);
                    }
                    else {
                        _unsafe0.connect(remoteAddress, localAddress, promise);
                    }
                }};
        }
        
        @Override
        protected void doWrite(final ChannelOutboundBuffer in) throws Exception {
            if (null!=_writeException) {
                throw _writeException;
            }
            super.doWrite(in);
        }
        
        @Override
        public ChannelFuture close() {
            if ( _isClosed.compareAndSet(false, true)) {
                _closed.countDown();
            }
            return super.close();
        }
        
        public void awaitClosed() throws InterruptedException {
            _closed.await();
        }
        
        public void awaitClosed(final long timeout) throws InterruptedException {
            _closed.await(timeout, TimeUnit.SECONDS);
        }
        
        public void assertClosed(final long timeout) throws InterruptedException {
            awaitClosed(timeout);
            if (!this._isClosed.get()) {
                throw new AssertionError("Channel Not Close");
            }
        }
        
        public void assertNotClose(final long timeout) throws InterruptedException {
            awaitClosed(timeout);
            if (this._isClosed.get()) {
                throw new AssertionError("Channel Closed");
            }
        }
        
        private final CountDownLatch _closed = new CountDownLatch(1);
        private final AtomicBoolean _isClosed = new AtomicBoolean(false);
    }
    
    @Override
    public void close() throws IOException {
        // ignore
    }

    @Override
    public Single<? extends ChannelFuture> newChannel(final DoOnUnsubscribe doOnUnsubscribe) {
        return Single.create(new Single.OnSubscribe<ChannelFuture>() {
            @Override
            public void call(final SingleSubscriber<? super ChannelFuture> subscriber) {
                if (!subscriber.isUnsubscribed()) {
                    final ChannelFuture future = _bootstrap.register();
                    if ( LOG.isDebugEnabled() ) {
                        LOG.debug("create new test channel: {}", future.channel());
                    }
                    _channels.add((TestChannel)future.channel());
                    doOnUnsubscribe.call(Subscriptions.from(future));
                    subscriber.onSuccess(future);
                }
            }});
    }
    
    public List<TestChannel> getChannels() {
        return this._channels;
    }
    
    public TestChannelCreator setWriteException(final Exception e) {
        this._writeException = e;
        return this;
    }
    
    public TestChannelCreator setConnectException(final Exception e) {
        this._connectException = e;
        return this;
    }
    
    public void reset() {
        this._bootstrap = new Bootstrap()
            .group(new LocalEventLoopGroup(1))
            .channelFactory(new ChannelFactory<TestChannel>() {
                        @Override
                        public TestChannel newChannel() {
                            return new TestChannel();
                        }})
            .handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(final Channel channel) throws Exception {
                    LOG.info("processing initChannel for {}", channel);
                }});
    }
    
    public TestChannelCreator() {
        reset();
    }
    
    private Exception _writeException = null;
    private Exception _connectException = null;
    
    private Bootstrap _bootstrap;
    
    private final List<TestChannel> _channels = new ArrayList<>();
}
