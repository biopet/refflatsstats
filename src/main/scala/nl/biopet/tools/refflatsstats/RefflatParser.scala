/*
 * The following code was copied and converted into scala from Picard and modified to suite this
 * program's needs:
 * https://github.com/broadinstitute/picard/blob/master/src/main/java/picard/annotation/RefFlatReader.java
 *
 * The MIT License
 *
 * Copyright (c) 2011 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package nl.biopet.tools.refflatsstats

import java.io.File

import htsjdk.samtools.SAMSequenceDictionary
import nl.biopet.tools.refflatsstats.RefflatStats.logger
import picard.annotation.{AnnotationException, Gene}
import picard.annotation.RefFlatReader.RefFlatColumns
import picard.util.TabbedTextFileWithHeaderParser

import scala.collection.JavaConversions._

object RefflatParser {

  /**
    * (the load() method)
    *
    * This method is used instead of the GeneAnnotationReader.loadRefFlat method from Picard,
    * since this method ignores genes which overlap exactly with another gene (only one of the two
    * is returned).
    *
    * Reads the refFlat file and does some sanity/formatting checks.
    */
  def getGenes(refFlatFile: File,
               sequenceDictionary: SAMSequenceDictionary): List[Gene] = {
    val expectedColumns: Int = RefFlatColumns.values.length
    val parser: TabbedTextFileWithHeaderParser =
      new TabbedTextFileWithHeaderParser(refFlatFile,
                                         RefFlatColumns.values().map(_.name()))
    val refFlatLinesByGene = parser
      .flatMap(row => {
        val lineNumber
          : Int = parser.getCurrentLineNumber // getCurrentLineNumber returns the number of the next line
        if (row.getFields.length != expectedColumns)
          throw new AnnotationException(
            s"Wrong number of fields in refFlat file $refFlatFile at line $lineNumber")
        val geneName: String = row.getField(RefFlatColumns.GENE_NAME.name)
        val transcriptName: String =
          row.getField(RefFlatColumns.TRANSCRIPT_NAME.name)
        val transcriptDescription: String = s"$geneName:$transcriptName"
        val chromosome: String = row.getField(RefFlatColumns.CHROMOSOME.name)
        if (sequenceDictionary.getSequence(chromosome) == null) {
          logger.warn( // warn seems more appropriate then the original debug
            s"Skipping $transcriptDescription due to unrecognized sequence $chromosome")
          None
        } else Some(row)
      })
      .groupBy(_.getField(RefFlatColumns.GENE_NAME.name))

    val genes = refFlatLinesByGene.values
      .flatMap(transcriptLines => {
        try {
          val gene: Gene = makeGeneFromRefFlatLines(transcriptLines.toList)
          Some(gene)
        } catch {
          case e: AnnotationException =>
            logger.debug(e.getMessage + " -- skipping")
            None
        }
      })
      .toList
    genes
  }

  /**
    * This method needed to be copied because it was private and, thus, couldn't be imported.
    *
    * This method constructs a Gene object from a set of refFlat rows.
    */
  def makeGeneFromRefFlatLines(
      transcriptLines: List[TabbedTextFileWithHeaderParser#Row]): Gene = {
    val geneName =
      transcriptLines.get(0).getField(RefFlatColumns.GENE_NAME.name)
    val strandStr = transcriptLines.get(0).getField(RefFlatColumns.STRAND.name)
    val negative = strandStr == "-"
    val chromosome =
      transcriptLines.get(0).getField(RefFlatColumns.CHROMOSOME.name)
    // Figure out the extend of the gene
    val start = transcriptLines
      .map(_.getIntegerField(RefFlatColumns.TX_START.name) + 1)
      .min
    val end =
      transcriptLines.map(_.getIntegerField(RefFlatColumns.TX_END.name)).max
    val gene = new Gene(chromosome, start, end, negative, geneName)
    transcriptLines.foreach(row => {
      if (!(strandStr == row.getField(RefFlatColumns.STRAND.name)))
        throw new AnnotationException(
          s"Strand disagreement in refFlat file for gene $geneName")
      if (!(chromosome == row.getField(RefFlatColumns.CHROMOSOME.name)))
        throw new AnnotationException(
          s"Chromosome disagreement($chromosome != ${row.getField(
            RefFlatColumns.CHROMOSOME.name)}) in refFlat file for gene $geneName")
      // This adds it to the Gene also
      makeTranscriptFromRefFlatLine(gene, row)
    })
    gene
  }

  /**
    * This method needed to be copied because it was private and, thus, couldn't be imported.
    *
    * This method constructs a Transcript object from a refFlat line, which gets added to gene.
    *
    * Original comment:
    * Conversion from 0-based half-open to 1-based inclusive intervals is done here.
    */
  def makeTranscriptFromRefFlatLine(
      gene: Gene,
      row: TabbedTextFileWithHeaderParser#Row): Gene#Transcript = {
    val geneName = row.getField(RefFlatColumns.GENE_NAME.name)
    val transcriptName = row.getField(RefFlatColumns.TRANSCRIPT_NAME.name)
    val transcriptDescription = s"$geneName:$transcriptName"
    val exonCount = row.getField(RefFlatColumns.EXON_COUNT.name).toInt
    val exonStarts = row.getField(RefFlatColumns.EXON_STARTS.name).split(",")
    val exonEnds = row.getField(RefFlatColumns.EXON_ENDS.name).split(",")
    if (exonCount != exonStarts.length)
      throw new AnnotationException(
        s"Number of exon starts does not agree with number of exons for $transcriptDescription")
    if (exonCount != exonEnds.length)
      throw new AnnotationException(
        s"Number of exon ends does not agree with number of exons for $transcriptDescription")
    val transcriptionStart = row.getIntegerField(RefFlatColumns.TX_START.name) + 1
    val transcriptionEnd = row.getIntegerField(RefFlatColumns.TX_END.name)
    val codingStart = row.getIntegerField(RefFlatColumns.CDS_START.name) + 1
    val codingEnd = row.getIntegerField(RefFlatColumns.CDS_END.name)
    val tx = gene.addTranscript(transcriptName,
                                transcriptionStart,
                                transcriptionEnd,
                                codingStart,
                                codingEnd,
                                exonCount)
    (0 until exonCount).foreach(i => {
      val e = tx.addExon(exonStarts(i).toInt + 1, exonEnds(i).toInt)
      if (e.start > e.end)
        throw new AnnotationException(
          s"Exon has 0 or negative extent for $transcriptDescription")
      if (i > 0 && tx.exons(i - 1).end >= tx.exons(i).start)
        throw new AnnotationException(
          s"Exons overlap for $transcriptDescription")
    })
    tx
  }
}
