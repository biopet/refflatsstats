package nl.biopet.tools.refflatsstats

/**
  * Created by pjvanthof on 01/05/2017.
  */
case class RegionStats(start: Int, end: Int, gc: Double) {
  def length: Int = end - start + 1
}
