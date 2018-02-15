organization := "com.github.biopet"
organizationName := "Biopet"

startYear := Some(2017)

name := "RefflatStats"
biopetUrlName := "refflatstats"

biopetIsTool := true

mainClass in assembly := Some("nl.biopet.tools.refflatsstats.RefflatStats")

developers += Developer(id="ffinfo", name="Peter van 't Hof", email="pjrvanthof@gmail.com", url=url("https://github.com/ffinfo"))
developers += Developer(id="rhpvorderman", name="Ruben Vorderman", email="r.h.p.vorderman@lumc.nl", url=url("https://github.com/rhpvorderman"))

scalaVersion := "2.11.11"

libraryDependencies += "com.github.biopet" %% "tool-utils" % "0.3-SNAPSHOT" changing()
libraryDependencies += "com.github.biopet" %% "tool-test-utils" % "0.2-SNAPSHOT" % Test changing()
libraryDependencies += "com.github.biopet" %% "ngs-utils" % "0.3-SNAPSHOT" changing()
libraryDependencies += "com.github.broadinstitute" % "picard" % "2.14.1"
