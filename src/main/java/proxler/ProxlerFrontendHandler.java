package proxler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import proxler.ContainerManager.ContainerInfo;

public class ProxlerFrontendHandler extends ChannelInboundHandlerAdapter {

    private final Logger logger = LoggerFactory
            .getLogger(ProxlerFrontendHandler.class);
    private volatile Channel outboundChannel;

    public ProxlerFrontendHandler(String remoteHost, int remotePort) {
        logger.info("ProxlerFrontendHandler.<init>()");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        logger.info("ProxlerFrontendHandler.channelActive()");
        ctx.channel().read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        final Channel inboundChannel = ctx.channel();
        if (msg != null) {
            logger.info("ProxlerFrontendHandler.channelActive(): "
                    + msg.getClass().getName());
        }
        if ((outboundChannel == null) || !outboundChannel.isActive()) {
            if (msg instanceof HttpRequest) {
                final HttpRequest inbound_http_request = (HttpRequest) msg;
                final String inbound_path = inbound_http_request.getUri();
                logger.info("ProxlerFrontendHandler.channelRead(): Received the HTTP request for "
                        + inbound_path);

                String[] segments = (inbound_path.startsWith("/") ? inbound_path
                        .substring(1) : inbound_path).split("/");
                if (segments.length < 2) {
                    logger.info("Path not valid for proxy to container: "
                            + inbound_path);

                    sendNotFound(ctx);
                    return;
                }

                String id = segments[0] + "/" + segments[1];

                final ContainerInfo c = ContainerManager.INSTANCE
                        .getContainerLocation(id);
                if (c == null) {
                    logger.info("Container not found: " + id);
                    sendNotFound(ctx);
                    return;
                }

                final String proxied_path = inbound_path.substring(inbound_path
                        .indexOf(id) + id.length());

                logger.info("Proxing to container: " + id);
                logger.info("Proxing to path: " + proxied_path);

                // Start the connection attempt.
                Bootstrap b = new Bootstrap();
                b.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new ProxlerBackendInitializer(inboundChannel));
                ChannelFuture f = b.connect(c.host, c.port);
                outboundChannel = f.channel();
                f.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) {
                        if (future.isSuccess()) {
                            logger.info("ProxlerFrontendHandler.channelRead(): Sending the outbound HTTP request");
                            HttpRequest outbound_http_request = new DefaultFullHttpRequest(
                                    inbound_http_request.getProtocolVersion(),
                                    inbound_http_request.getMethod(),
                                    proxied_path);
                            outbound_http_request.headers().add(
                                    inbound_http_request.headers());
                            outbound_http_request.headers().set(
                                    HttpHeaders.Names.HOST, c.host);
                            outbound_http_request.headers().set(
                                    HttpHeaders.Names.CONNECTION,
                                    HttpHeaders.Values.CLOSE);
                            outbound_http_request.headers().set(
                                    HttpHeaders.Names.ACCEPT_ENCODING,
                                    HttpHeaders.Values.GZIP);

                            // Set some example cookies.
                            outboundChannel
                            .writeAndFlush(outbound_http_request)
                            .addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(
                                        ChannelFuture future) {
                                    if (future.isSuccess()) {
                                        // was able to flush out data,
                                        // start to read the next
                                        // chunk
                                        ctx.channel().read();
                                    } else {
                                        future.channel().close();
                                    }
                                }
                            });
                        } else {
                            // Close the connection if the connection attempt
                            // has failed.
                            logger.info("ProxlerFrontendHandler.channelRead(): Connection failed to backend host");
                        }
                    }
                });

            } else if ((outboundChannel != null) && outboundChannel.isActive()) {
                outboundChannel.writeAndFlush(msg).addListener(
                        new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) {
                                if (future.isSuccess()) {
                                    // was able to flush out data, start to read
                                    // the next chunk
                                    ctx.channel().read();
                                } else {
                                    future.channel().close();
                                }
                            }
                        });
            }
        }
    }

    public void sendNotFound(ChannelHandlerContext ctx) {
        HttpResponse http_response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);

        EmbeddedChannel ech = new EmbeddedChannel(new HttpResponseEncoder());
        ech.writeOutbound(http_response);
        ctx.channel().writeAndFlush(ech.readOutbound())
        .addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(
                    ChannelFutureListener.CLOSE);
        }
    }
}