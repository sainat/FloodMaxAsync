package utd.distributed.computing


import java.util.concurrent.{LinkedBlockingQueue}
import scala.collection.mutable

/**
  * Created by sainath on 4/17/2016.
  */
case  class Node(id : Int,
                 nbrs : mutable.Set[Node],
                 inMsgs : LinkedBlockingQueue[Message]) {

  override def toString: String = {
    if (nbrs.size > 0) {
      val nbrsIDs = {
        nbrs.map(_.id)
      }
      s"ID : $id " + "Nbrs --> \t" + nbrsIDs.mkString(",")
    }
    else
      s"ID : $id "
  }
  override def hashCode: Int = this.id.hashCode

  override def equals(that : Any): Boolean = {
    if(that.isInstanceOf[Node]) {
      if(id == that.asInstanceOf[Node].id  && id.hashCode() == that.asInstanceOf[Node].id.hashCode())
        true
      else
        false
    }
    else
      false
  }
}