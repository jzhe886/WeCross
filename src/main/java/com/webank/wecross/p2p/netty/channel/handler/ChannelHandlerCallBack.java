package com.webank.wecross.p2p.netty.channel.handler;

import com.webank.wecross.p2p.netty.Connections;
import com.webank.wecross.p2p.netty.common.Host;
import com.webank.wecross.p2p.netty.common.Utils;
import com.webank.wecross.p2p.netty.message.MessageCallBack;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import java.security.Principal;
import java.security.PublicKey;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class ChannelHandlerCallBack {

    private static Logger logger = LoggerFactory.getLogger(ChannelHandlerCallBack.class);

    private ThreadPoolTaskExecutor threadPool = null;

    private Connections connections;
    private MessageCallBack callBack;

    public MessageCallBack getCallBack() {
        return callBack;
    }

    public void setCallBack(MessageCallBack callBack) {
        this.callBack = callBack;
    }

    public Connections getConnections() {
        return connections;
    }

    public void setConnections(Connections connections) {
        this.connections = connections;
    }

    public ThreadPoolTaskExecutor getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ThreadPoolTaskExecutor threadPool) {
        this.threadPool = threadPool;
    }

    private String bytesToHex(byte[] hashInBytes) {

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hashInBytes.length; i++) {
            sb.append(Integer.toString((hashInBytes[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public PublicKey fetchCertificate(ChannelHandlerContext ctx) throws SSLPeerUnverifiedException {
        SslHandler sslhandler = (SslHandler) ctx.channel().pipeline().get(SslHandler.class);

        logger.info(String.valueOf(ctx.channel().pipeline().names()));

        X509Certificate cert = sslhandler.engine().getSession().getPeerCertificateChain()[0];
        PublicKey publicKey = cert.getPublicKey();
        Principal principal = cert.getSubjectDN();

        logger.info(
                " algorithm: {}, format: {}, class name: {}",
                publicKey.getAlgorithm(),
                publicKey.getFormat(),
                publicKey.getClass().getName());
        logger.info(
                " encoded: {}, hex encoded: {}",
                publicKey.getEncoded(),
                bytesToHex(publicKey.getEncoded()));
        logger.info(
                " principal name: {} ,principal class name: {}",
                principal.getName(),
                principal.getClass().getName());

        return publicKey;
    }

    public void onConnect(ChannelHandlerContext ctx, boolean connectToServer)
            throws SSLPeerUnverifiedException {

        Host host = Utils.channelContextPeerHost(ctx);

        // peer node ssl pub
        String nodeID = bytesToHex(fetchCertificate(ctx).getEncoded()).substring(48);

        // set nodeID to channel attribute map
        ctx.channel().attr(AttributeKey.valueOf("NodeID")).set(nodeID);

        getConnections().addChannelHandler(nodeID, ctx, connectToServer);

        logger.info(
                " host {} connect success, nodeID: {}, ctx: {}",
                host,
                nodeID,
                System.identityHashCode(ctx));
    }

    public void onDisconnect(ChannelHandlerContext ctx) {
        Host host = Utils.channelContextPeerHost(ctx);

        String nodeID = (String) ctx.channel().attr(AttributeKey.valueOf("NodeID")).get();
        if (null != nodeID) {
            getConnections().removeChannelHandler(nodeID, ctx);
            logger.info(
                    " disconnect, host: {}, nodeID: {}, ctx: {}",
                    host,
                    nodeID,
                    System.identityHashCode(ctx));
        } else {
            logger.warn(
                    " disconnect, nodeID null handshake not success, host: {}, ctx: {}",
                    host,
                    System.identityHashCode(ctx));
        }
    }

    public void onMessage(ChannelHandlerContext ctx, ByteBuf message) {
        /*
         use thread pool first onMessage may block
        */
        if (threadPool == null) {
            callBack.onMessage(ctx, message);
        } else {
            threadPool.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            callBack.onMessage(ctx, message);
                        }
                    });
        }
    }
}
