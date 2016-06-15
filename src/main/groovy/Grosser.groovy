import SATUtils.DIMACSFileOperations.DIMACSFileCNFInput
import SATUtils.DIMACSFileOperations.Output.DIMACSFileSolverOutput
import SATUtils.SolutionValidator.ParallelSolutionChecker
import SATUtils.SolutionValidator.SolutionChecker
import elements.Formula
import org.pmw.tinylog.Configurator
import org.pmw.tinylog.Logger

import java.util.concurrent.TimeUnit

class Grosser {

    static final private String DEFAULT_FILE_OUT = "out.cnf"
    static private OptionAccessor options
    static private SolutionChecker checker
    static private Formula parsedInput
    static private File inputFile
    static private File outputFile
    static Long TIMEOUT = 10
    static TimeUnit TIME_FORMAT = TimeUnit.MINUTES
    static Integer NUMBER_OF_SOLVERS = 2

    static void main(String... args) {
        resolveArguments( args )
        init()

        PSolver solver = new PSolver()

        Logger.info "Input Formula has ${parsedInput.numberOfClauses} clauses, ${parsedInput.numberOfLiterals} literals and ${parsedInput.numberOfVariables} variables."

        List<Integer> solution
        Long startSolving, endSolving

        if ( options.s ) {
            startSolving = System.nanoTime()
            solution = solver.orchestrateSolvers(NUMBER_OF_SOLVERS, parsedInput, TIMEOUT, TIME_FORMAT, false)
            endSolving = System.nanoTime()
        } else {
            startSolving = System.nanoTime()
            solution = solver.orchestrateSolvers(NUMBER_OF_SOLVERS, parsedInput, TIMEOUT, TIME_FORMAT, true)
            endSolving = System.nanoTime()
            if ( !options.d ) {
                Logger.info("GROSSER solution: ${ solution }")
            }
        }

        if ( !options.s ) {
            Logger.info "Total solving time with thread startup: \
            ${( endSolving - startSolving ) / ( 1000 ** 3 ) } secs"
        }

        def out = new DIMACSFileSolverOutput(outputFile)
        out.setHeaderInfo("MIT", "GROSSER", "GROovy Satisfiablity Solver Extended Recursively")

        out << [ solution, ["Total solving time (in secs)": ( endSolving - startSolving ) / ( 1000 ** 3 ) ] ] // TODO get the stats?

        if ( !options.s ) {
            Logger.info("Solution written to ${outputFile.name}")
        }

        if ( options.c ) {
            if ( solution ) {
                Logger.info("Checking solution...")
                Boolean doesHold = checker.validateFormula( parsedInput, solution, false )
                if ( doesHold ) {
                    Logger.info("Solution holds.")
                } else {
                    Logger.warn("Solution does NOT hold.")
                }
            }
        }

    }

    static void resolveArguments(String... args) {
        def argCtl = new CliBuilder(usage: "java -jar grosser.jar -[hsd] " +
                "-[w|-workers] <numberOfWorkers> -[t|-timeout] <timeout> -[i|-input] <inputFile> -[o|-output] <outputFile> ")

        argCtl.with {
            h longOpt: "help", 'Show usage information'
            i longOpt: "input", args: 1, argName: 'input file', 'Required: Solve SAT using DIMACS CNF "input file"'
            o longOpt: "output", args: 1, argName: 'output file', 'Choose solution output CNF file'
            w longOpt: "workers", args: 1, argName: 'number of threads', 'Set number of workers used in solving. Default: 2'
            s longOpt: "silent", 'Limit the verbosity of the solver'
            c longOpt: "validate", 'Validate the solution against the parsed input'
            t longOpt: "timeout", args: 1, argName: 'timeoutFormat', 'Set the timeout limit. The format is a integer followed by any of the following suffixes: ' +
                    '\n d, days: for days\n h, hour: for hours\n m, min: for minutes\n s, sec: for secs\n ms: for milliseconds'
            d longOpt: "no-solution", "Don't show solution vector in stdout"
        }

        argCtl.stopAtNonOption

        options = argCtl.parse( args )

        if ( !options ) {
            System.exit(1)
        }

        if ( !args || !options.i || options.h ) {
            println "GROSSER Parallel Satisfiability Solver"
            argCtl.usage()
            System.exit(1)
        }
    }

    static private validateSolution(Formula formula, List<Integer> solution) {
        Logger.info "Validating solution..."
        if ( new ParallelSolutionChecker().validateFormula(formula, solution) ) {
            Logger.info "Solution holds."
        } else {
            Logger.error "Solution was unsatisfiable."
        }
    }

    static void init() {

        checker = new ParallelSolutionChecker()
        inputFile = new File( options.i as String )
        if ( !inputFile.exists() ) {
            System.err.println("Input file not found")
        }
        parsedInput = new DIMACSFileCNFInput( inputFile ).parseSolverFormula()

        if ( options.t ) {
            parseTimeout( (options.t as String ).trim() )
        }

        if ( options.w ) {
            NUMBER_OF_SOLVERS = Integer.valueOf(options.w).abs()
        }

        if ( options.o ) {
            outputFile = new File( (options.o as String).trim() )
        } else {
            outputFile = new File( DEFAULT_FILE_OUT )
        }

        if (!options.s) {
            Configurator.currentConfig().formatPattern("{level}: \t{message}").activate()
        } else {
            Configurator.currentConfig().removeAllWriters().activate()
        }
    }

    static void parseTimeout( String timeout ) {

        Long timeoutCount = timeout.find("[0-9]+")?.toLong()
        String timeUnitSuffix = timeout.find("[a-z]+")

        if ( timeoutCount && timeUnitSuffix ) {
            TIMEOUT = timeoutCount
            TIME_FORMAT = parseTimeUnit( timeUnitSuffix )
        } else {
            println("Error in timeout format. Assuming default values.")
        }
    }

    static TimeUnit parseTimeUnit( String suffix ) throws IllegalArgumentException {
        switch ( suffix.toLowerCase() ) {
            case "hour":
            case "h":
                return TimeUnit.HOURS
            case "min":
            case "m":
                return TimeUnit.MINUTES
            case "sec":
            case "s":
                return TimeUnit.SECONDS
            case "ms":
                return TimeUnit.MILLISECONDS
            case "days":
            case "d":
                return TimeUnit.DAYS
            default:
                return TimeUnit.MINUTES
        }
    }
}
