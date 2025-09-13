package io.github.j5ik2o.pcqrses.domain.support

trait AggregateRoot {
  type IdType <: AggregateId
  def id: IdType
}
