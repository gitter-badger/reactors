package io.reactors
package events
package bench



import org.scalameter.api._
import org.scalameter.picklers.noPickler._



class EventBoxingBench extends Bench.Forked[Long] {
  override def defaultConfig: Context = Context(
    exec.minWarmupRuns -> 2,
    exec.maxWarmupRuns -> 5,
    exec.independentSamples -> 1,
    verbose -> false
  )

  def measurer: Measurer[Long] =
    for (table <- Measurer.BoxingCount.allWithoutBoolean()) yield {
      table.copy(value = table.value.valuesIterator.sum)
    }

  def aggregator: Aggregator[Long] = Aggregator.median

  override def reporter = Reporter.Composite(
    LoggingReporter(),
    ValidationReporter()
  )

  measure method "Emitter.onX" config (
    reports.validation.predicate -> { (n: Any) => n == 0 }
  ) in {
    using(Gen.single("numEvents")(10000)) in { numEvents =>
      var sum = 0
      val emitter = new Events.Emitter[Int]
      emitter.onEvent(sum += _)
      emitter.on(sum += 1)

      var i = 0
      while (i < numEvents) {
        emitter.react(i)
        i += 1
      }
    }
  }

  measure method "Emitter.toSignal" config (
    reports.validation.predicate -> { (n: Any) => n == 4 }
  ) in {
    using(Gen.single("numEvents")(10000)) in { numEvents =>
      val emitter = new Events.Emitter[Int]
      val s0 = emitter.toEmpty
      val s1 = emitter.toSignal(-1)

      var i = 0
      while (i < numEvents) {
        assert(s1() == i - 1)
        emitter.react(i)
        assert(s0() == i)
        i += 1
      }
    }
  }

  measure method "Emitter.<combinators>" config (
    reports.validation.predicate -> { (n: Any) => n == 19 }
  ) in {
    using(Gen.single("numEvents")(10000)) in { numEvents =>
      val emitter = new Events.Emitter[Int]

      // count
      val count = emitter.count.toEmpty

      // scanPast
      var scanPastCount = 0
      emitter.scanPast(0)(_ + _).onEvent(x => scanPastCount += 1)
      emitter.scanPast(0)(_ + _).on(scanPastCount += 1)
      emitter.scanPast(0)(_ + _).onDone({})

      // reducePast
      var reducePastCount = 0
      emitter.reducePast(0)(_ + _).onEvent(reducePastCount = _)

      // mutate
      object Cell {
        var x = 0
      }
      val cell = new Events.Mutable(Cell)
      val mutate = emitter.mutate(cell) { c => v =>
        c.x = v
      }
      val mutate2 = emitter.mutate(cell, cell) { (c1, c2) => v =>
        c2.x = c1.x
        c1.x = v
      }
      val mutate3 = emitter.mutate(cell, cell, cell) { (c1, c2, c3) => v =>
        c3.x = c2.x
        c2.x = c1.x
        c1.x = v
      }

      // after
      var a0 = 0
      val start = new Events.Emitter[Int]
      val after = emitter.after(start)
      after.on(a0 += 1)
      start.react(7)

      // until
      var u0 = 0
      val end = new Events.Emitter[Int]
      val until = emitter.until(end)
      until.on(u0 += 1)
      emitter.onEvent(x => if (x == 1000) end.react(x))

      // once
      var onceCount = 0
      val once = emitter.once
      once.on(onceCount += 1)
      once.onDone(onceCount += 1)

      // filter
      var filterCount = 0
      emitter.filter(_ % 2 == 1).on(filterCount += 1)

      // map
      var mapSum = 0
      emitter.map(_ + 1).onEvent(mapSum += _)

      // map to boolean
      var mapBooleanCount = 0
      emitter.map(_ > 0).on(mapBooleanCount += 1)

      // takeWhile
      var takeWhileDone = false
      emitter.takeWhile(_ < 1000).onDone(takeWhileDone = true)

      // dropWhile
      var dropWhileCount = 0
      emitter.dropWhile(_ < 1000).on(dropWhileCount += 1)

      // mux
      var sum = 0
      val muxEmitter = new Events.Emitter[Events.Emitter[Int]]
      muxEmitter.mux.onEvent(sum += _)
      muxEmitter.react(emitter)

      // unreacted
      var unreactCount = 0
      emitter.unreacted.onDone(unreactCount += 1)

      // union
      var unionCount = 0
      emitter.union(emitter).on(unionCount += 1)

      // concat
      var concatCount = 0
      emitter.concat(emitter).on(concatCount += 1)

      // sync
      var syncCount = 0
      val syncEmitter = new Events.Emitter[Int]
      emitter.sync(syncEmitter)(_ + _).on(syncCount += 1)

      // postfix union
      var postfixUnionCount = 0
      var postfixUnionEmitter = new Events.Emitter[Events.Emitter[Int]]
      postfixUnionEmitter.union.on(postfixUnionCount += 1)
      postfixUnionEmitter.react(emitter)

      // postfix concat
      var postfixConcatCount = 0
      var postfixConcatEmitter = new Events.Emitter[Events.Emitter[Int]]
      postfixConcatEmitter.concat.on(postfixConcatCount += 1)
      postfixConcatEmitter.react(emitter)

      // postfix first
      var postfixFirstEmitterCount = 0
      var postfixFirstEmitter = new Events.Emitter[Events.Emitter[Int]]
      postfixFirstEmitter.first.on(postfixFirstEmitterCount += 1)
      postfixFirstEmitter.react(emitter)

      // possibly
      emitter.possibly(0.5).on({})

      // changes
      var changeCount = 0
      emitter.toSignal(0).changes.on(changeCount += 1)

      // diffPast
      var diffPastCount = 0
      emitter.toSignal(0).diffPast(_ - _).on(diffPastCount += 1)

      // zip
      var zipCount = 0
      (emitter.toSignal(0) zip emitter.toSignal(0))(_ + _).on(zipCount += 1)

      // toCold
      var coldCount = 0
      emitter.toCold(0).on(coldCount += 1)

      // aggregate
      val asignal = emitter.toSignal(0)
      Signal.aggregate(asignal, asignal, asignal)(0)(_ + _).on({})

      var i = 0
      while (i < numEvents) {
        assert(filterCount == i / 2)
        emitter.react(i)
        if (i % 3 == 0) syncEmitter.react(i)
        assert(count() == i + 1)
        assert(Cell.x == i)
        assert(onceCount == 2)
        i += 1
      }
      emitter.unreact()
    }
  }

}
