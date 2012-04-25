package grid

object SlotsPerByUser extends ChartingApp {
  def name = "slots-per-user"

  implicit val dataset = new org.jfree.data.category.DefaultCategoryDataset
  implicit val title = "Jobs per User"

  (
    interval map { implicit interval =>
      dispatched filter { isBetween(_) }
    } getOrElse {
      dispatched
    } groupBy {
      _.user.uid
    } map { x => // TODO mapValues
      x._1 -> x._2.size
    }
  ).toList sortBy {
    _._2
  } foreach { v =>
    dataset.addValue(v._2,v._1,interval.toString)
  }

  createLabelledBarChart saveAs extension
}