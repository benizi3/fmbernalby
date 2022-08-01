package com.koushikdutta.async;

import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.Multimap;

import java.nio.charset.Charset;

/**
 * 自动识别请求头的Content-Type，原生代码没有自动识别，会导致乱码
 */
public class ContentTypeEncodingEmitter extends FilteredDataEmitter {
    private Headers headers;

    public ContentTypeEncodingEmitter(Headers headers) {
        this.headers = headers;
    }

    @Override
    public String charset() {
        String targetCharset = super.charset();
        if (targetCharset != null && !targetCharset.isEmpty()) {
            return targetCharset;
        }
        Multimap mm = Multimap.parseSemicolonDelimited(headers.get("Content-Type"));
        String cs;
        if (mm != null && null != (cs = mm.getString("charset")) && Charset.isSupported(cs)) {
            return cs;
        }
        return null;
    }
}
