package org.snomed.termextractor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SnomedTermExtractorApplicationTest {

	@Test
	public void testPtToFilename() {
		SnomedTermExtractorApplication app = new SnomedTermExtractorApplication();
		assertEquals("Product-containing-genetically-modified-T-cell", app.ptToFilename("Product containing genetically modified T-cell"));
		assertEquals("Aspirin-500-mg-oral-tablet", app.ptToFilename("Aspirin 500 mg oral tablet"));
	}
}
