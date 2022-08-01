package com.koushikdutta.async.parser;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.ThenCallback;
import com.koushikdutta.async.http.body.DocumentBody;
import com.koushikdutta.async.stream.ByteBufferListInputStream;

import org.w3c.dom.Document;

import java.lang.reflect.Type;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Created by koush on 8/3/13.
 */
public class DocumentParser implements AsyncParser<Document> {
    @Override
    public Future<Document> parse(DataEmitter emitter) {
        //from -> DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteBufferListInputStream(from))
        return new ByteBufferListParser().parse(emitter)
        .thenConvert(new ThenCallback<Document, ByteBufferList>() {
            @Override
            public Document then(ByteBufferList from) throws Exception {
                return  DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteBufferListInputStream(from));
            }
        });
    }

    @Override
    public void write(DataSink sink, Document value, CompletedCallback completed) {
        new DocumentBody(value).write(null, sink, completed);
    }

    @Override
    public Type getType() {
        return Document.class;
    }

    @Override
    public String getMime() {
        return "text/xml";
    }
}
