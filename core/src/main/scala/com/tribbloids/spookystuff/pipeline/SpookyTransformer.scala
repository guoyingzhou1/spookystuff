package com.tribbloids.spookystuff.pipeline

import com.tribbloids.spookystuff.row.DepthKey
import com.tribbloids.spookystuff.sparkbinding.PageRowRDD
import com.tribbloids.spookystuff.{PipelineException, SpookyContext}
import org.apache.spark.ml.param.{Param, ParamMap, ParamPair}
import org.slf4j.LoggerFactory

import scala.language.dynamics



trait SpookyTransformer extends SpookyTransformerLike with Dynamic {

  import com.tribbloids.spookystuff.dsl._

  override def copy(extra: ParamMap): SpookyTransformer = this.defaultCopy(extra)

  def +> (another: SpookyTransformer): TransformerChain = new TransformerChain(Seq(this)) +> another

  /*
  This dynamic function automatically add a setter to any Param-typed property
   */
  def applyDynamic(methodName: String)(args: Any*): this.type = {
    assert(args.length == 1)
    val arg = args.head

    //TODO: there is no need, all parameters are already defined in paramMap, remove this to promote more simple API
    if (methodName.startsWith("set")) {
      val fieldName = methodName.stripPrefix("set")
      val field = this.getClass.getMethod(fieldName) //this gets all the getter generated by Scala
      val value = field.invoke(this).asInstanceOf[Param[Any]]

      set(value, arg)
      this
    }
    else throw new PipelineException(s"setter $methodName doesn't exist")
  }

  //example value of parameters used for testing
  val exampleParamMap: ParamMap = ParamMap.empty

  def exampleInput(spooky: SpookyContext): PageRowRDD

  protected final def setExample(paramPairs: ParamPair[_]*): this.type = {
    paramPairs.foreach { p =>
      setExample(p.param.asInstanceOf[Param[Any]], p.value)
    }
    this
  }

  protected final def setExample[T](param: Param[T], value: T): this.type = {
    exampleParamMap.put(param -> value)
    this
  }

  //condition that has to be met to pass the test
  val conditionMap: ParamMap = ParamMap.empty

  override def test(spooky: SpookyContext): Unit= {

    this.exampleParamMap.toSeq.foreach {
      pair =>
        this.set(pair)
    }

    val result: PageRowRDD = this.transform(this.exampleInput(spooky)).persist()
    val keys = result.keySeq

    result.toDF(sort = true).show()

    keys.foreach{
      key =>
        val distinct = result.flatMap(_.get(key)).distinct()
        val values = distinct.take(2)
        assert(values.length >= 1)
        key match {
          case depthKey: DepthKey =>
            depthKey.maxOption.foreach {
              expectedMax =>
                assert(expectedMax == distinct.map(_.asInstanceOf[Int]).max())
            }
          case _ =>
        }
        LoggerFactory.getLogger(this.getClass).info(s"column '${key.name} has passed the test")
        result.unpersist()
    }

    assert(result.toObjectRDD(S_*).flatMap(v => v).count() >= 1)
  }
}