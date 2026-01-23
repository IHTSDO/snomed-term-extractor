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
	private String fsn;

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

	public String getFSN(List<Long> langRefsets) throws ServiceException {
		String term = getFSNOrNull(langRefsets);
		if (term == null) {
			throw new ServiceException(("Concept %s does not have an FSN in any " +
					"of the requested language refsets %s.%n").formatted(conceptId, langRefsets));
		}
		return term;
	}

	private String getFSNOrNull(List<Long> langRefsets) {
		if (fsn == null) {
			for (Long langRefset : langRefsets) {
				for (Description description : descriptions) {
					if (description.isFsn() && PREFERRED.equals(description.getAcceptabilityMap().get(langRefset))) {
						fsn = description.getTerm();
						return fsn;
					}
				}
			}
		}
		return fsn;
	}

	public String getPtSafe(List<Long> langRefsets) {
		return getPtOrNull(langRefsets);
	}

	public String getPt(List<Long> langRefsets) throws ServiceException {
		String term = getPtOrNull(langRefsets);
		if (term == null) {
			throw new ServiceException(("Concept %s does not have a preferred term in any " +
					"of the requested language refsets %s.%n").formatted(conceptId, langRefsets));
		}
		return term;
	}

	private String getPtOrNull(List<Long> langRefsets) {
		if (pt == null) {
			for (Long langRefset : langRefsets) {
				for (Description description : descriptions) {
					if (!description.isFsn() && PREFERRED.equals(description.getAcceptabilityMap().get(langRefset))) {
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
