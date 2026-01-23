package org.snomed.termextractor;

import com.google.common.base.Strings;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.snomed.termextractor.model.Concept;
import org.snomed.termextractor.model.Description;
import org.snomed.termextractor.service.HierarchyAndTermsComponentFactory;
import org.snomed.termextractor.service.SCTIDUtil;
import org.snomed.termextractor.service.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;

@SpringBootApplication
public class SnomedTermExtractorApplication {

	public static final String EXPORT_HEADER = "ConceptCode\tDisplayTerm\tAdditionalSearchTerms";

	@Value("${release-files}") String releaseFiles;
	@Value("${extract-concept-and-descendants}") String includeConceptAndDescendants;
	@Value("${extract-refset}") String includeRefset;
	@Value("${exclude-concept-and-descendants}") String excludeConceptAndDescendants;
	@Value("${display-term-language-refsets}") String languageRefsetParam;
	@Value("${synonym-language-refsets}") String synonymLanguageRefsetParam;
	private final AtomicLong countWritten = new AtomicLong();
	private final Set<Long> written = new LongOpenHashSet();

	public static void main(String[] args) throws ReleaseImportException, IOException {
		SnomedTermExtractorApplication application = SpringApplication.run(SnomedTermExtractorApplication.class, args)
				.getBean(SnomedTermExtractorApplication.class);
		try {
			application.run();
		} catch (ServiceException e) {
			System.out.println();
			System.err.printf(e.getMessage());
			System.out.println();
			System.exit(1);
		}
	}

