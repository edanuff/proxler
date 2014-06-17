package proxler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class ProxlerInitializer extends ChannelInitializer<SocketChannel> {

    private final String remoteHost;
    private final int remotePort;

    public ProxlerInitializer(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        // ch.pipeline().addLast(
        ChannelPipeline p = ch.pipeline();
        // new LoggingHandler(LogLevel.INFO),
        // new HttpRequestDecoder(),
        // new HttpResponseEncoder(),
        // new ProxlerFrontendHandler);
        p.addLast(new LoggingHandler(LogLevel.INFO));
        p.addLast(new HttpRequestDecoder());
        // Uncomment the following line if you don't want to handle HttpChunks.
        // p.addLast(new HttpObjectAggregator(1048576));
        // p.addLast(new HttpResponseEncoder());
        // Remove the following line if you don't want automatic content
        // compression.
        // p.addLast(new HttpContentCompressor());
        p.addLast(new ProxlerFrontendHandler(remoteHost, remotePort));
    }
}