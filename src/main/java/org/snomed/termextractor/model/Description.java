package org.snomed.termextractor.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Description {

	private final Long descriptionId;
	private final String term;
	private final String languageCode;
	private final Map<Long, Long> acceptabilityMap;

	public Description(Long descriptionId, String term, String languageCode) {
		this.descriptionId = descriptionId;
		this.term = term;
		this.languageCode = languageCode;
		acceptabilityMap = new HashMap<>();
	}

	public boolean isPreferredOrAcceptable(List<Long> langRefsets) {
		for (Long langRefset : langRefsets) {
			if (acceptabilityMap.containsKey(langRefset)) {
				return true;
			}
		}
		return false;
	}

	public void addAcceptability(Long langRefset, Long acceptability) {
		acceptabilityMap.put(langRefset, acceptability);
	}

	public Long getDescriptionId() {
		return descriptionId;
	}

	public String getTerm() {
		return term;
	}

	public String getLanguageCode() {
		return languageCode;
	}

	public Map<Long, Long> getAcceptabilityMap() {
		return acceptabilityMap;
	}
}
