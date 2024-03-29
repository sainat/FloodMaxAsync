package utd.distributed.computing

import java.util.concurrent.{CyclicBarrier, Phaser}
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, HashMap, Queue}
import scala.collection.parallel.immutable
import scala.collection.immutable.Set
import scala.concurrent.Future
import scala.util.Random

/**
  * Created by sainath on 4/17/2016.
  */


class AsyncFMaxThread(me: Node, nbrs: mutable.Set[Node], barrier: Phaser) extends Runnable {

  var notifiedChildren = 0  // number of children that have notified that they are done with their processing and all their children are done processing
  var totalChildren = 0   // total number of nodes that notified me that they got their maxuid from me
  val children = mutable.Set[Int]()
  val nonChildren = mutable.Set[Int]()
  val doneChildren = mutable.Set[Int]()
  val talkedToNeighbors = mutable.Set[Int]()
  val nbrsSet = mutable.Set[Int]()
  var maxSeenSoFar: Int = me.id
  var leaderElected = false
  var round = 0  // global clock initally all start with 1 & report to the mater when they have read msgs with this round number
  var newInfo = true  // initially everyone needs to flood neighbors with thier
  val nbrNodesMap = new mutable.HashMap[Int, Node]()
  var delayedMessagesQueue = mutable.Queue[(Int, Message)]() // holds the (delay, message queue)
  val readyMsgsQueue = mutable.Queue[Message]()
  val random = new Random()
  var parent = this.me.id  // initally parent is me

  var doneSent = false

  // log levels
  val debug = false
  val info = true
  val trace = false

  nbrs.foreach(nbr => nbrNodesMap.put(nbr.id, nbr))
  nbrs.map(nbr => nbrsSet.add(nbr.id))

  override def run(): Unit = {
    while (!leaderElected){
      barrier.arriveAndAwaitAdvance()
      logTrace("Doing work ")
      if (newInfo) {
        floodNeighborsWithMaxUID(round)
        newInfo = false
      }
      //barrier.await()
      processMsgs(round)
      //barrier.await()
      sendMsgsForRound(round)
      //barrier.await()
    }
    while(!delayedMessagesQueue.isEmpty) {
      sendMsgsForRound(round)
      barrier.arriveAndAwaitAdvance()
    }
    barrier.arriveAndDeregister()
    logDebug(s" Done with algorithm ")
  }

  def isNodeDone(): Boolean = {
    if(doneChildren == children  && talkedToNeighbors == nbrsSet){
      return true
    }
    else
      return false
  }

  def floodNeighborsWithMaxUID(round :Int) = {
    nbrs
      .foreach(
        nbr =>
          addMsgToQueue(FloodMessage(me.id,nbr.id, round, maxSeenSoFar))
      )

  }



  def sendNak(destn: Int): Unit = {
    addMsgToQueue(ReplyMessage(me.id, destn, this.round, false))
  }

  def sendAck(destn: Int): Unit = {
    addMsgToQueue(ReplyMessage(me.id, destn, this.round, true))
  }

  def sendDoneMsg(logText: String) =
    if (!doneSent  && me.id != parent) {
      addMsgToQueue(DoneMessage(me.id, parent, this.round))
      doneSent = true
      logDebug(logText)
    }

  def checkIfDoneAndNotifyParent(): Unit = {
      if (children == nbrsSet && doneChildren == nbrsSet) {// i'm leader
        logInfo(s" ****************************    I AM THE LEADER -> $maxSeenSoFar ********************************")
        leaderElected = true
        floodChildrenWithLeaderMsg(LeaderMessage(me.id, me.id, this.round, me.id))
      }
      else if (nonChildren == (nbrsSet - parent) && talkedToNeighbors == nbrsSet) // leaf node termination
        sendDoneMsg(s"LEAF sending Done to ${parent}")

      else if (doneChildren == children && children.union(nonChildren) == nbrsSet)  //
        sendDoneMsg(s" non leaf Done -> parent : ${parent}")
  }

  def sendRemoveChild(id: Int) = {
    logDebug(s"sending remove child to ${id}")
    addMsgToQueue(RemoveChild(me.id, parent, this.round))
  }

