# Additional material #1: Binding and substitution

## 1 Introduction

A programming language can use the concept of variables to provide a mechanism to reduce redundancy.
For instance, the `With` construct can be used by programmers to eliminate common subexpressions.

```scala
Add(Add(6, Add(5, 3)), Add(5, 3))
```

can then be written more concisely as:

```scala
With('x, Add(5,3), Add(Add(6, 'x), 'x))
```

where the common subexpression `Add(5,3)` is bound by `'x` and does not need to be repeated.

## 2 `With`, part 1: Implementing the `eval` function

There are at least two common ways to implement `With`, with a substitution-based
interpreter and with an environment-based interpreter. Here we only consider the former.

Let us start with the interpreter for the arithmetic expression (AE) language
and add a to-be-implemented case for `With` (and one for variables that just produces an error,
since freestanding variables have no sensible meaning).

```scala
def eval(e: Expr): Int = e match {
  case Num(n)              => n
  case Add(l,r)            => eval(l) + eval(r)
  case Var(x)              => sys.error("unbound variable: " + x)
  case With(x, xDef, body) => ???
}
```

As a first approximation, the evaluation of the `With` expression can be paraphrased as:

> Where the variable (in the example `'x`) appears in the body (the third argument to `With`),
> put the result of evaluating what the variable is bound to (as an expression that packs up this value,
> i.e. in our case a `Num`).

There is one important word missing in this paraphrase, but we will come back to that soon.

We can approach the problem of implementing the `eval` case for `With` top-down by assuming that we
already have a function that carries out this replacement.
We will from now on use the technical term _substitution_ for this replacement.
Assuming such a `subst` function

```scala
// Inside e, substitute i with v
def subst(e: Expr, i: Symbol, v: Num) = ???
```

we can straightforwardly translate our paraphrase into code:

```scala
  case With(x, xDef, body) => eval(subst(body, x, Num(eval(xDef))))
```

## 3 `With`, part 2: Implementing the `subst` function, shadowing, free variables

Now let us try to implement the `subst` function to complete our interpreter.
We start with pattern matching on the three possible cases of expressions `e`
(into which will be substituted):

```scala
def subst(e: Expr, i: Symbol, v: Num): Exp = e match {
  case Num(n)              => ???
  case Add(l,r)            => ???
  case Var(x)              => ???
  case With(x, xDef, body) => ???
}
```

Let us first take care of the easy cases:
- For `Num` we do not have to do anything, since there is no variable around,
so we just return `e`.
- For `Add` it suffices to recursively apply the subsitution within
both sides.
- For `Var`, obviously, we check whether the variable to be substituted
is the variable that the expression represents. If yes, we just return the value `v`,
otherwise the variable stays as it is.

```scala
def subst(e: Expr, i: Symbol, v: Num): Exp = e match {
  case Num(n)              => e
  case Add(l,r)            => Add(subst(l, x, v), subst(r, x, v))
  case Var(x)              => if (x == i) v else e
  case With(x, xDef, body) => ???
}
```

For the `With` case, we need to reconsider our paraphrase of what evaluation of `With`
expressions does. We said that we want to replace the variable where it appears in the body
of the `With`. But this body may itself contain `With` expressions which might bind the 
same variable (!):

```scala
With('x, Add(5,3), With('x, Add(4,7), 'x))
```

Thus we need to decide how to interpret such expressions where a variable expression
is bound at multiple `With`s. We will use the following rule for that:

> A variable occurence is bound at the first `With` encountered when going outward from it (through the abstract syntax tree).

We say that all bindings that are more outward than this first binding of the variable are _shadowed_.

Our example should thus evaluate to 11, not to 8, since the `'x` bound by the first `With` is shadowed
and therefore disregarded in our example.

Another way to look at the situation is that we are only interested in replacing those variables
in the body of a let that are not already bound somewhere in the body. These variables are called
the _free_ variables of the body.

With this in mind, we can now complete our paraphrase by adding the word "free":

> Where the variable appears **free** in the body,
> put the result of evaluating what the variable is bound to.

This in turn lets us complete the definition of `subst`:

```scala
  case With(x, xDef, body) => With(x, subst(body, i, v), if (x == i) body else subst(body, i, v)
```

We substitute within the definition for the variable bound by the `With` (`x`), and within the body,
but only if this `With` does not itself shadow the variable to be substituted (i.e., it is not `x == i`).
We just keep the body as it is if the variable is shadowed.

This completes our implementation of the `With` construct.

## 4 Desugaring `LetStar`

We will not discuss `Let` in detail again. It is just a rather simple generalization of `With`
to multiple bound variables. There is not anything fancy going on, it should only be noted that
the variables are only bound in the body, not in the other definitions of the `Let`.

