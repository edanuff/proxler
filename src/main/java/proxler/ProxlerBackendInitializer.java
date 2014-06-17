package proxler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestEncoder;

public class ProxlerBackendInitializer extends
ChannelInitializer<SocketChannel> {

    Channel inboundChannel;

    public ProxlerBackendInitializer(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

        p.addLast(new HttpRequestEncoder());
        // p.addLast(new HttpResponseDecoder());

        // Remove the following line if you don't want automatic content
        // decompression.
        // p.addLast(new HttpContentDecompressor());

        // Uncomment the following line if you don't want to handle
        // HttpContents.
        // p.addLast(new HttpObjectAggregator(1048576));

        p.addLast(new ProxlerBackendHandler(inboundChannel));
    }
}