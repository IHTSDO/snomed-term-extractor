# SNOMED CT Term Extractor
## Background
SNOMED International advocates using a Terminology Server to access SNOMED CT content. 
This way content can be managed and kept up to date. 

Terminology servers can implement best practice functionality, for example:
- fast multi-prefix concept search, using all active synonyms, constrained to a hierarchy or subset
- fetching subsets that include inactive concepts for reports spanning more than one SNOMED CT release

SNOMED CT Terminology Servers often use standardised interfaces like HL7 FHIR, for more information see: 
[SNOMED CT Implementation Portal / Terminology Services](https://implementation.snomed.org/terminology-services) 

## When a Terminology Server is not possible...
Sometimes it is not possible to include a terminology server in your architecture. 
In this case it is still possible to use SNOMED CT for data input, and to implement some of the concept-search best practises.

## This Tool
This tool provides a mechanism to extract subsets of SNOMED CT concepts from the RF2 release files into a simple TSV format.  
Concept identifiers are extracted along with their preferred terms and also the acceptable synonyms.

Please ensure that:
- both the preferred term and acceptable synonyms are used in search algorithms to provide a good user experience
  - if users can not find the terms they need easily there will be an impact on data quality
- the process of extracting content is repeated regularly to keep pace with guidelines in your country


### Output Format
Each run of the tool creates a TSV file with one concept per row. 

Extracted columns are:
| ConceptId | PreferredTerm | OtherSynonyms |
| --------- | ------------- | ------------- |
| Value will be the concept code | Value will be the preferred/display term | Value will be a pipe separated list of other terms that should be included in the search index |

For example:
| ConceptId | PreferredTerm | OtherSynonyms |
| --------- | ------------- | ------------- |
| 63697000 | Cardiopulmonary bypass operation | CPB - Cardiopulmonary bypass\|Cardiopulmonary bypass\|Cardiopulmonary perfusion\|Heart lung bypass |

#### Extract Order
Concepts are extracted using a depth first method with each level is sorted by preferred term. 
The order will look similar to the hierarchy in the SNOMED International browser if switched to preferred terms and descendants are fully expanded.

### Running the tool
Download the latest jar file from the releases page.  
_(Alternatively the jar file can be compiled from the source code using 
[Apache Maven](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html) `mvn install`)._

Run the term extraction process on the command line:
```
java -Xms2g -jar snomed-term-extractor.jar \
  --release-files=RF2_RELEASE_ZIP_FILE \
  --extract-concept-and-descendants=CONCEPT
```
Where: 
- `RF2_RELEASE_ZIP_FILE` is the path to the RF2 Edition you would like to extract from
- `CONCEPT` is the id of the top level concept to extract

#### Example
For example, after downloading the RF2 release file `SnomedCT_InternationalRF2_PRODUCTION_20230131T120000Z.zip`,
a subset of the concept `387713003 |Surgical procedure|` can be extracted using the following command:
```
java -Xms2g -jar snomed-term-extractor.jar \
  --release-files=SnomedCT_InternationalRF2_PRODUCTION_20230131T120000Z.zip \
  --extract-concept-and-descendants=387713003
```
This will create a file named `SNOMED-CT_TermExtract_Surgical-procedure_20230131.txt`. [Here is an extract sample](https://gist.github.com/kaicode/66aee88e1549335b5f86a45bcb86803a) with the first 10 lines.
