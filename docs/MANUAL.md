# Manual

Reflatsstats requires the refflat file and an indexed reference fasta to run. 

Example:
```bash
java -jar refflatsStats-version.jar \
-a MouseAnnotation.refflat \
-r IndexedMouseReference.fa  # .fai and .dict files should be present.\
-g GeneOutput.tsv \
-t TranscriptOutput.tsv \
-e ExonOutput.tsv \
-i IntronOutput.tsv
```

This will create the output files GeneOutput.tsv, TranscriptOutput.tsv,
ExonOutput,tsv and IntronOutput.tsv.