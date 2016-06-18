package com.tribbloids.spookystuff.integration.select

import com.tribbloids.spookystuff.actions._
import com.tribbloids.spookystuff.dsl._
import com.tribbloids.spookystuff.integration.IntegrationSuite

/**
  * Created by peng on 11/26/14.
  */
class FlatSelectIT extends IntegrationSuite {

  override lazy val drivers = Seq(
    null
  )

  override def doMain() {

    val raw = spooky
      .fetch(
        Wget(HTML_URL)
      )
      .flatExtract(S"div.central-featured-lang")(
        'A.attr("lang"),
        A"a".href,
        A"a em".text,
        'A.uri
      )

    val result = raw
      .toDF(sort = true)

    assert(
      result.schema.fieldNames ===
        "_c1" ::
          "_c2" ::
          "_c3" ::
          "_c4" :: Nil
    )

    val rows = result.collect()

    assert(rows.length === 10)
    assert(rows.head.size === 4)
    assert(rows.head.getString(0) === "en")
    assert(rows.head.getString(1) contains "en.wikipedia.org/")
    assert(rows.head.getString(2) === "The Free Encyclopedia")
    assert(rows.head.getString(3) contains "spookystuff/test/Wikipedia.html")
  }

  override def numPages= 1

  override def numDrivers = 0
}