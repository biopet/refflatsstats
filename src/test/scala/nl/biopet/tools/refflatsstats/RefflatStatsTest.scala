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

import java.io.File

import nl.biopet.utils.test.tools.ToolTest
import org.testng.annotations.Test

import scala.io.Source

class RefflatStatsTest extends ToolTest[Args] {
  def toolCommand: RefflatStats.type = RefflatStats
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
    lines should contain(
      "geneA\tchrQ\t201\t500\t0.49\t0.5\t0.47000000000000003\t300\t197")
    lines should contain(
      "geneB\tchrQ\t201\t500\t0.49\t0.5\t0.47000000000000003\t300\t197")
    lines should contain(
      "geneC\tchrQ\t202\t500\t0.4882943143812709\t0.5\t0.47000000000000003\t299\t197")
  }
}
