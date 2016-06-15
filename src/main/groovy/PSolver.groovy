import elements.Formula
import elements.Solver
import elements.clauseLearning.ClauseSharerActor
import elements.conflictHandler.TwoWatchChecker
import groovyx.gpars.actor.Actor
import groovyx.gpars.actor.Actors
import org.pmw.tinylog.Logger

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class PSolver {
    private List<Actor> solverActors = []
    private List<Solver> solvers = []
    private BlockingQueue<List<Integer>> answers

    List<Integer> orchestrateSolvers(Integer numberOfSolvers, Formula problem, Long timeout, TimeUnit timeFormat, Boolean log = false) {
        answers = new ArrayBlockingQueue<>(numberOfSolvers, true)
        def random = new Random()

        numberOfSolvers.times {
            ClauseSharerActor sharerActor = new ClauseSharerActor( solverList: solvers )
            Solver newSolver = new Solver(random.nextLong(), new TwoWatchChecker(), sharerActor)
            solvers << newSolver
        }

        numberOfSolvers.times {
            solverActors << Actors.actor {
                react { Map message ->
                    reply( message.solver.solve( message.formula as Formula ) )
                }
            }
        }

        if ( log ) {
            Logger.info "Started ${numberOfSolvers} solvers..."
        }

        def startTime = System.nanoTime()
        solverActors.eachWithIndex { Actor solverActor, Integer index ->
            solverActor.sendAndPromise( [solver: solvers[index], formula: problem, id: random.nextLong()] ).whenBound { answers << (it as List<Integer>) }
        }

        def result = answers.poll(timeout, timeFormat)
        def endTime = System.nanoTime()
        if ( log ) {
            Logger.info "Time before first solution: ${ (endTime - startTime) / (1000 ** 3)}"
        }
        solverActors*.stop()
        result
    }
}
