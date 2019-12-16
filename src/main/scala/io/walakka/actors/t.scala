package io.walakka.actors

object t extends App {
  def product(arr: Seq[Seq[Int]]): Seq[Seq[Int]] = {
    arr.tail.foldLeft(arr.head.map(Seq(_)))((out, s) => {
      out.flatMap(l => s.map(n => l :+ n))
    })
  }

  print(product(Seq(Seq(1,2,3), Seq(4,5,6)))
}
