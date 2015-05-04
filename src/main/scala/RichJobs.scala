package grid

import language.postfixOps
import language.implicitConversions

import grid.Filtering._
import grid.TypeImports._

import scalaz.std.AllInstances._
import scalaz.syntax.monoid._

object RichJobs extends RichJobs

trait RichJobs {

  /** @todo variable slot period */
  def timeslots[A](jobs: GenIterable[Job])
                  (f: Job ⇒ A, start: Job ⇒ DateTime, end: Job ⇒ DateTime)
                  : GenIterable[Map[DateTime,A]] = for {
    job ← jobs
    s   = start(job) withSecondOfMinute 0
    e   = end(job)   withSecondOfMinute 0
    d   = f(job)
  } yield (s to e by 1.minute map { _ → d } toMap)

  implicit class RichJobs(jobs: GenIterable[Job]) {
    def perMinute[A](f: Job ⇒ A): GenIterable[Map[DateTime,A]] = for {
      job ← jobs
      s   = job.time.start withSecondOfMinute 0
      e   = job.time.end   withSecondOfMinute 0
      d   = f(job)
    } yield (s to e by 1.minute map {
      _ → d
    } toMap)

    def toTimeValues[A](ftime: Job ⇒ Interval)(fdata: Job ⇒ A): GenIterable[(Interval,A)] = for {
      job      ← jobs
      interval = ftime(job)
      data     = fdata(job)
    } yield (interval → data)

    def toTimeslots(f: Job ⇒ Double)
                   (implicit interval: Option[Interval]): Map[DateTime,Double] = {
      import scalaz.Monoid

      implicit object DoubleMonoid extends Monoid[Double] {
        def zero = 0.0
        def append(x: Double, y: => Double) = x + y
      }

      val ts: GenIterable[Map[DateTime,Double]] = for {
        job   ← interval map { interval ⇒
                  jobs filter isBetween(interval)
                } getOrElse(jobs)
        start = job.time.start withSecondOfMinute 0
        end   = job.time.end   withSecondOfMinute 0
        data  = f(job)
      } yield (start to end by 1.minute map {
        _ → data
      } toMap)

      ts.fold(Map())(_ ⊹ _)
    }

    def toPendingVsRunning(implicit interval: Option[Interval]): Map[String,Map[DateTime,Int]] = {
      val filtered = interval map { interval ⇒
        jobs filter isBetween(interval)
      } getOrElse { jobs }

      Map (
        "waiting".localized → timeslots(filtered)(_.slots,_.time.submission,_.time.start).fold(Map())(_ ⊹ _),
        "running".localized → timeslots(filtered)(_.slots,_.time.start     ,_.time.end  ).fold(Map())(_ ⊹ _)
      )
    }

    def efficiency(f: Job ⇒ Double)(implicit interval: Option[Interval]) = {
      val filtered = interval map { interval ⇒
        jobs filter isBetween(interval)
      } getOrElse(jobs)

      val fSum      = filtered map f sum
      val wctimeSum = filtered map { _.res.wctime } sum

      fSum / wctimeSum
    }

    def averageBy[A: Numeric](f: Job ⇒ A): Double = {
      val num = implicitly[Numeric[A]]
      import num._

      val sum = jobs.aggregate(zero)(_ + f(_), _ + _)

      sum.toDouble / jobs.size
    }

    def waste(implicit interval: Option[Interval], slotmax: Int): GenMap[DateTime,(Int,Int)] = {
      val filtered = interval map { interval ⇒
        jobs filter isBetween(interval)
      } getOrElse(jobs)

      val stuff: GenIterable[Map[DateTime,(Int,Int)]] = for {
        job ← filtered

        sub = job.time.submission withSecondOfMinute 0
        sta = job.time.start      withSecondOfMinute 0
        end = job.time.end        withSecondOfMinute 0

        slo = job.slots

        wai = (sub to sta by 1.minute map { a => (a,(0,slo)) }).toMap
        run = (sta to end by 1.minute map { a => (a,(slo,0)) }).toMap
      } yield (wai ⊹ run)

      stuff.fold(Map())(_ ⊹ _)
    }
  }

  implicit class RichCategorizedJobs[A](groupedJobs: GenMap[A,GenIterable[Job]]) {
    def toTimeslots(f: Job ⇒ Double)
                   (implicit interval: Option[Interval]): Map[A,Map[DateTime,Double]] =
      groupedJobs.mapValues(_.toTimeslots(f)).seq.toMap

    def efficiency(implicit interval: Option[Interval]) = for {
      groupedJob   <- groupedJobs
      group        = groupedJob._1
      jobs         = groupedJob._2
      numjobs      = jobs.size
      ueff         = jobs.efficiency { j ⇒ (j.res.utime   / j.slots) }
      useff        = jobs.efficiency { j ⇒ (j.res.cputime / j.slots) }
    } yield (group,numjobs,ueff,useff)
  }
}
