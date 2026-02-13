package edu.utexas.cs.alr.util;

import edu.utexas.cs.alr.ast.AndExpr;
import edu.utexas.cs.alr.ast.Expr;
import edu.utexas.cs.alr.ast.NegExpr;
import edu.utexas.cs.alr.ast.OrExpr;
import edu.utexas.cs.alr.ast.VarExpr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SatUtil {
    public static boolean checkSAT(Expr expr)
    {
        List<List<Long>> clauses = new ArrayList<>();
        collectClauses(expr, clauses);
        return solveCdcl(clauses);
    }

    private static boolean solveCdcl(List<List<Long>> clauses)
    {
        SolverState state = new SolverState(clauses);

        while (true)
        {
            Integer conflictClause = propagate(state);
            if (conflictClause != null)
            {
                if (state.decisionLevel == 0)
                    return false;

                LearnedClause learned = analyzeConflict(state, conflictClause);
                if (learned.clause.isEmpty())
                    return false;

                backtrack(state, learned.backtrackLevel);
                int learnedIndex = state.clauses.size();
                state.clauses.add(learned.clause);
                enqueue(state, learned.assertingLiteral, learnedIndex);
                continue;
            }

            Long decisionLiteral = pickDecisionLiteral(state);
            if (decisionLiteral == null)
                return true;

            state.decisionLevel++;
            state.levelStart.add(state.trail.size());
            enqueue(state, decisionLiteral, null);
        }
    }

    private static Integer propagate(SolverState state)
    {
        while (true)
        {
            boolean changed = false;

            for (int i = 0; i < state.clauses.size(); i++)
            {
                List<Long> clause = state.clauses.get(i);

                boolean satisfied = false;
                int unassignedCount = 0;
                long lastUnassigned = 0;

                for (long lit : clause)
                {
                    Boolean value = literalValue(state.assignment, lit);
                    if (value == null)
                    {
                        unassignedCount++;
                        lastUnassigned = lit;
                    }
                    else if (value)
                    {
                        satisfied = true;
                        break;
                    }
                }

                if (satisfied)
                    continue;

                if (unassignedCount == 0)
                    return i;

                if (unassignedCount == 1)
                {
                    if (!enqueue(state, lastUnassigned, i))
                        return i;
                    changed = true;
                }
            }

            if (!changed)
                return null;
        }
    }

    private static LearnedClause analyzeConflict(SolverState state, int conflictClauseIdx)
    {
        List<Long> learned = new ArrayList<>(state.clauses.get(conflictClauseIdx));

        while (countLiteralsAtLevel(learned, state, state.decisionLevel) > 1)
        {
            long pivot = latestAssignedLiteralAtLevel(learned, state, state.decisionLevel);
            Assignment pivotAssignment = state.assignment.get(Math.abs(pivot));

            if (pivotAssignment == null || pivotAssignment.reasonClause == null)
                break;

            List<Long> reason = state.clauses.get(pivotAssignment.reasonClause);
            learned = resolve(learned, reason, pivot);
        }

        long assertingLiteral = 0;
        int currentLevelCount = 0;
        int backtrackLevel = 0;

        for (long lit : learned)
        {
            Assignment a = state.assignment.get(Math.abs(lit));
            int level = a == null ? 0 : a.level;

            if (level == state.decisionLevel)
            {
                currentLevelCount++;
                assertingLiteral = lit;
            }
            else if (level > backtrackLevel)
            {
                backtrackLevel = level;
            }
        }

        if (currentLevelCount == 0)
        {
            if (!learned.isEmpty())
                assertingLiteral = learned.get(0);
            backtrackLevel = 0;
        }

        return new LearnedClause(learned, assertingLiteral, backtrackLevel);
    }

    private static List<Long> resolve(List<Long> a, List<Long> b, long pivot)
    {
        Set<Long> merged = new LinkedHashSet<>();

        for (long lit : a)
        {
            if (lit != pivot)
                merged.add(lit);
        }

        for (long lit : b)
        {
            if (lit != -pivot)
                merged.add(lit);
        }

        return new ArrayList<>(merged);
    }

    private static int countLiteralsAtLevel(List<Long> clause, SolverState state, int level)
    {
        int count = 0;
        for (long lit : clause)
        {
            Assignment a = state.assignment.get(Math.abs(lit));
            if (a != null && a.level == level)
                count++;
        }
        return count;
    }

    private static long latestAssignedLiteralAtLevel(List<Long> clause, SolverState state, int level)
    {
        for (int i = state.trail.size() - 1; i >= 0; i--)
        {
            long lit = state.trail.get(i);
            long var = Math.abs(lit);
            if (!containsLiteral(clause, var))
                continue;

            Assignment a = state.assignment.get(var);
            if (a != null && a.level == level)
            {
                if (containsExactLiteral(clause, lit))
                    return lit;
                return -lit;
            }
        }

        return clause.get(0);
    }

    private static boolean containsLiteral(List<Long> clause, long var)
    {
        for (long lit : clause)
        {
            if (Math.abs(lit) == var)
                return true;
        }
        return false;
    }

    private static boolean containsExactLiteral(List<Long> clause, long lit)
    {
        for (long cLit : clause)
        {
            if (cLit == lit)
                return true;
        }
        return false;
    }

    private static void backtrack(SolverState state, int level)
    {
        while (state.decisionLevel > level)
        {
            int start = state.levelStart.remove(state.levelStart.size() - 1);
            for (int i = state.trail.size() - 1; i >= start; i--)
            {
                long lit = state.trail.remove(i);
                state.assignment.remove(Math.abs(lit));
            }
            state.decisionLevel--;
        }
    }

    private static Long pickDecisionLiteral(SolverState state)
    {
        for (List<Long> clause : state.clauses)
        {
            for (long lit : clause)
            {
                long var = Math.abs(lit);
                if (!state.assignment.containsKey(var))
                    return var;
            }
        }
        return null;
    }

    private static boolean enqueue(SolverState state, long lit, Integer reasonClause)
    {
        long var = Math.abs(lit);
        boolean value = lit > 0;
        Assignment existing = state.assignment.get(var);

        if (existing != null)
            return existing.value == value;

        state.assignment.put(var, new Assignment(value, state.decisionLevel, reasonClause));
        state.trail.add(value ? var : -var);
        return true;
    }

    private static Boolean literalValue(Map<Long, Assignment> assignment, long lit)
    {
        Assignment a = assignment.get(Math.abs(lit));
        if (a == null)
            return null;
        return lit > 0 ? a.value : !a.value;
    }

    private static void collectClauses(Expr expr, List<List<Long>> clauses)
    {
        if (expr.getKind() == Expr.ExprKind.AND)
        {
            AndExpr andExpr = (AndExpr) expr;
            collectClauses(andExpr.getLeft(), clauses);
            collectClauses(andExpr.getRight(), clauses);
            return;
        }

        Set<Long> literals = new LinkedHashSet<>();
        collectClauseLiterals(expr, literals);

        for (long lit : literals)
        {
            if (literals.contains(-lit))
                return;
        }

        clauses.add(new ArrayList<>(literals));
    }

    private static void collectClauseLiterals(Expr expr, Set<Long> literals)
    {
        if (expr.getKind() == Expr.ExprKind.OR)
        {
            OrExpr orExpr = (OrExpr) expr;
            collectClauseLiterals(orExpr.getLeft(), literals);
            collectClauseLiterals(orExpr.getRight(), literals);
            return;
        }

        literals.add(toLiteral(expr));
    }

    private static long toLiteral(Expr expr)
    {
        if (expr.getKind() == Expr.ExprKind.VAR)
            return ((VarExpr) expr).getId();

        if (expr.getKind() == Expr.ExprKind.NEG)
            return -((VarExpr) ((NegExpr) expr).getExpr()).getId();

        throw new IllegalArgumentException("Expected CNF literal but found: " + expr.getKind());
    }

    private static final class SolverState
    {
        private final List<List<Long>> clauses;
        private final Map<Long, Assignment> assignment = new HashMap<>();
        private final List<Long> trail = new ArrayList<>();
        private final List<Integer> levelStart = new ArrayList<>();
        private int decisionLevel = 0;

        private SolverState(List<List<Long>> clauses)
        {
            this.clauses = new ArrayList<>(clauses);
        }
    }

    private static final class Assignment
    {
        private final boolean value;
        private final int level;
        private final Integer reasonClause;

        private Assignment(boolean value, int level, Integer reasonClause)
        {
            this.value = value;
            this.level = level;
            this.reasonClause = reasonClause;
        }
    }

    private static final class LearnedClause
    {
        private final List<Long> clause;
        private final long assertingLiteral;
        private final int backtrackLevel;

        private LearnedClause(List<Long> clause, long assertingLiteral, int backtrackLevel)
        {
            this.clause = clause;
            this.assertingLiteral = assertingLiteral;
            this.backtrackLevel = backtrackLevel;
        }
    }
}
