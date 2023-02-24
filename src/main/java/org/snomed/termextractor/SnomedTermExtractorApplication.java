package org.snomed.termextractor;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.domain.ConceptConstants;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.snomed.termextractor.model.Concept;
import org.snomed.termextractor.model.Description;
import org.snomed.termextractor.service.HierarchyAndTermsComponentFactory;
import org.snomed.termextractor.service.SCTIDUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;

@SpringBootApplication
public class SnomedTermExtractorApplication {

	@Value("${release-files}") String releaseFiles;
	@Value("${extract-concept-and-descendants}") String extractConceptAndDescendants;
	private final AtomicLong countWritten = new AtomicLong();
	private final Set<Long> written = new LongOpenHashSet();

	public static void main(String[] args) throws ReleaseImportException, IOException {
		SnomedTermExtractorApplication application = SpringApplication.run(SnomedTermExtractorApplication.class, args)
				.getBean(SnomedTermExtractorApplication.class);
		application.run();
	}

	private void run() throws ReleaseImportException, IOException {
		if (extractConceptAndDescendants.isEmpty()) {
			System.out.println();
			System.err.println("Please specify the subset of concepts to extract using the '--extract-concept-and-descendants=' parameter. " +
					"For example, to extract Clinical findings use: --extract-concept-and-descendants=404684003");
			System.out.println();
			System.exit(1);
		}
		Long ancestorConceptId = SCTIDUtil.parseSCTID(extractConceptAndDescendants);

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
				System.err.printf("File '%s' is not accessible.$n", file.getPath());
				System.exit(1);
			}
			releaseFileInputStreams.add(new FileInputStream(file));
		}

		HierarchyAndTermsComponentFactory componentFactory = new HierarchyAndTermsComponentFactory();
		new ReleaseImporter().loadEffectiveSnapshotReleaseFileStreams(releaseFileInputStreams, LoadingProfile.light, componentFactory, false);

		Map<Long, Concept> conceptMap = componentFactory.getConceptMap();
		System.out.printf("%s active concepts loaded%n", conceptMap.size());
		System.out.printf("Extracting concept %s and its descendants...%n", ancestorConceptId);

		Concept ancestorConcept = conceptMap.get(ancestorConceptId);
		if (ancestorConcept == null) {
			System.out.println();
			System.err.printf("Concept %s is not found in set of active concepts from these release files!", extractConceptAndDescendants);
			System.out.println();
			System.exit(1);
		}

		Long langRefset = SCTIDUtil.parseSCTID(ConceptConstants.US_EN_LANGUAGE_REFERENCE_SET);

		String pt = ancestorConcept.getPt(langRefset);
		String extractFilename = format("SNOMED-CT_TermExtract_%s_%s.txt", ptToFilename(pt), componentFactory.getMaxEffectiveTime());
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(extractFilename))) {
			writer.write("ConceptId\tPreferredTerm\tOtherSynonyms");
			writer.newLine();
			writeConcepts(Collections.singletonList(ancestorConcept), langRefset, writer);
		}
		System.out.println();
		System.out.printf("Extract successful%n");
		System.out.printf("%s concepts written to TSV file %s%n", written.size(), extractFilename);
	}

	public String ptToFilename(String pt) {
		return pt.replace(" ", "-").replaceAll("[^a-zA-Z0-9_-]", "");
	}

	private void writeConcepts(List<Concept> concepts, Long langRefset, BufferedWriter writer) throws IOException {
		concepts.sort(Comparator.comparing(concept -> concept.getPt(langRefset)));
		for (Concept concept : concepts) {
			if (written.add(concept.getConceptId())) {
				writer.write(concept.getConceptId().toString());
				writer.write("\t");
				String pt = concept.getPt(langRefset);
				writer.write(pt);
				writer.write("\t");
				boolean first = true;
				for (Description description : concept.getDescriptions()) {
					if (!description.getTerm().equals(pt)) {
						if (first) {
							first = false;
						} else {
							writer.write("|");
						}
						writer.write(description.getTerm());
					}
				}
				writer.newLine();
				if (countWritten.incrementAndGet() % 1_000 == 0) {
					System.out.print(".");
				}
			}
			writeConcepts(concept.getChildConcepts(), langRefset, writer);
		}
	}

}
