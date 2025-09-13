package io.github.j5ik2o.pcqrses.domain.support

trait EntityId {
  def entityTypeName: String
  def asString: String
}
