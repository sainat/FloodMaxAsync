package utd.distributed.computing

/**
  * Created by sainath on 4/19/2016.
  */

// Message models to be used for inter Node(Thread) communication

/* Basic message structure
* All messages will mixin this trait*/
trait Message {

  val source: Int
  val destn: Int
  var round: Int
/*
  override def toString(): String = {
    s"source : $source, destination : $destn, round: $round"
  }
*/

}

case  class  FloodMessage(source: Int,
                          destn: Int,
                          var round: Int,
                          max_uid: Int) extends Message

case class ReplyMessage(source: Int ,
                        destn: Int ,
                        var round: Int,
                        ack: Boolean) extends  Message


case class LeaderMessage(source: Int ,
                         destn: Int ,
                         var round: Int ,
                         leaderUid: Int) extends Message

case class DoneMessage(source: Int,
                       destn: Int,
                       var round: Int) extends Message

case class RemoveChild(source: Int,
                       destn: Int,
                       var round: Int) extends Message


