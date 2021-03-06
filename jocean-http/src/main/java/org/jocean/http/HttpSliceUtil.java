package org.jocean.http;

import org.jocean.http.util.RxNettys;
import org.jocean.idiom.DisposableWrapper;
import org.jocean.idiom.DisposableWrapperUtil;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObject;
import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Func1;

public class HttpSliceUtil {
    public static <T extends HttpMessage> Transformer<HttpSlice, T> extractHttpMessage() {
        return new Transformer<HttpSlice, T>() {
            @Override
            public Observable<T> call(final Observable<HttpSlice> slices) {
                return slices.flatMap(new Func1<HttpSlice, Observable<? extends DisposableWrapper<? extends HttpObject>>>() {
                            @Override
                            public Observable<? extends DisposableWrapper<? extends HttpObject>> call(final HttpSlice slice) {
                                return slice.element();
                            }
                        }).map(DisposableWrapperUtil.<HttpObject>unwrap()).first().map(new Func1<HttpObject, T>() {
                            @SuppressWarnings("unchecked")
                            @Override
                            public T call(final HttpObject httpobj) {
                                if (httpobj instanceof HttpMessage) {
                                    return (T) httpobj;
                                } else {
                                    throw new RuntimeException("First HttpObject is not HttpMessage.");
                                }
                            }
                        });
            }
        };
    }

    public static Observable<HttpSlice> single(final Observable<? extends DisposableWrapper<? extends HttpObject>> element) {
        return Observable.<HttpSlice>just(new HttpSlice() {
            @Override
            public Observable<? extends DisposableWrapper<? extends HttpObject>> element() {
                return element;
            }
            @Override
            public void step() {}});
    }

    public static Func1<HttpSlice, HttpSlice> transformElement(
            final Transformer<DisposableWrapper<? extends HttpObject>, DisposableWrapper<? extends HttpObject>> transformer) {
        return new Func1<HttpSlice, HttpSlice>() {
            @Override
            public HttpSlice call(final HttpSlice slice) {
                return transformElement(slice, transformer);
            }
        };
    }

    public static HttpSlice transformElement(final HttpSlice slice,
            final Transformer<DisposableWrapper<? extends HttpObject>, DisposableWrapper<? extends HttpObject>> transformer) {
        return new HttpSlice() {
            @Override
            public Observable<DisposableWrapper<? extends HttpObject>> element() {
                return slice.element().compose(transformer);
            }
            @Override
            public void step() {
                slice.step();
            }
        };
    }

    private final static Func1<HttpSlice, ByteBufSlice> _HSTOBBS = new Func1<HttpSlice, ByteBufSlice>() {
        @Override
        public ByteBufSlice call(final HttpSlice slice) {
            final Observable<DisposableWrapper<ByteBuf>> cached =
                    slice.element().flatMap(RxNettys.message2body()).cache();
            return new ByteBufSlice() {
                @Override
                public String toString() {
                    return new StringBuilder().append("ByteBufSlice [from ")
                            .append(slice).append("]").toString();
                }
                @Override
                public Observable<DisposableWrapper<ByteBuf>> element() {
                    return cached;
                }
                @Override
                public void step() {
                    slice.step();
                }};
        }
    };

    public static Func1<HttpSlice, ByteBufSlice> hs2bbs() {
        return _HSTOBBS;
    }
}
