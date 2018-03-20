package de.howaner.movieproxy.download;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;

public class OffsetMap {
	private final List<OffsetEntry> entries = new ArrayList<>();

	public boolean contains(long offset) {
		return entries.stream().anyMatch(e -> offset >= e.start && offset <= e.end && e.end != 0L);
	}

	public boolean contains(long start, long end) {
		long nextOffset = start;

		while (true) {
			boolean changes = false;
			for (OffsetEntry entry : this.entries) {
				if (entry.end != 0L && nextOffset >= entry.start && nextOffset < entry.end) {
					nextOffset = entry.end;
					changes = true;
				}
			}

			if (!changes)
				break;

			nextOffset += 1;
		}

		return (nextOffset - 1) >= end;
	}

	public long getMissingBytesAmount(long lastOffset) {
		List<OffsetEntry> entries = this.searchMissingOffsets(lastOffset);
		return entries.stream().mapToLong(OffsetEntry::getDiff).sum();
	}

	public List<OffsetEntry> searchMissingOffsets(long lastOffset) {
		List<OffsetEntry> list = new ArrayList<>();
		long offset = 0L;

		while (true) {
			if (offset >= lastOffset)
				break;

			final long fOffset = offset;
			long maxOffset = this.entries.stream().filter(e -> fOffset >= e.start && fOffset <= e.end && e.end != 0L).mapToLong(e -> e.end).max().orElse(0L);
			if (maxOffset == 0L) {
				// Offset isn't present -> There is a missing range.
				long nextOffset = this.entries.stream().filter(e -> e.start > fOffset && e.end != 0L).mapToLong(e -> e.start).min().orElse(0L);
				if (nextOffset == 0L) {
					list.add(new OffsetEntry(fOffset, lastOffset));
					break;
				} else {
					list.add(new OffsetEntry(fOffset, nextOffset - 1));
					offset = nextOffset;
				}
				continue;
			}

			offset = maxOffset + 1;
		}

		return list;
	}

	public OffsetEntry createEntry(long start) {
		OffsetEntry entry = new OffsetEntry(start);
		this.entries.add(entry);
		return entry;
	}

	@ToString
	@EqualsAndHashCode
	public static class OffsetEntry {
		public long start;
		public long end;

		private OffsetEntry(long start) {
			this(start, 0L);
		}

		public OffsetEntry(long start, long end) {
			this.start = start;
			this.end = end;
		}

		public long getDiff() {
			return this.end - this.start;
		}
	}

}
