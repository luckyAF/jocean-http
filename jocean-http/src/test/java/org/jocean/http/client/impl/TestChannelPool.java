package org.jocean.http.client.impl;

import io.netty.channel.Channel;

import java.util.concurrent.CountDownLatch;

public class TestChannelPool extends DefaultChannelPool {

    public TestChannelPool(final ChannelCreator channelCreator, final int recycleChannelCount) {
        super(channelCreator);
        this._countdown = new CountDownLatch(recycleChannelCount);
    }

    @Override
    public void recycleChannel(final Channel channel) {
        try {
            super.recycleChannel(channel);
        } finally {
            this._countdown.countDown();
        }
    }
    
    public void awaitRecycleChannels() throws InterruptedException {
        this._countdown.await();
    }

    private final CountDownLatch _countdown;
}