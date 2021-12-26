package com.virjar.hermes.hermesagent.host.netty;

import com.google.common.base.Charsets;
import com.virjar.hermes.hermesagent.util.Constant;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

/**
 * @author by fury.
 *         version 2018/9/18.
 */
public class MessageDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf,
                          List<Object> list) throws Exception {

        //这个HEAD_LENGTH是我们用于表示头长度的字节数
        if (byteBuf.readableBytes() < Constant.HEAD_LENGTH) {
            return;
        }

        //我们标记一下当前的readIndex的位置
        byteBuf.markReaderIndex();

        // 读取传送过来的消息的长度。ByteBuf 的readInt()方法会让他的readIndex增加4
        int dataLength = byteBuf.readInt();

        // 我们读到的消息体长度为0，这是不应该出现的情况，这里出现这情况，关闭连接。
        if (dataLength < 0) {
            channelHandlerContext.close();
        }

        //读到的消息体长度如果小于我们传送过来的消息长度，则resetReaderIndex. 这个配合markReaderIndex使用的。把readIndex重置到mark的地方
        if (byteBuf.readableBytes() < dataLength) {
            byteBuf.resetReaderIndex();
            return;
        }

        byte[] body = new byte[dataLength];
        byteBuf.readBytes(body);

        //将byte数据转化为我们需要的对象
        //BaseMsg baseMsg = JSON.parseObject(body, BaseMsg.class);
        //list.add(baseMsg);
        list.add(new String(body, Charsets.UTF_8));
    }
}
