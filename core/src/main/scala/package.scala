package object grid extends org.scala_tools.time.Imports {
  def verbosef[A](f: String, a: A): A = {
    Console.err.println(f.format(a.toString))
    a
  }

  import collection.GenMap
  implicit def mapwrap[A,B](m: GenMap[A,B]) = new MapWrapper(m)
  class MapWrapper[A,B](m: GenMap[A,B]) {
    def mapValues[C](f: B => C): GenMap[A,C] = m map { x => x._1 -> f(x._2) }
  }

  class BundleString(s: String) {
    def localized = try {
      java.util.ResourceBundle getBundle "PABundle" getString s
    } catch {
      case _ => s
    }
  }

  implicit def bundelize(s: String): BundleString = new BundleString(s)
}