The shadowing check from `With` can also be simply generalized to looking through the entire list
of bound variables whether the variable to be substituted is among them:

```scala
val xs = defs.map(_._1).toSet   // get all the variables bound by the Let by projecting the first argument of the defs
if (xs contains i) body else ...
```

Now, on to `LetStar` where each variable is indeed bound not only in the body, but also
in all the definitions that follow the definition of the variable (reading them from left to right).
Example:

```scala
LetStar(List('x -> Add(4,1), 'y -> Add('x, 2)), Add('x, 'y))
```

Here `'x` is bound in both the definition for `'y` and the body of the `LetStar`.
Hence this expression evaluates to 5 + (5 + 2) = 12.

Note that this evaluation behavior is the same when we rewrite the example as follows:

```scala
LetStar(List('x -> Add(4,1)), LetStar(List('y -> Add('x, 2)), Add('x, 'y)))
```

Here we just split up the list such that each of the variable definitions has its own
"personal" `LetStar`. This rewriting will always work, for any `LetStar`;
always the first definition will bind into the rest of the definitions just as
we wanted it to.

We also retain the evaluation behavior, for the same reason,
if the `LetStar`s are replaced by `Let`s:

```scala
Let(List('x -> Add(4,1)), Let(List('y -> Add('x, 2)), Add('x, 'y)))
```

We can thus observe that `LetStar` is really just a convenience for the
programmer that allows avoiding nested `Let`s!

We have already seen that desugaring comes in handy in such cases.
After considering our example, the desugaring of `LetStar` into `Let`s
should be straightforward:

```scala
def letStar(defs: List[(Symbol, Exp)], body: Exp): Exp =
  defs match {
    case Nil =>
      body
    case (x, xDef) :: rest =>
      Let(List(x -> xDef), letStar(rest, body))
  }
```

## 5 `LetStar`, directly

So we saw that we can easily desugar `LetStar` into `Let`, such that we do not
actually _need_ to consider a different way of implementing `LetStar`.

Let us do that nevertheless and see how the desugaring can even guide our
standalone `LetStar` implementation.

For the `eval` function, we can pretty much steal the entire idea from
the desugaring:

```scala
  case LetStar((x, xDef) :: defs, body) => eval(subst(LetStar(defs, body), x, Num(eval(xDef))))
  case LetStar(Nil, body)               => body
```

The only difference is that instead of inserting a `Let` at the appropriate position,
we do directly what that `Let` would be doing. That is, we substitute the first definition
into the remainder of the `LetStar`, and then evaluate the entire thing.

Like the `letStar` function in the desugaring, this process will eventually terminate since
the number of definitions is reduced by one at each step, finally arriving at the empty list.
In this empty list case we just produce the body unchanged.

(We could also implement `LetStar` with a fold over the definitions instead of having
the two cases for cons and empty list within the `eval` function.)

What is left is the implementation of substitution in the `LetStar` case of the `subst` function.

Even though not as straightforwardly, even here, we can make use of the fact that `LetStar` desugars into nested `Let`s:

```scala
// Inside e, substitute i with v
def subst(e: Expr, i: Symbol, v: Num) = e match {
  ...
  case LetStar((x, xDef) :: defs, body) => {
    // Step 1
    val restLetStar = LetStar(defs, body)
    // Step 2
    val substIntoRest = if (x == i) restLetStar else subst(restLetStar, i, v)
    substIntoRest match {
      // Step 3
      case LetStar(defs, body) => LetStar((x, subst(xDef, i, v)) :: defs, body)
    }
  }
  case LetStar(Nil, body)               => LetStar(Nil, subst(body, i, v))
}
```

When substituting into a degenerate `LetStar` with no definitions, we can just substitute into its
body without having to take care of any shadowing. When substituting into `LetStar` with at least one definition
`xDef` for a variable `x`, we (follows the Steps 1, 2, 3 as commented in the code):

1. consider the remaining `LetStar` when removing this definition (as we did for `eval`),
2. substitute recursively into it unless `x` shadows (i.e., is identical to) the variable to be substituted,
3. build the final resulting `LetStar` from
  - the definition list consisting only of `x` mapping to the result of substituting inside `xDef` 
  - the result of substituting into the remaining `LetStar` (from steps 1 & 2) as the body.

In summary, we first deconstruct the `LetStar` into the first definition `(x, xDef)` and the remainder when removing
that definition, perform substitution individually, and then glue the results together again.
And when substituting into the remainder, we need to check whether the variable is shadowed by `(x, xDef)`.
