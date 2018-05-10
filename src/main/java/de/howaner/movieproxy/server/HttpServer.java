package de.howaner.movieproxy.server;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.howaner.movieproxy.ProxyApplication;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.Getter;

public class HttpServer {
    @Getter
    private ChannelFuture server;
    private EventLoopGroup eventLoop;

    public void startServer(int port) {
        this.eventLoop = new NioEventLoopGroup(2, new ThreadFactoryBuilder().setNameFormat("Http Server").setDaemon(true).build());

        ProxyApplication.getInstance().getLogger().info("Start http server at 0.0.0.0:" + port + " ...");
        try {
            ServerBootstrap bootstrap;
            if (Epoll.isAvailable()) {
                bootstrap = new ServerBootstrap()
                        .group(this.eventLoop)
                        .channel(EpollServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel channel) throws Exception {
                                ChannelPipeline pipe = channel.pipeline();
                                pipe.addLast("codec", new HttpServerCodec());
                                pipe.addLast("handler", new HttpServerConnection());
                            }
                        });
            } else {
                bootstrap = new ServerBootstrap()
                        .group(this.eventLoop)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel channel) throws Exception {
                                ChannelPipeline pipe = channel.pipeline();
                                pipe.addLast("codec", new HttpServerCodec());
                                pipe.addLast("handler", new HttpServerConnection());
                            }
                        });

            }
            this.server = bootstrap.bind(port).syncUninterruptibly();
        } catch (Exception ex) {
            ProxyApplication.getInstance().getLogger().fatal("Can't start http server", ex);
        }
    }

    public void stopServer() {
        if (this.server == null)
            return;

        try {
            ProxyApplication.getInstance().getLogger().info("Stop http server ...");
            this.server.channel().close().syncUninterruptibly();
            this.eventLoop.shutdown();
        } catch (Exception ex) {
        }
    }

}
