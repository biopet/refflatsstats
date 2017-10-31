package nl.biopet.tools.refflatsstats

import java.io.File

import nl.biopet.utils.test.tools.ToolTest
import org.testng.annotations.Test

import scala.io.Source

class RefflatStatsTest extends ToolTest[Args] {
  @Test
  def test(): Unit = {
    val geneOutput = File.createTempFile("gene.", ".tsv")
    geneOutput.deleteOnExit()
    val transcriptOutput = File.createTempFile("transcript.", ".tsv")
    transcriptOutput.deleteOnExit()
    val exonOutput = File.createTempFile("exon.", ".tsv")
    exonOutput.deleteOnExit()
    val intronOutput = File.createTempFile("intron.", ".tsv")
    intronOutput.deleteOnExit()
    val refflatFile = new File(resourcePath("/chrQ.refflat"))
    val fastaFile = new File(resourcePath("/fake_chrQ.fa"))
    RefflatStats.main(
      Array(
        "--geneOutput",
        geneOutput.getAbsolutePath,
        "--transcriptOutput",
        transcriptOutput.getAbsolutePath,
        "--exonOutput",
        exonOutput.getAbsolutePath,
        "--intronOutput",
        intronOutput.getAbsolutePath,
        "--annotationRefflat",
        refflatFile.getAbsolutePath,
        "--referenceFasta",
        fastaFile.getAbsolutePath
      ))

    val lines = Source.fromFile(geneOutput).getLines().toList

    lines.head shouldBe "gene\tcontig\tstart\tend\ttotalGC\texonGc\tintronGc\tlength\texonLength"
    lines(1) shouldBe "geneA\tchrQ\t201\t500\t0.49\t0.5\t0.47000000000000003\t300\t197"

  }
}
