package de.howaner.movieproxy.content;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FileContentReceiverManager extends Thread {
	private final List<FileContentReceiver> receivers = new ArrayList<>();
	private final Lock receiversLock = new ReentrantLock();

	public FileContentReceiverManager() {
		super("File content receiver manager");
		this.setDaemon(true);
	}

	@Override
	public void run() {
		while (!this.isInterrupted()) {
			List<FileContentReceiver> receivers;
			try {
				this.receiversLock.lock();
				receivers = new ArrayList<>(this.receivers);
			} finally {
				this.receiversLock.unlock();
			}

			if (receivers.isEmpty()) {
				try {
					synchronized (this) {
						this.wait();
					}
				} catch (InterruptedException ex) {}
				continue;
			}

			for (FileContentReceiver receiver : receivers) {
				receiver.loop();
			}

			try {
				Thread.sleep(1000L);  // Sleep one second
			} catch (InterruptedException ex) {
				break;
			}
		}
	}

	public void addReceiver(FileContentReceiver receiver) {
		try {
			this.receiversLock.lock();
			this.receivers.add(receiver);
		} finally {
			this.receiversLock.unlock();
		}

		synchronized (this) {
			this.notifyAll();
		}
	}

	public void removeReceiver(FileContentReceiver receiver) {
		try {
			this.receiversLock.lock();
			this.receivers.remove(receiver);
		} finally {
			this.receiversLock.unlock();
		}
	}

}