	private void run() throws ReleaseImportException, IOException, ServiceException {
		if (includeConceptAndDescendants.isEmpty() && includeRefset.isEmpty()) {
			System.out.println();
			System.err.println("Please specify the subset of concepts to extract using the '--extract-concept-and-descendants=' or '--extract-refset' parameters. " +
					"For example, to extract Clinical findings use: --extract-concept-and-descendants=404684003");
			System.out.println();
			System.exit(1);
		}

		List<List<Long>> refsetsListOfLists = conceptsParamToList(includeRefset);
		List<Long> refsets = refsetsListOfLists.isEmpty() ? Collections.emptyList() : refsetsListOfLists.get(0);
		List<List<Long>> includes = conceptsParamToList(includeConceptAndDescendants);
		List<List<Long>> excludes = conceptsParamToList(excludeConceptAndDescendants);

		if (releaseFiles.isEmpty()) {
			System.out.println();
			System.err.println("Please provide the path to the RF2 release files using the '--release-files=' parameter.");
			System.out.println();
			System.exit(1);
		}

		Set<InputStream> releaseFileInputStreams = new HashSet<>();
		for (String filePath : releaseFiles.split(",")) {
			File file = new File(filePath);
			if (!file.isFile()) {
				System.err.printf("File '%s' is not accessible.%n", file.getPath());
				System.exit(1);
			}
			releaseFileInputStreams.add(new FileInputStream(file));
		}

		List<Long> displayTermLanguageRefsets = new ArrayList<>();
		if (languageRefsetParam == null || languageRefsetParam.isEmpty()) {
			System.out.println();
			System.err.println("Please provide a comma separated list of the language refsets ids that should be used to " +
					"select terms during extraction, using the '--display-term-language-refsets=' parameter.");
			System.out.println();
			System.exit(1);
		} else {
			for (String refset : languageRefsetParam.split(",")) {
				displayTermLanguageRefsets.add(SCTIDUtil.parseSCTID(refset.trim()));
			}
		}

		List<Long> synonymlanguageRefsets = new ArrayList<>();
		if (!Strings.isNullOrEmpty(synonymLanguageRefsetParam)) {
			for (String refset : synonymLanguageRefsetParam.split(",")) {
				synonymlanguageRefsets.add(SCTIDUtil.parseSCTID(refset.trim()));
			}
		} else {
			synonymlanguageRefsets = displayTermLanguageRefsets;
		}

		HierarchyAndTermsComponentFactory componentFactory = new HierarchyAndTermsComponentFactory(refsets);
		LoadingProfile loadingProfile = LoadingProfile.light.withInactiveConcepts();
		if (refsets.isEmpty()) {
			loadingProfile.setIncludedReferenceSetFilenamePatterns(Set.of(".*der2_cRefset_LanguageSnapshot.*"));
		} else {
			List<String> allRefsets = getAllRefsets(refsets, displayTermLanguageRefsets, synonymlanguageRefsets);
			loadingProfile = loadingProfile.withRefsets(allRefsets.toArray(new String[0]));
		}
		new ReleaseImporter().loadEffectiveSnapshotReleaseFileStreams(releaseFileInputStreams, loadingProfile, componentFactory, false);

		Map<Long, Concept> conceptMap = componentFactory.getConceptMap();
		System.out.printf("%s active concepts loaded%n", conceptMap.size());

		Set<Long> allExcludes = new LongOpenHashSet();
		for (List<Long> excludeList : excludes) {
			for (Long conceptId : excludeList) {
				allExcludes.add(conceptId);
				Concept concept = conceptMap.get(conceptId);
				if (concept != null) {
					Set<Long> descendantIds = concept.getDescendantAndSelfIds();
					System.out.printf("Excluding concept %s |%s| and descendants (%s)%n",
							concept.getConceptId(), concept.getPt(displayTermLanguageRefsets), descendantIds.size());
					allExcludes.addAll(descendantIds);
				}
			}
		}
		System.out.printf("Total excludes %s%n", allExcludes.size());

		for (Long refset : refsets) {
			Set<Long> memberIds = componentFactory.getRefsetMembers().getOrDefault(refset, Collections.emptySet());
			System.out.printf("Extracting refset %s with %s active members...%n", refset, memberIds.size());

			Concept refsetConcept = conceptMap.get(refset);
			String refsetPT;
			if (refsetConcept == null) {
				System.err.printf("Concept for Refset '%s' not found in release files, %s members found.%n", refset, memberIds.size());
				refsetPT = refset.toString();
			} else {
				refsetPT = refsetConcept.getPt(displayTermLanguageRefsets);
			}

			String extractFilename = format("SNOMED-CT_TermExtract_Refset_%s_%s.txt", ptToFilename(refsetPT), componentFactory.getMaxEffectiveTime());
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(extractFilename))) {
				writer.write(EXPORT_HEADER);
				writer.write("\r\n");

				List<Concept> members = new ArrayList<>();
				for (Long memberId : memberIds) {
					Concept concept = conceptMap.get(memberId);
					if (concept != null) {
						members.add(concept);
					} else {
						System.err.printf("Concept '%s' within Refset '%s' not found in release files so will not be extracted.%n", memberId, refset);
					}
				}
				writeConcepts(members, allExcludes, displayTermLanguageRefsets, synonymlanguageRefsets, writer);
			}
		}

		for (List<Long> include : includes) {
			if (include.isEmpty()) {
				continue;
			}
			String extractFilename;
			if (include.size() == 1) {
				Long ancestorConceptId = include.get(0);
				System.out.printf("Extracting concept %s and its descendants...%n", ancestorConceptId);

				Concept ancestorConcept = conceptMap.get(ancestorConceptId);
				checkActive(ancestorConcept);

				String pt = ancestorConcept.getPt(displayTermLanguageRefsets);
				extractFilename = format("SNOMED-CT_TermExtract_%s_%s.txt", ptToFilename(pt), componentFactory.getMaxEffectiveTime());
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(extractFilename))) {
					writer.write(EXPORT_HEADER);
					writer.write("\r\n");
					writeConcepts(Collections.singletonList(ancestorConcept), allExcludes, displayTermLanguageRefsets, synonymlanguageRefsets, writer);
				}

			} else {
				System.out.printf("Extracting concept list %s...%n", include);
				Concept firstConcept = conceptMap.get(include.get(0));
				checkActive(firstConcept);

				String pt = firstConcept.getPt(displayTermLanguageRefsets);
				extractFilename = format("SNOMED-CT_TermExtract_%s-List_%s.txt", ptToFilename(pt), componentFactory.getMaxEffectiveTime());

				try (BufferedWriter writer = new BufferedWriter(new FileWriter(extractFilename))) {
					writer.write(EXPORT_HEADER);
					writer.write("\r\n");
					for (Long singleConcept : include) {
						Concept concept = conceptMap.get(singleConcept);
						writeConcept(concept, displayTermLanguageRefsets, synonymlanguageRefsets, writer);
					}
				}
			}

			System.out.println();
			System.out.printf("Extract successful%n");
			System.out.printf("%s concepts written to TSV file %s%n", written.size(), extractFilename);
		}

	}

	private List<String> getAllRefsets(List<Long> refsets, List<Long> displayTermLanguageRefsets, List<Long> synonymlanguageRefsets) {
		List<String> all = new ArrayList<>();
		addAll(refsets, all);
		addAll(displayTermLanguageRefsets, all);
		addAll(synonymlanguageRefsets, all);
		return all;
	}

	private void addAll(List<Long> source, List<String> target) {
		source.stream().map(Object::toString).forEach(target::add);
	}

	private List<List<Long>> conceptsParamToList(String param) {
		List<List<Long>> extracts = new ArrayList<>();
		if (param != null && !param.isEmpty()) {
			for (String extract : param.split(",")) {
				String[] concepts = extract.split("\\|");
				List<Long> ids = new ArrayList<>();
				for (String concept : concepts) {
					Long conceptId = SCTIDUtil.parseSCTID(concept);
					ids.add(conceptId);
				}
				extracts.add(ids);
			}
		}
		return extracts;
	}

	private void checkActive(Concept ancestorConcept) throws ServiceException {
		if (ancestorConcept == null) {
			throw new ServiceException("Concept %s is not found in set of active concepts from these release files!".formatted(includeConceptAndDescendants));
		}
	}

	public String ptToFilename(String pt) {
		return pt.replace(" ", "-").replaceAll("[^a-zA-Z0-9_-]", "");
	}

	private void writeConcepts(List<Concept> concepts, Set<Long> allExcludes, List<Long> displayTermLangRefsets, List<Long> synonymlanguageRefsets, BufferedWriter writer) throws IOException, ServiceException {
		concepts.sort(Comparator.comparing(concept -> concept.getPtSafe(displayTermLangRefsets)));
		for (Concept concept : concepts) {
			if (allExcludes.contains(concept.getConceptId())) {
				continue;
			}
			if (written.add(concept.getConceptId())) {
				writeConcept(concept, displayTermLangRefsets, synonymlanguageRefsets, writer);
				if (countWritten.incrementAndGet() % 1_000 == 0) {
					System.out.print(".");
				}
			}
			writeConcepts(concept.getChildConcepts(), allExcludes, displayTermLangRefsets, synonymlanguageRefsets, writer);
		}
	}

	private static void writeConcept(Concept concept, List<Long> displayTermLangRefsets, List<Long> synonymlanguageRefsets, BufferedWriter writer) throws IOException, ServiceException {
		writer.write(concept.getConceptId().toString());
		writer.write("\t");
		String pt = concept.getPt(displayTermLangRefsets);
		writer.write(pt);
		writer.write("\t");
		boolean first = true;
		for (Description description : concept.getDescriptions()) {
			if (!description.getTerm().equals(pt) && description.isPreferredOrAcceptable(synonymlanguageRefsets)) {
				if (first) {
					first = false;
				} else {
					writer.write("|");
				}
				writer.write(description.getTerm());
			}
		}
		writer.write("\r\n");
	}

}
