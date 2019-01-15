/*
 * Copyright (c) 2017 Biopet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package nl.biopet.tools.refflatsstats

import java.io.{File, PrintWriter}

import htsjdk.samtools.SAMSequenceDictionary
import htsjdk.samtools.reference.IndexedFastaSequenceFile
import nl.biopet.utils.ngs.fasta
import nl.biopet.utils.ngs.intervals.{BedRecord, BedRecordList}
import nl.biopet.utils.tool.ToolCommand
import picard.annotation.RefFlatReader.RefFlatColumns
import picard.annotation.{AnnotationException, Gene}
import picard.util.TabbedTextFileWithHeaderParser

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, TimeoutException}
import scala.collection.JavaConversions._

/**
  * Created by pjvan_thof on 1-5-17.
  */
object RefflatStats extends ToolCommand[Args] {

  def emptyArgs: Args = Args()
  def argsParser = new ArgsParser(this)

  /**
    * Program will prefix reads with a given seq
    *
    * @param args the command line arguments
    */
  def main(args: Array[String]): Unit = {
    val cmdArgs = cmdArrayToArgs(args)

    logger.info("Start")

    //Sets picard logging level
    htsjdk.samtools.util.Log
      .setGlobalLogLevel(
        htsjdk.samtools.util.Log.LogLevel.valueOf(logger.getLevel.toString))

    logger.info("Reading refflat file")

    val futures =
      getGenes(cmdArgs.refflatFile, fasta.getCachedDict(cmdArgs.referenceFasta))
        .map(generateGeneStats(_, cmdArgs.referenceFasta))

    val totalGenes = futures.length

    logger.info(s"$totalGenes genes found in refflat file")

    val f = Future.sequence(futures)

    def waitOnFuture(future: Future[List[GeneStats]]): List[GeneStats] = {
      try {
        Await.result(future, Duration(5, "seconds"))
      } catch {
        case _: TimeoutException =>
          logger.info(
            futures.count(_.isCompleted) + s" / $totalGenes genes done")
          waitOnFuture(future)
      }
    }

    val geneStats = waitOnFuture(f)

    logger.info("Writing output files")

    val geneWriter = new PrintWriter(cmdArgs.geneOutput)
    val transcriptWriter = new PrintWriter(cmdArgs.transcriptOutput)
    val exonWriter = new PrintWriter(cmdArgs.exonOutput)
    val intronWriter = new PrintWriter(cmdArgs.intronOutput)

    geneWriter.println(
      "gene\tcontig\tstart\tend\ttotalGC\texonGc\tintronGc\tlength\texonLength")
    transcriptWriter.println(
      "gene\ttranscript\tcontig\tstart\tend\ttotalGC\texonGc\tintronGc\tlength\texonLenth\tnumberOfExons")
    exonWriter.println("gene\ttranscript\tcontig\tstart\tend\tgc\tlength")
    intronWriter.println("gene\ttranscript\tcontig\tstart\tend\tgc\tlength")

    for (geneStat <- geneStats.sortBy(_.name)) {
      geneWriter.println(
        s"${geneStat.name}\t${geneStat.contig}\t${geneStat.start}\t${geneStat.end}\t${geneStat.totalGc}\t${geneStat.exonGc}\t${geneStat.intronGc
          .getOrElse(".")}\t${geneStat.length}\t${geneStat.exonLength}")
      for (transcriptStat <- geneStat.transcripts.sortBy(_.name)) {
        val exonLength = transcriptStat.exons.map(_.length).sum
        transcriptWriter.println(
          s"${geneStat.name}\t${transcriptStat.name}\t${geneStat.contig}\t" +
            s"${transcriptStat.start}\t${transcriptStat.end}\t" +
            s"${transcriptStat.totalGc}\t${transcriptStat.exonGc}\t${transcriptStat.intronGc
              .getOrElse(".")}\t${transcriptStat.length}\t$exonLength\t${transcriptStat.exons.length}")
        for (stat <- transcriptStat.exons) {
          exonWriter.println(
            s"${geneStat.name}\t${transcriptStat.name}\t${geneStat.contig}\t${stat.start}\t${stat.end}\t${stat.gc}\t${stat.length}")
        }
        for (stat <- transcriptStat.introns) {
          intronWriter.println(
            s"${geneStat.name}\t${transcriptStat.name}\t${geneStat.contig}\t${stat.start}\t${stat.end}\t${stat.gc}\t${stat.length}")
        }
      }
    }

    geneWriter.close()
    transcriptWriter.close()
    exonWriter.close()
    intronWriter.close()

    logger.info("Done")
  }

