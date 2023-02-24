package org.snomed.termextractor.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Concept {

	public static final Long PREFERRED = 900000000000548007L;
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

	public void sortAncestorsByPt(Long langRefset) {
		childConcepts.sort(Comparator.comparing(concept -> concept.getPt(langRefset)));
		for (Concept childConcept : childConcepts) {
			childConcept.sortAncestorsByPt(langRefset);
		}
	}

	public String getPt(Long langRefset) {
		if (pt == null) {
			descriptions.stream()
					.filter(description -> PREFERRED.equals(description.getAcceptabilityMap().get(langRefset)))
					.findFirst()
					.ifPresentOrElse(description -> pt = description.getTerm(),
							() -> {
								if (!descriptions.isEmpty()) {
									pt = descriptions.iterator().next().getTerm();
								} else {
									pt = "";// This should never happen
									System.err.printf("Concept %s does not have a preferred term in the requested language refset %s.%n",
											conceptId, langRefset);
								}
							});
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
}
