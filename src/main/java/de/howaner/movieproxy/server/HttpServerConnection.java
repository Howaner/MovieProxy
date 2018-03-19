package de.howaner.movieproxy.server;

import de.howaner.movieproxy.HttpConnection;
import de.howaner.movieproxy.ProxyApplication;
import de.howaner.movieproxy.util.CloseReason;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.IOException;
import lombok.Getter;

public class HttpServerConnection extends ChannelInboundHandlerAdapter {
	private HttpConnection connection;
	@Getter private Channel channel;

	public HttpServerConnection() {
		this.connection = new HttpConnection(this);
	}

	public void send(Object obj) {
		this.channel.writeAndFlush(obj);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		this.channel = ctx.channel();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		this.connection.close(CloseReason.StreamClosed);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		ProxyApplication.getInstance().getLogger().error("Exception from http server connection", cause);
		this.connection.close(CloseReason.Error);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws IOException {
		this.connection.receivedFromServer(msg);
	}

}
