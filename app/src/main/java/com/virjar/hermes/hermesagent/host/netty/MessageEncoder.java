package com.virjar.hermes.hermesagent.host.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * @author by fury.
 *         version 2018/9/18.
 */
@ChannelHandler.Sharable
public class MessageEncoder extends MessageToByteEncoder<String> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, String baseMsg,
                          ByteBuf byteBuf) throws Exception {
        //将对象转换为byte
        byte[] body = baseMsg.getBytes();
        //读取消息的长度
        int dataLength = body.length;
        //先将消息长度写入，也就是消息头
        byteBuf.writeInt(dataLength);
        //消息体中包含我们要发送的数据
        byteBuf.writeBytes(body);
    }
}
