package io.github.j5ik2o.pcqrses.domain.support

trait AggregateId {
  def aggregateTypeName: String
  def asString: String
}