  def processMsgs(round: Int): Unit = {
    while(!me.inMsgs.isEmpty){
      val msg = me.inMsgs.poll()
      logTrace(s" received ${msg}  at round $round")
      msg match {
        case floodMsg: FloodMessage =>
          talkedToNeighbors.add(floodMsg.source)
          if (floodMsg.max_uid <= maxSeenSoFar){
            sendNak(floodMsg.source)
          }
          else  {
            logDebug(s" got a new max_id -> ${floodMsg.max_uid}")
            maxSeenSoFar = floodMsg.max_uid
            if (parent != this.me.id) { // notify the previous parent that its no longer my parent  .. ignore if its the first round
              sendRemoveChild(parent)
            }
            parent = nbrNodesMap.getOrElse(floodMsg.source, this.me).id
            sendAck(parent)
            newInfo = true
          }

        case replyMsg: ReplyMessage =>
          talkedToNeighbors.add(replyMsg.source)
          if (replyMsg.ack) {
            nonChildren.remove(replyMsg.source)
            children.add(replyMsg.source)
          }
          else {
            nonChildren.add(replyMsg.source)
            children.remove(replyMsg.source)
          }
          checkIfDoneAndNotifyParent()
        case doneMsg : DoneMessage =>
          logDebug(s" got a DONE MSG from -> ${doneMsg.source}")
          doneChildren.add(doneMsg.source)
          checkIfDoneAndNotifyParent()
        case rmChild: RemoveChild =>
          logDebug(s" got a Remove MSG from -> ${rmChild.source}")
          children.remove(rmChild.source)
          nonChildren.add(rmChild.source)
        case lm: LeaderMessage =>
          logInfo(s" leader : ${lm.leaderUid}")
          floodChildrenWithLeaderMsg(LeaderMessage(me.id,me.id, round, lm.leaderUid))
          leaderElected = true
      }

    }
  }

  def floodChildrenWithLeaderMsg(lMsg: LeaderMessage) = {
    nbrsSet.foreach(
      child => {
        addMsgToQueue(lMsg.copy(destn = child, round = this.round))
        logDebug(s"sent Leader message to child ${child}  ")
      }
    )
  }



  //after decrementing each msg delay by 1, dequeu 0 delay msgs from delayQueue  and add to readyQueue
  // break whenever non zero delay is encountered .... preserves FIFO
  def sendMsgsForRound(round: Int) = {
    if(newInfo){
      floodNeighborsWithMaxUID(round)
    }

    delayedMessagesQueue = delayedMessagesQueue.map(kv => if(kv._1 != 0) (kv._1 - 1, kv._2) else kv)

    while(!delayedMessagesQueue.isEmpty && delayedMessagesQueue.head._1 == 0) {
      val delayedMsg: (Int, Message) = delayedMessagesQueue.dequeue()
      readyMsgsQueue.enqueue(delayedMsg._2)             // add to ready messages
    }
    while(!readyMsgsQueue.isEmpty)
      sendMsgToRecipient(readyMsgsQueue.dequeue())
  }

  // add messages to delayed msg queue with appropriate delay
  def addMsgToQueue(msg: Message) = {
    val delay = random.nextInt(19) + 1 // random delay b/w 1 to 20 units
    msg.round = delay + msg.round
    delayedMessagesQueue.enqueue((msg.round, msg))
  }

  def sendMsgToRecipient(msg: Message): Unit = {
    nbrNodesMap.get(msg.destn)
      .foreach(nbr => nbr.inMsgs.put(msg))  // used foreach
  }

  def logInfo(text: String) = if(info) println(s"${System.currentTimeMillis()}  Thread ${me.id} : $text\t parent : ${parent} ")
  def logDebug(text: String) = if(debug) println(s"${System.currentTimeMillis()}  Thread ${me.id} : $text\t parent is : ${parent} ")
  def logTrace(text: String) = if(trace) println(s"${System.currentTimeMillis()}  Thread ${me.id} : $text\tparent is : ${parent} ")
}