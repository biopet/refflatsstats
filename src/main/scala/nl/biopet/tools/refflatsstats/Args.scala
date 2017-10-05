package nl.biopet.tools.refflatsstats

import java.io.File

case class Args(refflatFile: File = null,
                referenceFasta: File = null,
                geneOutput: File = null,
                transcriptOutput: File = null,
                exonOutput: File = null,
                intronOutput: File = null)
