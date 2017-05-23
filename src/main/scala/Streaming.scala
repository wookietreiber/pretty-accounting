package grid

import fs2._
import java.nio.file._

import Filtering._

object Streaming extends Streaming

trait Streaming {

  // TODO throw out of this API and move to app config
  def accountingFile: String = sys.props get "grid.accounting.file" getOrElse {
    sys.env.getOrElse("SGE_ROOT", "/usr/local/sge") + "/default/common/accounting"
  }

  // TODO remove chunk size magic number / make customizable in app
  def lines(path: Path): Stream[Task,String] = {
    io.file.readAll[Task](path, chunkSize = math.pow(2,20).toInt)
      .through(text.utf8Decode)
      .through(text.lines)
  }

  def lines: Stream[Task,String] =
    lines(Paths.get(accountingFile))

  def raw: Stream[Task,Job] =
    lines collect {
      case AccountingEntry(job) => job
    }

  def jobs: Stream[Task,Job] = {
    val exclude = ExcludeGIDsRegex
    raw filter combined(exclude)
  }

  def dispatched: Stream[Task,Job] =
    jobs filter isDispatched

  // TODO make the whole job parsing stuff lazy, because usually one only needs a few columns

  object AccountingEntry {
    import Job._

    private val UNKNOWN = "UNKNOWN"
    private val NONE    = "NONE"

    // TODO parse first line of accounting file! contains grid engine version!

    //                           queue   node    u(gid  uid  ) j(name id   ) acc     prio    t(sub start end   ) st(fail exit) ru(wc  utime stime mrss  ixrss ismrs idrss isrss miflt maflt nswap inblk oublk msgsn msgrc nsig  nvcsw nicsw) acl(proj dep) pe      slots   task    cpu     mem     io      req  iow     pe_id   maxvmem marss mapss NONE? delby ar(id    sub) subno NONE? subcm wallc ioops?
    private[grid] val uge84 = """([^:]+):([^:]+):([^:]+:[^:]+):([^:]+:[^:]+):([^:]+):([^:]+):([^:]+:[^:]+:[^:]+):([^:]+:[^:]+):([^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+):([^:]+:[^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):(.+):([^:]+):([^:]+):([^:]+):[^:]+:[^:]+:[^:]+:[^:]+:([^:]+:[^:]+):[^:]+:[^:]+:[^:]+:[^:]+:[^:]+""".r

    //                                   queue   node    u(gid  uid  ) j(name id   ) acc     prio    t(sub start end   ) st(fail exit) ru(wc  utime stime mrss  ixrss ismrs idrss isrss miflt maflt nswap inblk oublk msgsn msgrc nsig  nvcsw nicsw) acl(proj dep) pe      slots   task    cpu     mem     io      req  iow     pe_id   maxvmem marss mapss NONE? delby ar(id    sub) subno NONE? subcm wallc
    private[grid] val ugeeightEntry = """([^:]+):([^:]+):([^:]+:[^:]+):([^:]+:[^:]+):([^:]+):([^:]+):([^:]+:[^:]+:[^:]+):([^:]+:[^:]+):([^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+):([^:]+:[^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):(.+):([^:]+):([^:]+):([^:]+):[^:]+:[^:]+:[^:]+:[^:]+:([^:]+:[^:]+):[^:]+:[^:]+:[^:]+:[^:]+""".r

    //                                        queue   host   u(gid   uid  )j(name  id   ) acc     prio   t(sub   start end  ) st(fail exit) ru(wc  utime stime maxr  ixr   ismr  idr   isr   minfl majfl nswap inblk oublk msgsn msgrc signs nvcsw nvcsw)a(proj  dep  ) pe      slots   taskid  cpu     mem     io      req  iow     pe_id   maxvmem ar(id  sub)
    private[grid] val sixtwoufiveEntry   = """([^:]+):([^:]+):([^:]+:[^:]+):([^:]+:[^:]+):([^:]+):([^:]+):([^:]+:[^:]+:[^:]+):([^:]+:[^:]+):([^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+):([^:]+:[^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):(.+):([^:]+):([^:]+):([^:]+):([^:]+:[^:]+)""".r

