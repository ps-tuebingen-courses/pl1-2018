sealed abstract class Exp
case class Num(n: Int) extends Exp
case class Id(name: Symbol) extends Exp
case class Add(lhs: Exp, rhs: Exp) extends Exp
case class Fun(param: Symbol, body: Exp) extends Exp
implicit def num2exp(n: Int) = Num(n)
implicit def id2exp(s: Symbol) = Id(s)
case class App (funExpr: Exp, argExpr: Exp) extends Exp
case class Shift(param: Symbol, body: Exp) extends Exp
case class Reset(body: Exp) extends Exp

sealed abstract class Value
type Env = Map[Symbol, Value]
case class NumV(n: Int) extends Value
case class ClosureV(f: Fun, env: Env) extends Value
case class ContV(f: Value => Value) extends Value

def eval(e: Exp, env: Env, k: Value => Value) : Value = e match {
  case Num(n: Int) => k(NumV(n))
  case Id(x) => k(env(x))
  case Add(l,r) => {
    eval(l,env, lv => 
        eval(r,env, rv =>
          (lv,rv) match {
            case (NumV(v1), NumV(v2)) => k(NumV(v1+v2))
            case _ => sys.error("can only add numbers")
          }))
  }
  case f@Fun(param,body) => k(ClosureV(f, env))
  
  case App(f,a) => eval(f,env, cl => cl match {
            case ClosureV(f,closureEnv) => eval(a,env, av => eval(f.body, closureEnv + (f.param -> av),k))
            case ContV(f) => eval(a,env, av => k(f(av)))
            case _ => sys.error("can only apply functions")
  })
  case Reset(e) => k(eval(e,env,x=>x))
  case Shift(param,body) => eval(body, env+(param -> ContV(k)), x=>x) 
}