  def generateGeneStats(gene: Gene, fastaFile: File): Future[GeneStats] =
    Future {
      val referenceFile = new IndexedFastaSequenceFile(fastaFile)
      val contig = gene.getContig
      val start = List(gene.getStart, gene.getEnd).min
      val end = List(gene.getStart, gene.getEnd).max
      val gcCompleteGene =
        fasta.getSequenceGc(referenceFile, contig, start, end)

      val exons =
        geneToExonRegions(gene).distinct
          .map(exon => exon -> exon.getGc(referenceFile))
          .toMap
      val introns =
        geneToIntronRegions(gene).distinct
          .map(intron => intron -> intron.getGc(referenceFile))
          .toMap

      val exonicRegions = BedRecordList.fromList(exons.keys).combineOverlap
      val exonicGc = exonicRegions.getGc(referenceFile)
      val intronicRegions = BedRecordList.fromList(introns.keys).combineOverlap
      val intronicGc =
        if (intronicRegions.length > 0)
          Some(intronicRegions.getGc(referenceFile))
        else None

      val transcriptStats = for (transcript <- gene) yield {
        val start = List(transcript.start(), transcript.end()).min
        val end = List(transcript.start(), transcript.end()).max
        val gcCompleteTranscript =
          fasta.getSequenceGc(referenceFile, contig, start, end)

        val exonRegions = transcriptToExonRegions(transcript)
        val intronRegions = transcriptToIntronRegions(transcript)

        val exonicGc = BedRecordList
          .fromList(exonRegions)
          .combineOverlap
          .getGc(referenceFile)
        val intronicRegions =
          BedRecordList.fromList(intronRegions).combineOverlap
        val intronicGc =
          if (intronicRegions.length > 0)
            Some(intronicRegions.getGc(referenceFile))
          else None

        val exonStats =
          exonRegions.map(x => RegionStats(x.start, x.end, exons(x))).toArray
        val intronStats = intronRegions
          .map(x => RegionStats(x.start, x.end, introns(x)))
          .toArray

        TranscriptStats(transcript.name,
                        transcript.start(),
                        transcript.end(),
                        gcCompleteTranscript,
                        exonicGc,
                        intronicGc,
                        exonStats,
                        intronStats)
      }

      referenceFile.close()
      GeneStats(gene.getName,
                gene.getContig,
                gene.getStart,
                gene.getEnd,
                gcCompleteGene,
                exonicGc,
                intronicGc,
                exonicRegions.length.toInt,
                transcriptStats.toArray)
    }

  def geneToExonRegions(gene: Gene): List[BedRecord] = {
    (for (transcript <- gene) yield {
      transcriptToExonRegions(transcript)
    }).flatten.toList
  }

  def transcriptToExonRegions(transcript: Gene#Transcript): List[BedRecord] = {
    for (exon <- transcript.exons.toList) yield {
      val start = List(exon.start, exon.end).min
      val end = List(exon.start, exon.end).max
      BedRecord(transcript.getGene.getContig, start, end)
    }
  }

  def geneToIntronRegions(gene: Gene): List[BedRecord] = {
    (for (transcript <- gene) yield {
      transcriptToIntronRegions(transcript)
    }).flatten.toList
  }

  def transcriptToIntronRegions(
      transcript: Gene#Transcript): List[BedRecord] = {
    if (transcript.exons.length > 1) {
      (for (i <- 0 until (transcript.exons.length - 1)) yield {
        val intronStart = transcript.exons(i).end + 1
        val intronEnd = transcript.exons(i + 1).start - 1
        val start = List(intronStart, intronEnd).min
        val end = List(intronStart, intronEnd).max
        BedRecord(transcript.getGene.getContig, start, end)
      }).toList
    } else Nil

  }

  /**
    * Copied and converted to scala from Picard:
    * https://github.com/broadinstitute/picard/blob/master/src/main/java/picard/annotation/RefFlatReader.java
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
    * Copied and converted to scala from Picard:
    * https://github.com/broadinstitute/picard/blob/master/src/main/java/picard/annotation/RefFlatReader.java
    *
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
    * Copied and converted to scala from Picard:
    * https://github.com/broadinstitute/picard/blob/master/src/main/java/picard/annotation/RefFlatReader.java
    *
    * This method needed to be copied because it was private and, thus, couldn't be imported.
    *
    * This method constructs a Transcript object from a refFlat line, which gets added to gene.
    *
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

  def descriptionText: String =
    s"""
      |$toolName generates stats about an annotation refflat file.
      |It outputs stats files on genes, transcripts, exons and introns.
      |Information includes start,end information, GC content, number of
      |exonic regions etc.
    """.stripMargin

  def manualText: String =
    s"""
    |$toolName requires the refflat file and an indexed reference fasta to run.
    |If the reference is in `reference.fa` then a `reference.fai` and a
    |`reference.dict` must also be present.
    |
  """.stripMargin

  def exampleText: String =
    s"""
       | To output information on genes, transcripts, exons and introns:
       |${example(
         "-a",
         "MouseAnnotation.refflat",
         "-R",
         "IndexedMouseReference.fa",
         "-g",
         "GeneOutput.tsv",
         "-t",
         "TranscriptOutput.tsv",
         "-e",
         "ExonOutput.tsv",
         "-i",
         "IntronOutput.tsv"
       )}
     """.stripMargin
}
