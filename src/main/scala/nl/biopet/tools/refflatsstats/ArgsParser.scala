package nl.biopet.tools.refflatsstats

import java.io.File

import nl.biopet.utils.tool.AbstractOptParser

class ArgsParser(cmdName: String) extends AbstractOptParser[Args](cmdName) {
  opt[File]('a', "annotationRefflat") required () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(refflatFile = x)
  }
  opt[File]('R', "referenceFasta") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
    c.copy(referenceFasta = x)
  }
  opt[File]('g', "geneOutput") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
    c.copy(geneOutput = x)
  }
  opt[File]('t', "transcriptOutput") required () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(transcriptOutput = x)
  }
  opt[File]('e', "exonOutput") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
    c.copy(exonOutput = x)
  }
  opt[File]('i', "intronOutput") required () maxOccurs 1 valueName "<file>" action { (x, c) =>
    c.copy(intronOutput = x)
  }
}
