package org.snomed.termextractor.service;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ihtsdo.otf.snomedboot.domain.ConceptConstants;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.termextractor.model.Concept;
import org.snomed.termextractor.model.Description;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class HierarchyAndTermsComponentFactory extends ImpotentComponentFactory {

	private final Map<Long, Concept> conceptMap = new Long2ObjectOpenHashMap<>();
	private final Map<Long, Description> descriptionMap = new Long2ObjectOpenHashMap<>();
	private final List<Long> refsets;
	private final Map<Long, Set<Long>> refsetMembers = new Long2ObjectOpenHashMap<>();
	private final boolean loadFsn;
	private int maxEffectiveTime = 0;

	public HierarchyAndTermsComponentFactory(List<Long> refsets, boolean loadFsn) {
		this.refsets = refsets;
		this.loadFsn = loadFsn;
	}

    @Override
	public void newConceptState(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		collectMaxEffectiveTime(effectiveTime);
		Long id = SCTIDUtil.parseSCTID(conceptId);
		conceptMap.put(id, new Concept(id));
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		collectMaxEffectiveTime(effectiveTime);
		boolean fsn = typeId.equals("900000000000003001");
		if (fsn) {
			System.out.println();
		}
		if ("1".equals(active) && (typeId.equals("900000000000013009") || (loadFsn && fsn))) {// Active synonym or FSN
			Concept concept = conceptMap.get(SCTIDUtil.parseSCTID(conceptId));
			if (concept != null) {// Concept will be null if it's inactive. No need to extract these.
				Long descriptionId = SCTIDUtil.parseSCTID(id);
				Description description = new Description(descriptionId, term, languageCode, fsn);
				concept.addDescription(description);
				descriptionMap.put(descriptionId, description);
			}
		}
	}

	@Override
	public void newRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		collectMaxEffectiveTime(effectiveTime);
		if ("1".equals(active) && typeId.equals(ConceptConstants.isA)) {
			Concept parentConcept = conceptMap.get(SCTIDUtil.parseSCTID(destinationId));
			if (parentConcept == null) {
				System.err.printf("Concept '%s' has an active is-a relationship to concept '%s' that is inactive.%n", sourceId, destinationId);
				return;
			}
			Concept childConcept = conceptMap.get(SCTIDUtil.parseSCTID(sourceId));
			if (childConcept != null) {
				parentConcept.addChild(childConcept);
			}
		}
	}

	@Override
	public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		collectMaxEffectiveTime(effectiveTime);
		if ("1".equals(active)) {
			Long refsetIdLong = SCTIDUtil.parseSCTID(refsetId);
			if (fieldNames.length == 7 && fieldNames[6].equals("acceptabilityId")) {// Active language refsets
				Description description = descriptionMap.get(SCTIDUtil.parseSCTID(referencedComponentId));
				if (description != null) {
					// id	effectiveTime	active	moduleId	refsetId	referencedComponentId	acceptabilityId
					// Record acceptability
					description.addAcceptability(refsetIdLong, SCTIDUtil.parseSCTID(otherValues[0]));
				}
			} else if (refsets.contains(refsetIdLong)) {
				refsetMembers.computeIfAbsent(refsetIdLong, k -> new LongOpenHashSet()).add(SCTIDUtil.parseSCTID(referencedComponentId));
			}
		}
	}

	private void collectMaxEffectiveTime(String effectiveTime) {
		if (!effectiveTime.isEmpty()) {
			int i = Integer.parseInt(effectiveTime);
			if (i > maxEffectiveTime) {
				maxEffectiveTime = i;
			}
		}
	}

	public Map<Long, Concept> getConceptMap() {
		return conceptMap;
	}

	public int getMaxEffectiveTime() {
		return maxEffectiveTime;
	}

	public Map<Long, Set<Long>> getRefsetMembers() {
		return refsetMembers;
	}
}
