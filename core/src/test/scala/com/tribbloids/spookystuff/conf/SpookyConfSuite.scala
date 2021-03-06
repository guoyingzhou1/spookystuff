package com.tribbloids.spookystuff.conf

import com.tribbloids.spookystuff.SpookyEnvFixture
import com.tribbloids.spookystuff.caching.DFSDocCache
import com.tribbloids.spookystuff.utils.ConfUtils
import org.apache.spark.SparkConf

import scala.util.Random

/**
  * Created by peng on 30/09/16.
  */
class SpookyConfSuite extends SpookyEnvFixture {

  def conf = new SpookyConf()
  def dirConf = new DirConf()

  it("SpookyConf is serializable") {

    assertSerDe(
      conf,
      condition = {
        (v1: SpookyConf, v2: SpookyConf) =>
          v1.cacheFilePath == v2.cacheFilePath
      }
    )
  }

  it("DirConf.import is serializable") {

    val sparkConf = new SparkConf()
    val dummyV = "dummy" + Random.nextLong()
    sparkConf.set("spooky.dirs.autosave", dummyV)
    val imported = dirConf.importFrom(sparkConf)
    assertSerDe(
      imported
    )
  }

  it("DirConf.import can read from SparkConf") {
    val sparkConf = new SparkConf()
    val dummyV = "dummy" + Random.nextLong()
    sparkConf.set("spooky.dirs.autosave", dummyV)
    DFSDocCache
    val imported = dirConf.importFrom(sparkConf)

    assert(imported.autoSave == dummyV)
  }

  it("getProperty() can load property from spark property") {
    val conf = sc.getConf
    val v = conf.get("dummy.property")

    try {
      assert(ConfUtils.getPropertyOrEnv("dummy.property") == Some(v))
    }
  }

  it("getProperty() can load property from system property") {

    System.setProperty("dummy.property", "AA")

    try {
      assert(ConfUtils.getPropertyOrEnv("dummy.property") == Some("AA"))
    }
    finally {
      System.setProperty("dummy.property", "")
    }
  }
}
