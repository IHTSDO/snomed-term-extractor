package org.snomed.termextractor.model;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.snomed.termextractor.service.ServiceException;

import java.util.*;

public class Concept {

	public static final Long PREFERRED = 900000000000548007L;
	public static final Long ACCEPTABLE = 900000000000549004L;
	private final Long conceptId;
	private final List<Description> descriptions;
	private final List<Concept> childConcepts;
	private String pt;

	public Concept(Long conceptId) {
		this.conceptId = conceptId;
		descriptions = new ArrayList<>();
		childConcepts = new ArrayList<>();
	}

	public void addDescription(Description description) {
		descriptions.add(description);
	}

	public void addChild(Concept childConcept) {
		childConcepts.add(childConcept);
	}

	public String getPtSafe(List<Long> langRefsets) {
		return getPtCanReturnNull(langRefsets);
	}

	public String getPt(List<Long> langRefsets) throws ServiceException {
		String term = getPtCanReturnNull(langRefsets);
		if (term == null) {
			throw new ServiceException(("Concept %s does not have a preferred term in any " +
					"of the requested language refsets %s.%n").formatted(conceptId, langRefsets));
		}
		return term;
	}

	private String getPtCanReturnNull(List<Long> langRefsets) {
		if (pt == null) {
			for (Long langRefset : langRefsets) {
				for (Description description : descriptions) {
					if (PREFERRED.equals(description.getAcceptabilityMap().get(langRefset))) {
						pt = description.getTerm();
						return pt;
					}
				}
			}
		}
		return pt;
	}

	public Long getConceptId() {
		return conceptId;
	}

	public List<Description> getDescriptions() {
		return descriptions;
	}

	public List<Concept> getChildConcepts() {
		return childConcepts;
	}

	public Set<Long> getDescendantAndSelfIds() {
		return getDescendantAndSelfIds(new LongOpenHashSet());
	}

	private Set<Long> getDescendantAndSelfIds(Set<Long> ids) {
		ids.add(conceptId);
		for (Concept childConcept : getChildConcepts()) {
			childConcept.getDescendantAndSelfIds(ids);
		}
		return ids;
	}
}