    private[grid] val sixzeroueightEntry = """([^:]+):([^:]+):([^:]+:[^:]+):([^:]+:[^:]+):([^:]+):([^:]+):([^:]+:[^:]+:[^:]+):([^:]+:[^:]+):([^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+:[^:]+):([^:]+:[^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):([^:]+):(.+):([^:]+):([^:]+):([^:]+)""".r

    def unapply(s: String) = s match {

      case uge84(queue, node, user, jobId, account, priority, time, status, res, acl,
      parallelEnvironment, slots, taskNumber, cpu, mem, io, resReq, iow, parallelTaskId, maxvmem,
      reservation) =>

      Some(Job(
        queue match { case UNKNOWN ⇒ None ; case queue ⇒ Some(queue) },
        node  match { case UNKNOWN ⇒ None ; case node  ⇒ Some(node)  },
        User(user),
        JobId(jobId, taskNumber),
        account,
        priority.toDouble,
        Time.milliseconds(time),
        Status(status),
        ResourceUsage(res,cpu,mem,maxvmem,io,iow),
        Acl(acl),
        parallelEnvironment match { case NONE ⇒ None ; case s ⇒ Some(s) },
        slots.toInt,
        resReq         match { case NONE ⇒ None ; case s ⇒ Some(s) },
        parallelTaskId match { case NONE ⇒ None ; case s ⇒ Some(s) },
        Reservation(reservation)
      ))

      case ugeeightEntry(queue, node, user, jobId, account, priority, time, status, res, acl,
      parallelEnvironment, slots, taskNumber, cpu, mem, io, resReq, iow, parallelTaskId, maxvmem,
      reservation) =>

      Some(Job(
        queue match { case UNKNOWN ⇒ None ; case queue ⇒ Some(queue) },
        node  match { case UNKNOWN ⇒ None ; case node  ⇒ Some(node)  },
        User(user),
        JobId(jobId, taskNumber),
        account,
        priority.toDouble,
        Time.milliseconds(time),
        Status(status),
        ResourceUsage(res,cpu,mem,maxvmem,io,iow),
        Acl(acl),
        parallelEnvironment match { case NONE ⇒ None ; case s ⇒ Some(s) },
        slots.toInt,
        resReq         match { case NONE ⇒ None ; case s ⇒ Some(s) },
        parallelTaskId match { case NONE ⇒ None ; case s ⇒ Some(s) },
        Reservation(reservation)
      ))

      case sixtwoufiveEntry(queue, node, user, jobId, account, priority, time, status, res, acl,
      parallelEnvironment, slots, taskNumber, cpu, mem, io, resReq, iow, parallelTaskId, maxvmem,
      reservation) ⇒

      Some(Job(
        queue match { case UNKNOWN ⇒ None ; case queue ⇒ Some(queue) },
        node  match { case UNKNOWN ⇒ None ; case node  ⇒ Some(node)  },
        User(user),
        JobId(jobId, taskNumber),
        account,
        priority.toDouble,
        Time.seconds(time),
        Status(status),
        ResourceUsage(res,cpu,mem,maxvmem,io,iow),
        Acl(acl),
        parallelEnvironment match { case NONE ⇒ None ; case s ⇒ Some(s) },
        slots.toInt,
        resReq         match { case NONE ⇒ None ; case s ⇒ Some(s) },
        parallelTaskId match { case NONE ⇒ None ; case s ⇒ Some(s) },
        Reservation(reservation)
      ))

      case sixzeroueightEntry(queue, node, user, jobId, account, priority, time, status, res, acl,
      parallelEnvironment, slots, taskNumber, cpu, mem, io, resReq, iow, parallelTaskId, maxvmem) ⇒

      Some(Job(
        queue match { case UNKNOWN ⇒ None ; case queue ⇒ Some(queue) },
        node  match { case UNKNOWN ⇒ None ; case node  ⇒ Some(node)  },
        User(user),
        JobId(jobId, taskNumber),
        account,
        priority.toDouble,
        Time.seconds(time),
        Status(status),
        ResourceUsage(res,cpu,mem,maxvmem,io,iow),
        Acl(acl),
        parallelEnvironment match { case NONE ⇒ None ; case s ⇒ Some(s) },
        slots.toInt,
        resReq         match { case NONE ⇒ None ; case s ⇒ Some(s) },
        parallelTaskId match { case NONE ⇒ None ; case s ⇒ Some(s) },
        None
      ))

      // TODO report on failed parsing
      case _ ⇒ None
    }
  }

}