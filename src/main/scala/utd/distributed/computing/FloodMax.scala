package utd.distributed.computing

import java.util.concurrent.CyclicBarrier

import java.util.concurrent.atomic.AtomicInteger
import java.util.{ArrayList, Collections}
import java.util.concurrent.{Phaser, LinkedBlockingQueue}

import scala.collection
import scala.collection.mutable
import scala.io.Source

/**
  * Created by sainath on 4/17/2016.
  */

object FloodMax {

  val round = new AtomicInteger(0)  // masters copy of round
  val presentRoundDoneThreads = new AtomicInteger(0)
  val prevRoundDoneThreads = new AtomicInteger(0)

  def main(args : Array[String]):Unit = {
    if (args.length < 1) {
      System.err.println("USAGE java FloodMax <path to connectivity.txt>")
      System.exit(1)
    }

    val (numThreads, nodesMap) = readDataFile(args(0))
    if(numThreads == 0 || nodesMap.isEmpty)
      println("Data source issue : something wrong while reading data file")
    val barrier = new CyclicBarrier(numThreads)
    val nodes = nodesMap.map(kv=>kv._2)
    val threads = for{
      node <- nodes
    } yield new Thread(new AsyncFMaxThread(node, node.nbrs, barrier), node.id.toString)
    threads.foreach{
      t =>
        t.start()
    }
  }

  def printEx(e: Exception): Any = {
    System.err.println(e.printStackTrace())
    System.exit(1)
  }
  def logDebug(text: String) = println(text)

  //read data from connect.txt
  def readDataFile(path:String): (Int, Map[Int, Node]) = {
    try {
      val input = Source.fromFile(path).getLines()
      val numThreads = input.next().trim().toInt
      val ids= input.next.split("\\s+")
      val nodes = ids.map(
        id =>
          Node(id.toInt, mutable.Set[Node](), new LinkedBlockingQueue[Message]())
      )
      val nodesMap = (Stream from 0).zip(nodes).toMap
      processConnectivity(input, numThreads, nodesMap)
      return (numThreads, nodesMap)
    }
    catch {
      case e:Exception => printEx(e) ; return (0, Map[Int,Node]())
    }
  }

  // reads the connectivity matrix for the graph from connect.txt
  def processConnectivity(input: Iterator[String],
                          numThreads: Int,
                          nodesMap: Map[Int, Node]) :Unit = {
    for(i <- 0 to numThreads -1) {
      val line = input.next().trim().split("\\s+")
      for (j <- i + 1 to numThreads - 1)
        if (line(j).toInt == 1){
          val (a, b) = (nodesMap(i), nodesMap(j))
          a.nbrs.add(b)   //   a <========> b is a bidirectional link so add to both neighbors list
          b.nbrs.add(a)
        }
      }
  }
}
