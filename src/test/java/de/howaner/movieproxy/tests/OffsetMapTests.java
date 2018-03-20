package de.howaner.movieproxy.tests;

import de.howaner.movieproxy.download.OffsetMap;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class OffsetMapTests {

	@Test
	public void testBasicEmpty() {
		OffsetMap offsetMap = new OffsetMap();
		offsetMap.createEntry(0L).end = 1000L;
		offsetMap.createEntry(1001L).end = 2000L;

		List<OffsetMap.OffsetEntry> missingEntries = offsetMap.searchMissingOffsets(2000L);
		System.out.println(missingEntries);
		assertTrue("OffsetMap with length of 2000 needs to be empty", missingEntries.isEmpty());
		assertTrue("OffsetMap needs to have bytes 0 - 2000", offsetMap.contains(0L, 2000L));
		assertTrue("OffsetMap needs to have bytes 900 - 2000", offsetMap.contains(900L, 2000L));
	}

	@Test
	public void testAdvancedEmpty() {
		OffsetMap offsetMap = new OffsetMap();
		offsetMap.createEntry(0L).end = 3000L;
		offsetMap.createEntry(2000L).end = 5000L;
		offsetMap.createEntry(100L).end = 200L;
		offsetMap.createEntry(4000L).end = 8000L;

		List<OffsetMap.OffsetEntry> missingEntries = offsetMap.searchMissingOffsets(2000L);
		System.out.println(missingEntries);
		assertTrue("OffsetMap with length of 2000 needs to be empty", missingEntries.isEmpty());
	}

	@Test
	public void testBasicMissing() {
		OffsetMap offsetMap = new OffsetMap();
		offsetMap.createEntry(0L).end = 200L;
		offsetMap.createEntry(500L).end = 1000L;

		List<OffsetMap.OffsetEntry> missingEntries = offsetMap.searchMissingOffsets(1300L);
		System.out.println(missingEntries);
		assertEquals("Invalid first offset entry", new OffsetMap.OffsetEntry(201L, 499L), missingEntries.get(0));
		assertEquals("Invalid second offset entry", new OffsetMap.OffsetEntry(1001L, 1300L), missingEntries.get(1));
	}

}
