package org.jocean.http;

import io.netty.handler.codec.http.HttpMessage;
import rx.Observable;

public interface FullMessage<M extends HttpMessage> {
    public M message();
    public Observable<? extends MessageBody> body();
}
