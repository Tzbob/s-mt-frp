package smtfrp.examples

import collection.{ immutable => i }
import scala.js.language._
import mtfrp.lang._
import spray.json.DefaultJsonProtocol
import mtfrp.lang.DatabaseFRPLib
import mtfrp.lang.DatabaseFunctionality
import scala.slick.driver.H2Driver

/**
 * Model the application domain
 */
trait TodoModel extends Adts with DefaultJsonProtocol with DatabaseFunctionality {
  import driver.simple._

  lazy val initialServerState = Nil

  case class Task(id: Option[Int], description: String) extends Adt
  implicit def taskOps(p: Rep[Task]) = adtOps(p)
  implicit val taskFormat = jsonFormat2(Task)
  val TaskRep = adt[Task]

  class Tasks(tag: Tag) extends Table[Task](tag, "TASK") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def description = column[String]("DESCRIPTION")
    def * = (id.?, description) <> (Task.tupled, Task.unapply)
  }
  val taskQuery = TableQuery[Tasks]

  type ServerState = List[Task]
  type TaskQuery = TableQuery[Tasks]
}

/**
 * Design the interface elements
 */
trait TodoInterface extends EasyHTML with TodoModel {
  lazy val addTask: Rep[Input] = text("What needs to be done?")

  def interface(state: Rep[ServerState]): Rep[Element] = el('div)(
    el('h1)("Todo Example"),
    el('div)(addTask),
    el('ol)(template(state)), el('hr)())

  def template(state: Rep[ServerState]): Rep[Element] = {
    def template(task: Rep[Task]): Rep[Element] = el('li)(task.description)
    el('ol)(state.map(template))
  }
}

/**
 * Design the updates of the state in the application
 */
trait TodoUpdate extends TodoModel with MtFrpProg {
  def toInsert(tq: TaskQuery, newDesc: String): TableManipulation =
    Insert(tq, Task(None, newDesc))
}

/**
 * Wrap everything together
 */
trait TodoCore extends TodoInterface with TodoUpdate with MtFrpProg with DatabaseFRPLib {
  lazy val values = addTask.values
  lazy val enters = addTask.toStream(KeyUp).filter(_.keyCode == 13)

  lazy val newTasks = values.sampledBy(enters)
  lazy val newServerTasks = newTasks.toServerAnon

  lazy val taskTableBehavior: TableBehavior[Tasks] =
    newServerTasks.toTableManipulation(taskQuery)(toInsert).toTableBehavior
  lazy val tasks: ServerBehavior[List[Task]] =
    taskTableBehavior.select(identity).map(_.toList)

  def main: ClientBehavior[Element] = tasks.toAllClients.map(interface)
}