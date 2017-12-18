package nl.biopet.tools.refflatsstats

import java.io.File

import nl.biopet.utils.tool.{AbstractOptParser, ToolCommand}

class ArgsParser(toolCommand: ToolCommand[Args])
    extends AbstractOptParser[Args](toolCommand) {
  opt[File]('a', "annotationRefflat") required () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(refflatFile = x)
  } text "The refflat file used for annotation"
  opt[File]('R', "referenceFasta") required () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(referenceFasta = x)
  } text "The reference fasta file"
  opt[File]('g', "geneOutput") required () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(geneOutput = x)
  } text "Ouput file for genes"
  opt[File]('t', "transcriptOutput") required () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(transcriptOutput = x)
  } text "Output file for transcripts"
  opt[File]('e', "exonOutput") required () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(exonOutput = x)
  } text "Output file for exons"
  opt[File]('i', "intronOutput") required () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(intronOutput = x)
  } text "Output file for introns"
}
