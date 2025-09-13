package io.github.j5ik2o.pcqrses.command.domain.support

trait Entity {
  type IdType <: EntityId
  def id: IdType
}
