package io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql

import sangria.validation.ValueCoercionViolation

case object DateTimeCoercionViolation extends ValueCoercionViolation("DateTime expected")
