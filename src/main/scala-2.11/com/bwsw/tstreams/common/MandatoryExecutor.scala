package com.bwsw.tstreams.common

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{CountDownLatch, LinkedBlockingQueue}

import com.bwsw.ResettableCountDownLatch
import com.bwsw.tstreams.common.MandatoryExecutor.{MandatoryExecutorException, MandatoryExecutorTask}

/**
  * Executor which provides sequence runnable
  * execution but on any failure exception will be thrown
  */
class MandatoryExecutor {
  private val awaitSignalVar = new ResettableCountDownLatch(0)
  private val queue = new LinkedBlockingQueue[MandatoryExecutorTask]()
  private val isNotFailed = new AtomicBoolean(true)
  private val isShutdown = new AtomicBoolean(false)
  private var executor : Thread = null
  private var failureMessage : String = null
  startExecutor()

  /**
    * Mandatory task handler
    */
  private def startExecutor() : Unit = {
    val latch = new CountDownLatch(1)
    executor = new Thread(new Runnable {
      override def run(): Unit = {
        latch.countDown()

        //main task handle cycle
        while (isNotFailed.get()) {
          val task: MandatoryExecutorTask = queue.take()
          try {
            task.lock.foreach(x=>x.lock())
            task.runnable.run()
            task.lock.foreach(x=>x.unlock())
          }
          catch {
            case e: Exception =>
              task.lock.foreach(x=>x.unlock())
              isNotFailed.set(false)
              failureMessage = e.getMessage
          }
        }

        //release await in case of executor failure
        while (queue.size() > 0){
          val task = queue.take()
          if (!task.isIgnorableIfExecutorFailed){
            task.runnable.run()
          }
        }
      }
    })
    executor.start()
    latch.await()
  }

  /**
    * Submit new task to execute
    * @param runnable
    */
  def submit(runnable : Runnable, lock : Option[ReentrantLock]) = {
    if (isShutdown.get()){
      throw new MandatoryExecutorException("executor is been shutdown")
    }
    if (runnable == null) {
      throw new MandatoryExecutorException("runnable must be not null")
    }
    if (executor != null && !isNotFailed.get()){
      throw new MandatoryExecutorException(failureMessage)
    }
    queue.add(MandatoryExecutorTask(runnable, isIgnorableIfExecutorFailed = true, lock))
  }

  /**
    * Wait all current tasks to be handled
    * Warn! this method is not thread safe
    */
  def await() : Unit = {
    if (isShutdown.get()){
      throw new MandatoryExecutorException("executor is been shutdown")
    }
    if (executor != null && !executor.isAlive){
      throw new MandatoryExecutorException(failureMessage)
    }
    this.awaitInternal()
  }

  /**
    * Internal method for [[await]]
    */
  private def awaitInternal() : Unit = {
    awaitSignalVar.setValue(1)
    val runnable = new Runnable {
      override def run(): Unit = {
        awaitSignalVar.countDown()
      }
    }
    queue.add(MandatoryExecutorTask(runnable, isIgnorableIfExecutorFailed = false, lock = None))
    awaitSignalVar.await()
  }

  /**
    * Safe shutdown this executor (wit)
    */
  def shutdownSafe() : Unit = {
    if (isShutdown.get()){
      throw new MandatoryExecutorException("executor is already been shutdown")
    }
    isShutdown.set(true)
    this.awaitInternal()
  }
}

/**
  * Mandatory executor objects
  */
object MandatoryExecutor {
  class MandatoryExecutorException(msg : String) extends Exception(msg)
  case class MandatoryExecutorTask(runnable : Runnable,
                                   isIgnorableIfExecutorFailed : Boolean,
                                   lock : Option[ReentrantLock])
}





