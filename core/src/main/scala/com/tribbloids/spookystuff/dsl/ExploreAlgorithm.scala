package com.tribbloids.spookystuff.dsl

import com.tribbloids.spookystuff.actions._
import com.tribbloids.spookystuff.caching.ExploreRunnerCache
import com.tribbloids.spookystuff.execution.ExploreParams
import com.tribbloids.spookystuff.row._
import com.tribbloids.spookystuff.utils.CachingUtils.ConcurrentMap

sealed trait ExploreAlgorithm {

  def getImpl(
               params: ExploreParams,
               schema: SpookySchema
             ): ExploreAlgorithm.Impl
}

object ExploreAlgorithm {

  trait Impl extends Serializable {

    val params: ExploreParams
    val schema: SpookySchema

    /**
      *
      */
    val openReducer: RowReducer

    def openReducerBetweenEpochs: RowReducer = openReducer

    /**
      *
      */
    val visitedReducer: RowReducer //precede eliminator

    def visitedReducerBetweenEpochs: RowReducer = visitedReducer

    def openSelector(
                      open: ConcurrentMap[TraceView, Iterable[DataRow]]
                    ): (TraceView, Iterable[DataRow])
  }

  trait EliminatingImpl extends Impl {

    /**
      *
      */
    val ordering: RowOrdering

    /**
      *
      */
    def eliminator(
                    open: Iterable[DataRow],
                    visited: Iterable[DataRow]
                  ): Iterable[DataRow]

    override final def openSelector(
                                     open: ConcurrentMap[TraceView, Iterable[DataRow]]
                                   ): (TraceView, Iterable[DataRow]) = {

      //Should I use pre-sorted collection? Or is it overengineering?
      val bestOpenBeforeElimination: (TraceView, Iterable[DataRow]) = open.min(ordering)

      open -= bestOpenBeforeElimination._1

      val existingVisitedOption: Option[Iterable[DataRow]] =
        ExploreRunnerCache.get(bestOpenBeforeElimination._1 -> params.executionID, visitedReducer)

      val bestOpen: (TraceView, Iterable[DataRow]) = existingVisitedOption match {
        case Some(allVisited) =>
          val dataRowsAfterElimination = eliminator(bestOpenBeforeElimination._2, allVisited)
          bestOpenBeforeElimination.copy(_2 = dataRowsAfterElimination)
        case None =>
          bestOpenBeforeElimination
      }
      bestOpen
    }
  }
}

object ExploreAlgorithms {

  import ExploreAlgorithm._

  case object BreadthFirst extends ExploreAlgorithm {

    override def getImpl(
                          params: ExploreParams,
                          schema: SpookySchema
                        ) = Impl(params, schema)

    case class Impl(
                     override val params: ExploreParams,
                     schema: SpookySchema
                   ) extends EliminatingImpl {

      import params._

      import scala.Ordering.Implicits._

      override val openReducer: RowReducer = {
        (v1, v2) =>
          (v1 ++ v2)
            .groupBy(_.groupID)
            .values
            .minBy(_.head.sortIndex(Seq(depthField, ordinalField)))
      }

      override val visitedReducer: RowReducer = openReducer

      override val ordering: RowOrdering = Ordering.by {
        tuple: (TraceView, Iterable[DataRow]) =>
          val inProgress = ExploreRunnerCache
            .getOnGoingRunners(params.executionID)
            .flatMap(_.fetchingInProgressOpt)

          val result = if (inProgress contains tuple._1) {
            Int.MaxValue
          }
          else {
            val v = tuple._2
            //          assert(v.size == 1)
            v.head.getInt(depthField)
              .getOrElse(Int.MaxValue)
          }
          result
      }

      override def eliminator(
                               open: Iterable[DataRow],
                               visited: Iterable[DataRow]
                             ): Iterable[DataRow] = {
        val visitedDepth = visited.head.getInt(depthField)
        open.filter {
          row =>
            row.getInt(depthField) < visitedDepth
        }
      }
    }
  }

  //move reduce of openSet to elimination, should have identical result
  //case class ShortestPathImpl2(
  //                              depthField: IndexedField,
  //                              ordinalField: IndexedField,
  //                              extracts: Seq[Expression[Any]]
  //                            ) extends ExploreAlgorithmImpl {
  //
  //  import scala.Ordering.Implicits._
  //
  //  override def openReducer: RowReducer = {
  //    _ ++ _
  //  }
  //
  //  override def visitedReducer: RowReducer = {
  //    (v1, v2) =>
  //      Array((v1 ++ v2).minBy(_.sortIndex(Seq(depthField, ordinalField))))
  //  }
  //
  //  override def ordering: RowOrdering = Ordering.by{
  //    v: Iterable[DataRow] =>
  //      assert(v.size == 1)
  //      v.head.getInt(depthField).get
  //  }
  //
  //  override def eliminator: RowEliminator = {
  //    (v1, v2) =>
  //      assert(v2.size == 1)
  //      val visitedDepth = v2.head.getInt(depthField).get
  //      val filtered = v1.filter {
  //        row =>
  //          row.getInt(depthField).get < visitedDepth
  //      }
  //      if (filtered.isEmpty) filtered
  //      else Some(filtered.minBy(_.sortIndex(Seq(depthField, ordinalField))))
  //  }
  //}

  case object DepthFirst extends ExploreAlgorithm {

    override def getImpl(params: ExploreParams, schema: SpookySchema): Impl =
      Impl(params, schema)

    case class Impl(params: ExploreParams, schema: SpookySchema) extends EliminatingImpl {
      /**
        *
        */
      override val ordering: RowOrdering = ???

      /**
        *
        */
      override def eliminator(open: Iterable[DataRow], visited: Iterable[DataRow]): Iterable[DataRow] = ???

      /**
        *
        */
      override val openReducer: RowReducer = ???
      /**
        *
        */
      override val visitedReducer: RowReducer = ???
    }
  }
}

//TODO: finish these
//case object AllSimplePath extends ExploreOptimizer

//case object AllPath extends ExploreOptimizer {
//  override def openReducer: RowReducer = {_ ++ _}
//
//  //precede eliminator
//  override def ordering: RowOrdering = {_ ++ _}
//
//  override def visitedReducer: RowReducer = {_ ++ _}
//
//  override def eliminator: RowEliminator = {(v1, v2) => v1}
//}

//this won't merge identical traces and do lookup, only used in case each resolve may yield different result
//case class Dijkstra(
//
//                   ) extends ExploreOptimizer {
//  override def openReducer: RowReducer = ???
//
//  //precede eliminator
//  override def ordering: RowOrdering = ???
//
//  override def visitedReducer: RowReducer = ???
//
//  override def eliminator: RowEliminator = ???
//}