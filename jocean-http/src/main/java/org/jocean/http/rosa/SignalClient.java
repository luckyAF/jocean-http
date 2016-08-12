package org.jocean.http.rosa;

import java.lang.annotation.Annotation;

import org.jocean.http.Feature;
import org.jocean.http.rosa.impl.internal.Facades.JSONSource;
import org.jocean.http.rosa.impl.internal.Facades.MethodSource;
import org.jocean.http.rosa.impl.internal.Facades.PathSource;

import io.netty.util.CharsetUtil;
import rx.Observable;

public interface SignalClient {
    
    public class Attachment implements Feature {
        public Attachment(final String filename, final String contentType) {
            this.filename = filename;
            this.contentType = contentType;
        }
        
        public final String filename;
        public final String contentType;
        //  add direct content for test
    }
    
    public class UsingPath implements Feature, PathSource {
        public UsingPath(final String path) {
            this._path = path;
        }
        
        public String path() {
            return this._path;
        }
        
        private final String _path;
    }
    
    public class UsingMethod implements Feature, MethodSource {
        public UsingMethod(final Class<? extends Annotation> method) {
            this._method = method;
        }
        
        public Class<? extends Annotation> method() {
            return this._method;
        }
        
        private final Class<? extends Annotation> _method;
    }
    
    public class JSONContent implements Feature, JSONSource {
        public JSONContent(final String jsonAsString) {
            this._content = jsonAsString.getBytes(CharsetUtil.UTF_8);
        }
        
        public byte[] content() {
            return this._content;
        }
        
        private final byte[] _content;
    }
    
    public <RESP> Observable<? extends RESP> defineInteraction(
            final Object request, final Feature... features);
}
