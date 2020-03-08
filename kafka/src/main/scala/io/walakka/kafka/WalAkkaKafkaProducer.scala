package io.walakka.kafka

import scala.concurrent.{ExecutionContext, Future}

class WalAkkaKafkaProducer[K, V](
    val kafkaProducer: KafkaProducer[K, V],
    val topic: String
) extends OutputProducer[K, V, RecordMetadata] {
  override def put(key: K, value: V)(
      implicit ec: ExecutionContext): Future[RecordMetadata] = {
    kafkaProducer.send(new ProducerRecord(topic, key, value)).asScala
  }
}
