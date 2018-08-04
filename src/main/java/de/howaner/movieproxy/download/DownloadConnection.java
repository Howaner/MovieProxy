package de.howaner.movieproxy.download;

import com.google.common.net.HttpHeaders;
import de.howaner.movieproxy.Constants;
import de.howaner.movieproxy.ProxyApplication;
import de.howaner.movieproxy.util.FileInformation;
import de.howaner.movieproxy.util.HttpUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import javax.net.ssl.SSLEngine;
import lombok.Getter;

public class DownloadConnection extends SimpleChannelInboundHandler<HttpObject> {
	@Getter private volatile boolean closed = false;
	@Getter private final long creationTime = System.currentTimeMillis();
	private Channel channel;

	private final Download download;
	private OffsetMap.OffsetEntry offsetEntry;
	@Getter private long offset;
	@Getter private long maxOffset;

	@Getter private boolean connected = false;

	public DownloadConnection(Download download, long offset, long maxOffset) {
		this.download = download;
		this.offset = offset;
		this.maxOffset = maxOffset;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		this.channel = ctx.channel();

		if (this.closed) {
			this.channel.close();
			return;
		}

		this.download.log("Connected to download server, now send download request ...");
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, this.download.getHttpFile().getMethod(), this.download.getHttpFile().getPath());
		request.headers().set(HttpHeaders.USER_AGENT, Constants.USER_AGENT);
		request.headers().set(HttpHeaders.CONNECTION, "keep-alive");
		request.headers().set(HttpHeaders.HOST, this.download.getHttpFile().getHost());

		if (this.offset != 0L && this.maxOffset != 0L) {
			request.headers().set(HttpHeaders.RANGE, "bytes=" + this.offset + "-" + this.maxOffset);
		} else if (this.offset != 0L) {
			request.headers().set(HttpHeaders.RANGE, "bytes=" + this.offset + "-");
		}

		this.channel.writeAndFlush(request);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		this.close();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		this.download.log("Exception occurred at file download", cause);
		this.closeWithError();
	}

	public void connect() {
		boolean ssl = this.download.getHttpFile().getProtocol().equals("https");
		int port = ssl ? 443 : 80;

		String host = this.download.getHttpFile().getHost();
		{
			int pointIndex = host.lastIndexOf(':');
			if (pointIndex != -1) {
				try {
					port = Integer.parseInt(host.substring(pointIndex + 1));
					host = host.substring(0, pointIndex);
				} catch (NumberFormatException ex) {}
			}
		}
		final String fHost = host;

		new Bootstrap()
				.group(ProxyApplication.getInstance().getDownloadManager().getEventLoop())
				.channel(Epoll.isAvailable() ? EpollSocketChannel.class : NioSocketChannel.class)
				.handler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel channel) throws Exception {
							ChannelPipeline pipe = channel.pipeline();
							if (ssl) {
								SSLEngine engine = SslContext.newClientContext().newEngine(channel.alloc(), fHost, 443);
								pipe.addLast("ssl", new SslHandler(engine));
							}

							pipe.addLast("codec", new HttpClientCodec());
							pipe.addLast("handler", DownloadConnection.this);
						}
					})
				.connect(host, port);
	}

	public void close() {
		if (this.closed)
			return;

		this.closed = true;
		if (this.channel != null)
			this.channel.close();

		boolean lock = false;
		try {
			lock = ProxyApplication.getInstance().getDownloadManager().getDownloadsLock().writeLock().tryLock();
			if (this.download.getDlConnection() == this)
				this.download.disposeDownloadConnection();
		} finally {
			if (lock)
				ProxyApplication.getInstance().getDownloadManager().getDownloadsLock().writeLock().unlock();
		}
	}

	public void closeWithError() {
		ProxyApplication.getInstance().getDownloadManager().cancelDownload(this.download);
		this.close();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
		if (this.closed)
			return;

		if (msg instanceof HttpResponse) {
			HttpResponse res = (HttpResponse) msg;
			if (res.status() != HttpResponseStatus.OK && res.status() != HttpResponseStatus.PARTIAL_CONTENT) {
				this.download.log("Received invalid http response from download server: {} ({})", res.status().code(), res.status().codeAsText());
				this.closeWithError();
				return;
			}

			long offset = HttpUtils.readOffset(res);
			if (offset != this.offset)
				this.download.log("Download server returned other offset than expected (expected: {}, get: {})", this.offset, offset);
			this.offset = offset;

			if (this.download.getFileInfo() == null) {
				String contentLengthStr = res.headers().get(HttpHeaders.CONTENT_LENGTH);
				this.download.setFileInfo(new FileInformation(res.headers().get(HttpHeaders.CONTENT_TYPE), Long.parseLong(contentLengthStr) + this.offset));
			}

			this.offsetEntry = this.download.getOffsetMap().createEntry(this.offset);

			this.connected = true;
			this.download.getCallbacks().forEach(c -> c.getRequestCallback().onStart(this.download.getFileInfo()));
			this.download.log("Received response from download server, now start to download :)");
			return;
		}

		if (msg instanceof HttpContent) {
			HttpContent content = (HttpContent) msg;

			byte[] bytes = new byte[content.content().readableBytes()];
			content.content().readBytes(bytes);
			this.download.writeBytes(this.offset, bytes);

			this.offset += bytes.length;
			this.offsetEntry.end = this.offset;

			if (this.maxOffset != 0L && this.offset >= this.maxOffset)
				this.close();
		}

		if (msg instanceof LastHttpContent) {
			this.close();
		}
	}

}
