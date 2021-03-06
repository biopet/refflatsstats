organization := "com.github.biopet"
organizationName := "Biopet"

startYear := Some(2017)

name := "RefflatStats"
biopetUrlName := "refflatstats"

biopetIsTool := true

mainClass in assembly := Some("nl.biopet.tools.refflatsstats.RefflatStats")

developers += Developer(id = "ffinfo",
                        name = "Peter van 't Hof",
                        email = "pjrvanthof@gmail.com",
                        url = url("https://github.com/ffinfo"))
developers += Developer(id = "rhpvorderman",
                        name = "Ruben Vorderman",
                        email = "r.h.p.vorderman@lumc.nl",
                        url = url("https://github.com/rhpvorderman"))
developers += Developer(id = "DavyCats",
                        name = "Davy Cats",
                        email = "davycats.dc@gmail.com",
                        url = url("https://github.com/DavyCats"))

excludeFilter.in(headerSources) := HiddenFileFilter || "*RefflatParser.scala"

scalaVersion := "2.11.12"

libraryDependencies += "com.github.biopet" %% "tool-utils" % "0.6"
libraryDependencies += "com.github.biopet" %% "tool-test-utils" % "0.3"
libraryDependencies += "com.github.biopet" %% "ngs-utils" % "0.6"
libraryDependencies += "com.github.broadinstitute" % "picard" % "2.18.23"
