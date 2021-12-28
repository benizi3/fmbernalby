//package com.virjar.hermes.hermesagent.host.netty;
//
//import android.os.DeadObjectException;
//import android.os.RemoteException;
//import android.util.Log;
//
//import com.alibaba.fastjson.JSONObject;
//import com.google.common.collect.Maps;
//import com.virjar.hermes.hermesagent.bean.CommonRes;
//import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
//import com.virjar.hermes.hermesagent.hermes_api.Multimap;
//import com.virjar.hermes.hermesagent.hermes_api.aidl.IHookAgentService;
//import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
//import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;
//import com.virjar.hermes.hermesagent.host.http.HttpServer;
//import com.virjar.hermes.hermesagent.host.service.FontService;
//import com.virjar.hermes.hermesagent.util.CommonUtils;
//import com.virjar.hermes.hermesagent.util.Constant;
//
//import org.apache.commons.lang3.StringUtils;
//
//import java.util.Map;
//import java.util.concurrent.Executor;
//import java.util.concurrent.TimeUnit;
//
//import io.netty.bootstrap.Bootstrap;
//import io.netty.channel.Channel;
//import io.netty.channel.ChannelFuture;
//import io.netty.channel.ChannelFutureListener;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.ChannelInitializer;
//import io.netty.channel.ChannelOption;
//import io.netty.channel.ChannelPipeline;
//import io.netty.channel.SimpleChannelInboundHandler;
//import io.netty.channel.nio.NioEventLoopGroup;
//import io.netty.channel.socket.SocketChannel;
//import io.netty.channel.socket.nio.NioSocketChannel;
//import io.netty.handler.timeout.IdleStateEvent;
//import io.netty.handler.timeout.IdleStateHandler;
//
///**
// * @author by fury.
// *         version 2018/9/18.
// */
//public class NettyClientBootstrap {
//    private SocketChannel socketChannel;
//    private Bootstrap bootstrap;
//    private NioEventLoopGroup workGroup = null;
//
//    private FontService fontService;
//
//    private NettyClientBootstrap(Executor executor, FontService fontService) throws InterruptedException {
//        this.workGroup = new NioEventLoopGroup(0, executor);
//        this.fontService = fontService;
//        start();
//    }
//
//    public void start() {
//        bootstrap = new Bootstrap();
//        bootstrap.channel(NioSocketChannel.class)
//                .option(ChannelOption.SO_KEEPALIVE, true)
//                .group(workGroup)
//                .remoteAddress(Constant.serverHost, Constant.serverNettyPort)
//                .handler(new ChannelInitializer() {
//
//                    @Override
//                    protected void initChannel(Channel channel) throws Exception {
//                        ChannelPipeline p = channel.pipeline();
//                        p.addLast(new IdleStateHandler(20, 10, 0));
//                        p.addLast(new MessageDecoder());
//                        p.addLast(new MessageEncoder());
//                        //p.addLast(new NettyClientHandler(clientId));
//                        p.addLast(new SimpleChannelInboundHandler<String>() {
//                            @Override
//                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//                                if (evt instanceof IdleStateEvent) {
//                                    IdleStateEvent e = (IdleStateEvent) evt;
//                                    switch (e.state()) {
//                                        case WRITER_IDLE:
//                                            //PingMsg pingMsg = new PingMsg(clientId);
//                                            JSONObject pingMsg = new JSONObject();
//                                            pingMsg.put("requestType", "ping");
//                                            ctx.writeAndFlush(pingMsg.toJSONString());
//                                            break;
//                                        default:
//                                            break;
//                                    }
//                                }
//                            }
//
//                            @Override
//                            protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
//                                JSONObject serverRequest = JSONObject.parseObject(msg);
//                                String requestType = serverRequest.getString("requestType");
//                                if (requestType == null) {
//                                    return;
//                                }
//                                if (requestType.equalsIgnoreCase("pong")) {
//                                    return;
//                                }
//                                if (requestType.equalsIgnoreCase("invoke")) {
//                                    handleServerRequest(serverRequest.getString("requestBody"), serverRequest.getString("contentType"), ctx);
//                                }
//                            }
//                        });
//                    }
//                });
//        doConnect(Constant.serverHost, Constant.serverNettyPort);
//    }
//
//    private Map<String, String> determineInnerParam(String requestBody, String contentType) {
//        Map<String, String> result = Maps.newHashMap();
//        if (StringUtils.containsIgnoreCase(contentType, "application/x-www-form-urlencoded")) {
//            Multimap nameValuePairs = Multimap.parseQuery(requestBody);
//            result.put(Constant.invokePackage, nameValuePairs.getString(Constant.invokePackage));
//            result.put(Constant.invokeSessionID, nameValuePairs.getString(Constant.invokeSessionID));
//            result.put(Constant.invokeRequestID, nameValuePairs.getString(Constant.invokeRequestID));
//        } else {
//            JSONObject serverRequest = JSONObject.parseObject(requestBody);
//            result.put(Constant.invokePackage, serverRequest.getString(Constant.invokePackage));
//            result.put(Constant.invokeSessionID, serverRequest.getString(Constant.invokeSessionID));
//            result.put(Constant.invokeRequestID, serverRequest.getString(Constant.invokeRequestID));
//        }
//        return result;
//
//    }
//
//    private InvokeRequest buildInvokeRequest(String requestBody, Map<String, String> innerParam) {
//        String requestSession = innerParam.get(Constant.invokeSessionID);
//        if (StringUtils.isBlank(requestSession)) {
//            requestSession = CommonUtils.genRequestID();
//        }
//        if (!requestSession.startsWith("request_session_")) {
//            requestSession += "request_session_";
//        }
//        return new InvokeRequest(requestBody, fontService, requestSession);
//
//    }
//
//    private void handleServerRequest(String requestBody, String contentType, ChannelHandlerContext ctx) {
//        if (!StringUtils.containsIgnoreCase(contentType, "application/x-www-form-urlencoded")
//                && !StringUtils.containsIgnoreCase(contentType, "application/json")) {
//            ctx.writeAndFlush(JSONObject.toJSONString(CommonRes.failed("unknown request data format")));
//            return;
//        }
//
//        Map<String, String> innerParam = determineInnerParam(requestBody, contentType);
//        final String requestID = innerParam.get(Constant.invokeRequestID);
//        final String invokePackage = innerParam.get(Constant.invokePackage);
//        final IHookAgentService hookAgent = fontService.findHookAgent(invokePackage);
//        if (hookAgent == null) {
//            CommonRes failed = CommonRes.failed(Constant.status_service_not_available, Constant.serviceNotAvailableMessage, requestID);
//            ctx.writeAndFlush(JSONObject.toJSONString(failed));
//            return;
//        }
//        final InvokeRequest invokeRequest = buildInvokeRequest(requestBody, innerParam);
//
//        HttpServer.getInstance().getJ2Executor().getOrCreate(invokePackage, 2, 4)
//                .execute(new Runnable() {
//                             @Override
//                             public void run() {
//                                 InvokeResult invokeResult = null;
//                                 long invokeStartTimestamp = System.currentTimeMillis();
//                                 try {
//                                     APICommonUtils.requestLogI(invokeRequest, " startTime: " + invokeStartTimestamp + "  params:" + invokeRequest.getParamContent(false));
//                                     invokeResult = hookAgent.invoke(invokeRequest);
//                                     if (invokeResult == null) {
//                                         APICommonUtils.requestLogW(invokeRequest, " agent return null object");
//                                         // CommonUtils.sendJSON(response, CommonRes.failed("agent return null object"));
//                                         return;
//                                     }
//                                     if (invokeResult.getStatus() != InvokeResult.statusOK) {
//                                         APICommonUtils.requestLogW(invokeRequest, " return status not ok");
//                                         //  CommonUtils.sendJSON(response, CommonRes.failed(invokeResult.getStatus(), invokeResult.getTheData()));
//                                         return;
//                                     }
//                                     if (invokeResult.getDataType() == InvokeResult.dataTypeJson) {
//                                         //  CommonUtils.sendJSON(response, CommonRes.success(com.alibaba.fastjson.JSON.parse(invokeResult.getTheData())));
//                                     } else {
//                                         //  CommonUtils.sendJSON(response, CommonRes.success(invokeResult.getTheData()));
//                                     }
//                                 } catch (DeadObjectException e) {
//                                     APICommonUtils.requestLogW(invokeRequest, "service " + invokePackage + " dead ,offline it", e);
//                                     fontService.releaseDeadAgent(invokePackage);
//                                     // CommonUtils.sendJSON(response, CommonRes.failed(Constant.status_service_not_available, Constant.serviceNotAvailableMessage));
//                                 } catch (RemoteException e) {
//                                     APICommonUtils.requestLogW(invokeRequest, "remote exception", e);
//                                     // CommonUtils.sendJSON(response, CommonRes.failed(e));
//                                 } finally {
//                                     long endTime = System.currentTimeMillis();
//                                     APICommonUtils.requestLogI(invokeRequest, "invoke end time:" + endTime + " duration:" + ((endTime - invokeStartTimestamp) / 1000) + "s");
//                                     if (invokeResult != null) {
//                                         APICommonUtils.requestLogI(invokeRequest, " invoke result" + invokeResult.getTheData());
//                                         String needDeleteFile = invokeResult.needDeleteFile();
//                                         if (needDeleteFile != null) {
//                                             try {
//                                                 hookAgent.clean(needDeleteFile);
//                                             } catch (DeadObjectException e) {
//                                                 fontService.releaseDeadAgent(invokePackage);
//                                             } catch (RemoteException e) {
//                                                 APICommonUtils.requestLogW(invokeRequest, "remove temp file failed", e);
//                                             }
//                                         }
//                                     }
//                                 }
//                             }
//                         }
//                );
//    }
//
//    /**
//     * 建立连接，并自动重连.
//     */
//    private void doConnect(String host, int port) {
//        if (socketChannel != null && socketChannel.isActive()) {
//            return;
//        }
//
//        final int portConnect = port;
//        final String hostConnect = host;
//        ChannelFuture future = bootstrap.connect(host, port);
//        future.addListener(new ChannelFutureListener() {
//            @Override
//            public void operationComplete(ChannelFuture futureListener) throws Exception {
//                if (futureListener.isSuccess()) {
//                    socketChannel = (SocketChannel) futureListener.channel();
//                } else {
//                    Log.i("weijia", "Failed to connect to server, try connect after 10s");
//                    futureListener.channel().eventLoop().schedule(new Runnable() {
//                        @Override
//                        public void run() {
//                            doConnect(hostConnect, portConnect);
//                        }
//                    }, 10, TimeUnit.SECONDS);
//                }
//            }
//        });
//    }
//}
