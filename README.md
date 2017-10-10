#  BIOPET tool suite
This tool is part of BIOPET tool suite that is developed at LUMC by [the SASC team](http://sasc.lumc.nl/).
Each tool in the BIOPET tool suite is meant to offer a standalone function that can be used to perform a
dedicate data analysis task or added as part of [BIOPET pipelines](http://biopet-docs.readthedocs.io/en/latest/).

#  About this tool
Refflatsstats generates stats about an annotation refflat file. It outputs stats files on genes, transcripts,
exons and introns. 

#  Installation
This tool requires Java 8 to be installed on your device. Download Java 8
[here](http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html)
or install via your distribution's package manager.

Download the latest version of refflatsstats [here](https://github.com/biopet/refflatsstats/releases).
To generate the usage run:
```bash
java -jar refflatstats-version.jar --help
```

#  Manual
Reflatsstats requires the refflat file and an indexed reference fasta to run. 

Example:
```bash
java -jar refflatsstats-version.jar \
-a MouseAnnotation.refflat \
-r IndexedMouseReference.fa  # .fai and .dict files should be present.\
-g GeneOutput.tsv \
-t TranscriptOutput.tsv \
-e ExonOutput.tsv \
-i IntronOutput.tsv
```

This will create the output files GeneOutput.tsv, TranscriptOutput.tsv,
ExonOutput,tsv and IntronOutput.tsv.

#  Contact


<p>
  <!-- Obscure e-mail address for spammers -->
For any question related to this tool, please use the github issue tracker or contact 
  <a href='http://sasc.lumc.nl/'>the SASC team</a> directly at: <a href='&#109;&#97;&#105;&#108;&#116;&#111;&#58;
 &#115;&#97;&#115;&#99;&#64;&#108;&#117;&#109;&#99;&#46;&#110;&#108;'>
  &#115;&#97;&#115;&#99;&#64;&#108;&#117;&#109;&#99;&#46;&#110;&#108;</a>.
</p>
