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

import htsjdk.samtools.reference.IndexedFastaSequenceFile
import nl.biopet.utils.ngs.fasta
import nl.biopet.utils.ngs.intervals.{BedRecord, BedRecordList}
import nl.biopet.utils.tool.ToolCommand
import picard.annotation.Gene

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
      RefflatParser
        .getGenes(cmdArgs.refflatFile,
                  fasta.getCachedDict(cmdArgs.referenceFasta))
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
