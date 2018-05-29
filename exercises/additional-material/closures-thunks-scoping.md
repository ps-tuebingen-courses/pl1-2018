# Additional material #2: Static scoping, or: Why closures and thunks?

TL;DR version: An example term that evaluates to a different result with the
[substitution-based FAE interpreter](https://github.com/ps-tuebingen-courses/pl1-2018/blob/master/lecturenotes/07-fae.scala#L107) than with the ["wrong" environment-based interpreter that
introduces dynamic scoping](https://github.com/ps-tuebingen-courses/pl1-2018/blob/master/lecturenotes/07-fae.scala#L231):

```scala
App(Fun('f, wth('y, 3, App('f, 2))), wth('y, 5, Fun('z, Add('y, 'z))))
```

An example term that evaluates to a different result with the
[substitution-based call-by-name LCFAE interpreter](https://github.com/ps-tuebingen-courses/pl1-2018/blob/master/lecturenotes/08-lcfae.scala#L19) than with the environment-based interpreter
based on the ["wrong" idea that would introduce dynamic scoping](https://github.com/ps-tuebingen-courses/pl1-2018/blob/master/lecturenotes/08-lcfae.scala#L128):

```scala
wth('y, 5, App(Fun('f, wth('y, 3, App('f, 2))), Fun('z, Add('y, 'z))))
```

Read on for a detailed explanation.

## 1 Static scoping

Let us first consider the following FAE term: a function application.

```scala
App(Fun('f, wth('y, 3, App('f, 2))), wth('y, 5, Fun('z, Add('y, 'z))))
```

When using our substitution-based call-by-value interpreter, this term evaluates to 7.
Click the "Details" below to see a prose explanation of how the evaluation procceeds:
<details>
<summary markdown="span">Details</summary>

- The term is a function application, so in call-by-value, we first evaluate the left side,
which is a function term and thus there is nothing left to do.
- Next we evaluate the function argument: `wth('y, 5, Fun('z, Add('y, 'z)))`.
For this we first substitute 5 into the one occurrence of 'y: `Fun('z, Add(5, 'z))`.
This is a function term, so our evaluation is done.
- Now we take this result `Fun('z, Add('y, 'z))` and substitute it into the occurrences of
the formal function parameter `'f`, yielding: `wth('y, 3, App(Fun('z, Add(5, 'z)), 2))`.
- This term can be further evaluated by substituting 3 into the occurences of 'y, of which there
are none, so the result is: `App(Fun('z, Add(5, 'z)), 2)`.
- Evaluating this application again procceeds in the same way: the function is evaluated,
but there is nothing to do left, then the argument is evaluated, which is also already a value.
Then this argument value 2 is substituted into the occurrences of the function parameter `z`, yielding:
`Add(5, 2)`, which in turn evaluates to our final result 7.
</details>

So when using the substitution-based interpreter, we can say that the right-most occurrence
of the variable 'y in our term is bound at its enclosing `wth` to the value 5, since this is the value that was
substituted into the right-most 'y.

So we can just look at the structure of the term to see what the right-most 'y
is bound to. Or, when looking at it from the binding `wth`, we can see just from
the structure what occurrences of 'y it binds; we say that these occurrences are
in the _scope_ of the `wth`.

In particular, we do not need to evaluate the term to see this binding structure.
Properties that we can only see when evaluating/executing/running programs/terms
are usually referred to as _dynamic_, whereas properties that can be determined
in an "easier" way (in particular also more safe since avoiding non-termination)
are referred to as _static_.

Thus we say that _static scoping_ holds for our substitution-based interpreter.

The opposite would be _dynamic scoping_, which as already said in the lecture we
consider a misfeature for the purposes of the lecture. We now look at two examples
where dynamic scoping might arise if we are not careful when implementing certain
features. Specifically, we discuss what happens when evaluating our example term
under dynamic scoping as compared to our desired implementation for which static
scoping holds.

## 2 Closures

Let us know think back to when we implemented the environment-based interpreter for FAE.
In a first attempt, we defined an environment as a map from symbols (for the variables) to fully evaluated terms;
this attempt failed and here we want to consider why and what that has to do with dynamic vs. static scoping.

Looking again at our example term from above, evaluation should start with an empty environment:

```scala
(App(Fun('f, wth('y, 3, App('f, 2))), wth('y, 5, Fun('z, Add('y, 'z)))), Map.empty)
```

(The first component here is the term we evaluate, the second component is the environment in
which we do that.) For evaluating applications it was rather clear that this should happen
by first evaluating the function and then the argument, and both in the original environment
(here the empty environment). So we should evaluate

```scala
(Fun('f, wth('y, 3, App('f, 2))), Map.empty)
```

(which just stays as it is) and

```scala
(wth('y, 5, Fun('z, Add('y, 'z))), Map.empty)
```

which in the next evaluation step becomes: `(Fun('z, Add('y, 'z)), ('y -> 5))`.
DANGER: We now just take the function term `Fun('z, Add('y, 'z))` as the result
(there we should already have our first doubt about our approach, since we just
throw away a potentially relevant environment).

For the application itself, we should then evaluate

```scala
(wth('y, 3, App('f, 2)), ('f -> Fun('z, Add('y, 'z))))
```

where we added to the environment the mapping from the formal function parameter 'f
to the argument evaluation result.

Step-by-step evaluating this we first get:

```scala
(App('f, 2), ('y -> 3, 'f -> Fun('z, Add('y, 'z))))`
```

where we again evaluate the left-hand side, which is the variable 'f, so we look it up in the environment:
`Fun('z, Add('y, 'z))`. Evaluating the right-hand side there is nothing to do, it is just the number 2.
So, for the application itself, we evaluate

```scala
(Add('y, 'z), ('z -> 2, 'y -> 3, 'f -> Fun('z, Add('y, 'z)))
```

where we added to the environment the mapping from the formal function parameter 'z
to the argument evaluation result.

Now we can finish our evaluation by looking up 'y and 'z in the environment and adding them up,
which gives the result 3+2=5.

But this result differs from the result 7 we obtained with the substitution-based interpreter!

Even worse, when looking at our original term

```scala
App(Fun('f, wth('y, 3, App('f, 2))), wth('y, 5, Fun('z, Add('y, 'z))))
```

we see that our interpreter behaves in such a way that the right-most 'y is now
bound at the left-most `wth`, rather than its enclosing `wth`. To actually understand
where it is bound to (and if it is bound anywhere at all!) we need to evaluate the
term, so our environment-based interpreter introduced dynamic scoping!

We can fix this by going back to the part marked with "DANGER" and taking the environment
there with us such that the right-most 'y is bound to its enclosing `wth` in effect.

But "taking the environment with us" means that we need to bind the formal function parameter
in the environment to not just the function term but also the environment relevant for it,
in this case: `('y -> 5)`. So we need to refine our definition of environment:

An environment maps symbols to either:

- a fully evaluated term that is **not** a function term, or
- a pair of a function term and an environment.

We call such a pair of a function term and environment a _closure_.

## 3 Thunks

So far we discussed different kinds of interpreters, but the evaluation strategy was always call-by-value.
If we want to implement call-by-name in the substitution-based interpreter, all we need to change is leave away
the evaluation of the argument before substituting it, that is, we directly substitute the unevaluated term.

However, when implementing call-by-name in the _environment-based_ interpreter, we again need to be
careful not to introduce a form of dynamic scoping.
Consider the following example term (not quite the same as our example from the start), in the empty environment:

```scala
(wth('y, 5, App(Fun('f, wth('y, 3, App('f, 2))), Fun('z, Add('y, 'z)))), Map.empty)
```

Directly translating the change we made to the substitution-based interpreter in order to switch to call-by-name,
we could attempt to implement evaluation of applications such that the formal function parameter in the new environment
maps to the unevaluated argument term. The evaluation steps are thus:
 
```scala
(App(Fun('f, wth('y, 3, App('f, 2))), Fun('z, Add('y, 'z))), ('y -> 5))
```

```scala
(wth('y, 3, App('f, 2)), ('f -> Fun('z, Add('y, 'z)), 'y -> 5))
```

```scala
(App('f, 2), ('y -> 3, 'f -> Fun('z, Add('y, 'z)), 'y -> 5))
```

```scala
(App(Fun('z, Add('y, 'z)), 2), ('y -> 3, 'f -> Fun('z, Add('y, 'z)), 'y -> 5))
```

```scala
(Add('y, 'z), ('z -> 2, 'y -> 3, 'f -> Fun('z, Add('y, 'z)), 'y -> 5))
```

Finally, we just add what 'y and 'z are bound to, and the result is 3+2=5.
But running the substitution-based call-by-name interpreter produces the result 7!

Again, we have encountered a case where our interpreter introduced dynamic scoping.
Looking at the original term

```scala
wth('y, 5, App(Fun('f, wth('y, 3, App('f, 2))), Fun('z, Add('y, 'z))))
```

the right-most 'y is, under this interpretation, not bound to the enclosing `wth` (the left-most one),
as expected under static scoping, but rather the second `wth` from left to right.

We solve this by applying the same basic idea as for closures: The relevant environment needs to be packed
together with the term. Only this time, we do not have a pair of a (fully evaluated) function term and
an environment mapping to such closures, but rather a pair of some not necessarily fully evaluated term
and an environment mapping to such pairs. We call a pair of some term and some such environment a _thunk_.

When we encounter a variable in a function position, we _force_ the thunk bound to that variable, that is,
we evaluate the term contained in the thunk (its first component) in the environment contained in the thunk
(its second component).

## Summary

In summary, both closures and thunks pack an environment together with some object that the environment is relevant for
(a function term or an arbitrary term, respectively). We can thus use them as the targets of the map from symbols, that is,
the environment, in order to ensure static scoping for our environment-based interpreters.
