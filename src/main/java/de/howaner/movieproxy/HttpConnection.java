package de.howaner.movieproxy;

import com.google.common.base.Charsets;
import com.google.common.net.HttpHeaders;
import de.howaner.movieproxy.content.ContentReceiver;
import de.howaner.movieproxy.content.FileContentReceiver;
import de.howaner.movieproxy.content.RequestBytesCallback;
import de.howaner.movieproxy.dataresponse.DownloadsResponse;
import de.howaner.movieproxy.dataresponse.FoldersResponse;
import de.howaner.movieproxy.download.Download;
import de.howaner.movieproxy.exception.InvalidRequestException;
import de.howaner.movieproxy.server.HttpServerConnection;
import de.howaner.movieproxy.util.CloseReason;
import de.howaner.movieproxy.util.FileInformation;
import de.howaner.movieproxy.util.FilePath;
import de.howaner.movieproxy.util.HttpFile;
import de.howaner.movieproxy.util.HttpUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HttpConnection implements RequestBytesCallback {
	@Getter private final HttpServerConnection connection;
	@Getter private volatile boolean closed = false;

	@Getter private HttpRequest request;

	@Getter private ContentReceiver contentReceiver;
	@Getter private FileInformation fileInfo;
	private long offset;

	public void close(CloseReason reason) {
		if (this.closed)
			return;

		this.closed = true;
		if (this.connection != null)
			this.connection.getChannel().close();

		if (this.contentReceiver != null)
			this.contentReceiver.dispose(this);
	}

	private void handleProxyRequest(HttpRequest req) throws IOException {
		this.request = req;
		try {
			this.offset = HttpUtils.readOffset(req);
		} catch (InvalidRequestException ex) {
			ProxyApplication.getInstance().getLogger().info("Can't read offset from proxy request", ex);
			this.closeWithErrorResponse(CloseReason.Error, "Can't read offset from proxy request");
		}

		String identifier = req.uri().replace("/proxy/", "");
		if (identifier.contains("?"))
			identifier = identifier.substring(0, identifier.indexOf('?'));
		if (identifier.endsWith(".mp4"))
			identifier = identifier.substring(0, identifier.length() - ".mp4".length());

		if (identifier.isEmpty()) {
			this.closeWithErrorResponse(CloseReason.InvalidRequest, "Missing identifier");
			return;
		}

		Download download = ProxyApplication.getInstance().getDownloadManager().getDownload(identifier);
		if (download == null) {
			File alreadyDownloadedFile = ProxyApplication.getInstance().getDownloadManager().getDownloadRedirect(identifier);
			if (alreadyDownloadedFile != null) {
				ProxyApplication.getInstance().getLogger().info("Started file redirect streaming with file {} and identifier {}", alreadyDownloadedFile.getPath(), identifier);
				this.contentReceiver = new FileContentReceiver(alreadyDownloadedFile);
				this.contentReceiver.requestBytes(this.offset, this, this);
			} else {
				this.closeWithErrorResponse(CloseReason.InvalidRequest, "Download with identiifer " + identifier + " doesn't exists.");
			}
		} else {
			this.contentReceiver = download;
			this.contentReceiver.requestBytes(this.offset, this, this);
		}
	}

	private void sendInternalFileRequest(String filePath) throws IOException {
		InputStream stream = ProxyApplication.class.getResourceAsStream(filePath);
		if (stream == null) {
			this.closeWithErrorResponse(CloseReason.InvalidRequest, "File not found");
			return;
		}

		ByteBuf buffer = this.connection.getChannel().alloc().buffer(Constants.HTTP_INITIAL_RESOURCES_BUFFER_SIZE);
		String contentType = HttpUtils.getContentType(filePath);

		int readNum;
		byte[] readBuf = new byte[1024];
		while ((readNum = stream.read(readBuf, 0, readBuf.length)) != -1) {
			buffer.writeBytes(readBuf, 0, readNum);
		}
		stream.close();

		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		if (contentType != null)
			response.headers().set(HttpHeaders.CONTENT_TYPE, contentType);
		response.headers().set(HttpHeaders.CONNECTION, "close");
		response.headers().set(HttpHeaders.SERVER, "MovieProxy");
		response.headers().set(HttpHeaders.CONTENT_LENGTH, buffer.readableBytes());
		response.headers().set(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");

		HttpContent content = new DefaultHttpContent(buffer);
		HttpConnection.this.getConnection().getChannel().write(response);
		HttpConnection.this.getConnection().getChannel().write(content);
		HttpConnection.this.getConnection().getChannel().flush();
		this.close(CloseReason.Finished);
	}

	private void handleDataRequest(HttpRequest req) throws IOException {
		String path = (req.uri().length() >= 7 ? req.uri().substring("/data/".length()) : "");
		String responseText;
		String contentType;

		if (path.contains("?"))
			path = path.substring(0, path.indexOf('?'));

		switch (path.toLowerCase()) {
			case "folders.json":
			{
				List<FoldersResponse.FolderEntry> responseEntries = FoldersResponse.createFolderEntries();
				responseText = ProxyApplication.getInstance().getGson().toJson(responseEntries);
				contentType = "application/json; charset=utf-8";
				break;
			}
			case "downloads.json":
			{
				List<DownloadsResponse.DownloadEntry> responseEntries = DownloadsResponse.createDownloadsResponse();
				responseText = ProxyApplication.getInstance().getGson().toJson(responseEntries);
				contentType = "application/json; charset=utf-8";
				break;
			}
			default:
				this.closeWithErrorResponse(CloseReason.InvalidRequest, "Not supported data request");
				return;
		}

		ByteBuf buf = Unpooled.copiedBuffer(responseText, Charsets.UTF_8);

		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		if (contentType != null)
			response.headers().set(HttpHeaders.CONTENT_TYPE, contentType);
		response.headers().set(HttpHeaders.CONNECTION, "close");
		response.headers().set(HttpHeaders.SERVER, "MovieProxy");
		response.headers().set(HttpHeaders.CONTENT_LENGTH, buf.readableBytes());
		response.headers().set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

		HttpContent content = new DefaultHttpContent(buf);
		HttpConnection.this.getConnection().getChannel().write(response);
		HttpConnection.this.getConnection().getChannel().write(content);
		HttpConnection.this.getConnection().getChannel().flush();
		this.close(CloseReason.Finished);
	}

	private void handleUploadRequest(HttpRequest req) throws IOException {
		int beginIndex = req.uri().indexOf('?');
		if (beginIndex == -1) {
			this.closeWithErrorResponse(CloseReason.InvalidRequest, "Missing get data");
			return;
		}

		Map<String, String> data = new HashMap<>();
		String[] split = req.uri().substring(beginIndex + 1).split("&");

		for (String part : split) {
			int seperator = part.indexOf('=');
			if (seperator == -1)
				continue;

			String key = part.substring(0, seperator);
			String value = part.substring(seperator + 1);

			data.put(key, URLDecoder.decode(value, "UTF-8"));
		}

		String filename = data.get("filename");
		String url = data.get("url");
		String savepath = data.get("savepath");
		if (filename == null || url == null || savepath == null || filename.isEmpty() || url.isEmpty() || savepath.isEmpty()) {
			this.closeWithErrorResponse(CloseReason.InvalidRequest, "Missing parameters");
			return;
		}

		HttpFile httpFile = HttpFile.createFromUrl(url);
		if (httpFile == null) {
			this.closeWithErrorResponse(CloseReason.InvalidRequest, "Streaming url is invalid.");
			return;
		}

		String identifier = UUID.randomUUID().toString().substring(0, 8);
		Download download = ProxyApplication.getInstance().getDownloadManager().createDownload(identifier, new FilePath(filename, savepath), httpFile);
		download.startDownloadConnection(0L, 0L);

		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TEMPORARY_REDIRECT);
		response.headers().set(HttpHeaders.CONNECTION, "close");
		response.headers().set(HttpHeaders.SERVER, "MovieProxy");
		response.headers().set(HttpHeaders.LOCATION, "/proxy/" + identifier + ".mp4");
		this.connection.getChannel().writeAndFlush(response);
		this.close(CloseReason.Finished);
	}

	private void closeWithErrorResponse(CloseReason reason, String message) {
		byte[] encodedMsg = message.getBytes(Charsets.UTF_8);

		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, reason.getHttpResponseStatus());
		response.headers().set(HttpHeaders.CONTENT_TYPE, "text/html");
		response.headers().set(HttpHeaders.CONTENT_LENGTH, encodedMsg.length);
		response.headers().set(HttpHeaders.CONNECTION, "close");
		response.headers().set(HttpHeaders.SERVER, "MovieProxy");

		ByteBuf buf = Unpooled.wrappedBuffer(encodedMsg);
		HttpContent content = new DefaultHttpContent(buf);

		this.connection.getChannel().write(response);
		this.connection.getChannel().write(content);
		this.connection.getChannel().flush();
		this.close(reason);
	}

	public void receivedFromServer(Object msg) throws IOException {
		if (this.closed)
			return;

		if (msg instanceof HttpRequest) {
			HttpRequest req = (HttpRequest) msg;
			ProxyApplication.getInstance().getLogger().info("Received request " + req.uri());

			if (req.uri().startsWith("/proxy")) {
				this.handleProxyRequest(req);
			} else if (req.uri().startsWith("/assets")) {
				String filePath = req.uri().replace("..", "");
				if (filePath.contains("?"))
					filePath = filePath.substring(0, filePath.indexOf('?'));
				this.sendInternalFileRequest(filePath);
			} else if (req.uri().equals("/") || req.uri().startsWith("/index")) {
				this.sendInternalFileRequest("/index.html");
			} else if (req.uri().startsWith("/data")) {
				this.handleDataRequest(req);
			} else if (req.uri().startsWith("/upload")) {
				this.handleUploadRequest(req);
			} else {
				// Not yet implemented
				this.closeWithErrorResponse(CloseReason.InvalidRequest, "Invalid url");
			}
		}
	}

	@Override
	public void onStart(FileInformation fileInfo) {
		this.fileInfo = fileInfo;
		ProxyApplication.getInstance().getLogger().info("Started file streaming.");

		HttpResponseStatus responseStatus = (HttpConnection.this.offset == 0L ? HttpResponseStatus.OK : HttpResponseStatus.PARTIAL_CONTENT);
		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, responseStatus);

		response.headers().set(HttpHeaders.CONTENT_TYPE, fileInfo.getContentType());
		response.headers().set(HttpHeaders.CONNECTION, "keep-alive");
		response.headers().set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Range");
		response.headers().set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Accept-Ranges,Content-Encoding,Content-Length,Content-Range");
		response.headers().set(HttpHeaders.SERVER, "MovieProxy");

		if (responseStatus == HttpResponseStatus.OK) {
			response.headers().set(HttpHeaders.CONTENT_LENGTH, fileInfo.getContentLength());
			response.headers().set(HttpHeaders.ACCEPT_RANGES, "bytes");
		} else {
			response.headers().set(HttpHeaders.CONTENT_LENGTH, fileInfo.getContentLength() - this.offset);
			response.headers().set(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", HttpConnection.this.offset, fileInfo.getContentLength() - 1L, fileInfo.getContentLength()));
		}

		HttpConnection.this.getConnection().getChannel().writeAndFlush(response);
	}

	@Override
	public void onData(byte[] data) {
		HttpContent content = new DefaultHttpContent(Unpooled.wrappedBuffer(data));
		HttpConnection.this.getConnection().getChannel().writeAndFlush(content);

		HttpConnection.this.offset += data.length;
	}

	@Override
	public void onFinish() {
		this.getConnection().getChannel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		this.close(CloseReason.Finished);
	}

	@Override
	public void error(Exception ex) {
		HttpConnection.this.closeWithErrorResponse(CloseReason.Error, "An exception occurred: " + ex.getMessage());
		ex.printStackTrace();
	}

}
