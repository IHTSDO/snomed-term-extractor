package org.snomed.termextractor.service;

import java.util.regex.Pattern;

import static java.lang.String.format;

public class SCTIDUtil {

	private static final Pattern SCTID_PATTERN = Pattern.compile("[0-9]{6,18}");

	public static Long parseSCTID(String sctid) {
		if (!SCTID_PATTERN.matcher(sctid).matches()) {
			throw new RuntimeException(format("Could not parse invalid SNOMED ID '%s'.", sctid));
		}
		return Long.parseLong(sctid);
	}
}
