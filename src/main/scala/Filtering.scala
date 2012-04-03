package grid

object Filtering extends Filtering

trait Filtering {
  lazy val ExcludeGIDs = "circular|root|wkdv|hpcworks|extusers".r
  lazy val epochstart  = new DateTime("1970-01-01T01:00:00.000+01:00")

  def nonAdminAndExternalGIDs(j: Job) = ExcludeGIDs.unapplySeq(j.user.gid).isEmpty

  def successful(j: Job) = j.status.successful
  def failed    (j: Job) = j.status.failed

  def parallel(j: Job) = j.parallelEnvironment.isDefined

  def isBetween(j: Job)(implicit interval: Interval) =
    (j.time.end isAfter interval.start) && (j.time.start isBefore interval.end)

  def realJob(j: Job) = j.queue.nonEmpty && j.node.nonEmpty  && (j.time.submission != epochstart)

  def isDispatched(j: Job) = j.res.wctime > 0

  def combined(j: Job) = nonAdminAndExternalGIDs(j) && realJob(j)
}
