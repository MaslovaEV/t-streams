package com.bwsw.tstreamstransactionserver.netty.server.streamService

import java.nio.charset.StandardCharsets

case class StreamValue(name: String, partitions: Int, description: Option[String], ttl: Long)
  extends com.bwsw.tstreamstransactionserver.rpc.StreamValue
{
  def toByteArray: Array[Byte] = {
    val nameBodyFiledSize = java.lang.Integer.BYTES
    val partitionsFieldSize = java.lang.Integer.BYTES
    val descriptionFlagFieldSize = java.lang.Byte.BYTES
    val descriptionFieldSize = java.lang.Integer.BYTES
    val ttlFieldSize = java.lang.Long.BYTES

    val nameBodyBytes = name.getBytes(StreamValue.charset)
    val descriptionOptionFlag: Byte = description.map(_ => 1:Byte).getOrElse(0:Byte)
    val descriptionBodyBytes = description.map(_.getBytes(StreamValue.charset)).getOrElse(Array[Byte]())

    val buffer = java.nio.ByteBuffer.allocate(
      nameBodyFiledSize + nameBodyBytes.length + partitionsFieldSize +
        descriptionFlagFieldSize + descriptionFieldSize +
        ttlFieldSize + descriptionBodyBytes.length
    )

    buffer
      .putInt(name.length)
      .put(nameBodyBytes)
      .putInt(partitions)
      .put(descriptionOptionFlag)
      .putInt(descriptionBodyBytes.length)
      .put(descriptionBodyBytes)
      .putLong(ttl)
      .array()
  }
}

object StreamValue
{
  private val charset = StandardCharsets.UTF_8

  def fromByteArray(bytes: Array[Byte]): StreamValue = {
    val buffer = java.nio.ByteBuffer.wrap(bytes)
    val nameLength = buffer.getInt
    val name = {
      val bytes = new Array[Byte](nameLength)
      buffer.get(bytes)
      new String(bytes, charset)
    }
    val partitions = buffer.getInt
    val isDescriptionOptional = {
      val flag = buffer.get()
      if (flag == (1:Byte)) true else false
    }
    val descriptionLength = buffer.getInt
    val descriptionBody = {
      val bytes = new Array[Byte](descriptionLength)
      buffer.get(bytes)
      new String(bytes, charset)
    }
    val description =
      if (isDescriptionOptional)
        Some(descriptionBody)
      else
        None
    val ttl = buffer.getLong
    StreamValue(name, partitions, description, ttl)
  }
}