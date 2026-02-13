package edu.utexas.cs.alr.util;

import edu.utexas.cs.alr.ast.AndExpr;
import edu.utexas.cs.alr.ast.Expr;
import edu.utexas.cs.alr.ast.NegExpr;
import edu.utexas.cs.alr.ast.OrExpr;
import edu.utexas.cs.alr.ast.VarExpr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SatUtil {
    public static boolean checkSAT(Expr expr)
    {
        List<List<Long>> clauses = new ArrayList<>();
        collectClauses(expr, clauses);
        return dpll(clauses, new HashMap<>());
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

        Set<Long> literals = new HashSet<>();
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

    private static boolean dpll(List<List<Long>> clauses, Map<Long, Boolean> assignment)
    {
        while (true)
        {
            boolean changed = false;

            for (List<Long> clause : clauses)
            {
                ClauseState state = analyzeClause(clause, assignment);
                if (state.satisfied)
                    continue;

                if (state.unassignedCount == 0)
                    return false;

                if (state.unassignedCount == 1)
                {
                    if (!assignLiteral(assignment, state.lastUnassignedLiteral))
                        return false;
                    changed = true;
                }
            }

            Map<Long, Integer> polarity = new HashMap<>();
            for (List<Long> clause : clauses)
            {
                ClauseState state = analyzeClause(clause, assignment);
                if (state.satisfied)
                    continue;

                for (long lit : clause)
                {
                    long var = Math.abs(lit);
                    if (assignment.containsKey(var))
                        continue;

                    int signMask = lit > 0 ? 1 : 2;
                    polarity.put(var, polarity.getOrDefault(var, 0) | signMask);
                }
            }

            for (Map.Entry<Long, Integer> entry : polarity.entrySet())
            {
                int signMask = entry.getValue();
                if (signMask == 1 || signMask == 2)
                {
                    long lit = signMask == 1 ? entry.getKey() : -entry.getKey();
                    if (!assignLiteral(assignment, lit))
                        return false;
                    changed = true;
                }
            }

            if (!changed)
                break;
        }

        boolean allSatisfied = true;
        for (List<Long> clause : clauses)
        {
            ClauseState state = analyzeClause(clause, assignment);
            if (!state.satisfied)
            {
                allSatisfied = false;
                break;
            }
        }

        if (allSatisfied)
            return true;

        long branchVar = pickBranchVariable(clauses, assignment);
        if (branchVar == -1)
            return false;

        Map<Long, Boolean> leftAssignment = new HashMap<>(assignment);
        leftAssignment.put(branchVar, true);
        if (dpll(clauses, leftAssignment))
            return true;

        Map<Long, Boolean> rightAssignment = new HashMap<>(assignment);
        rightAssignment.put(branchVar, false);
        return dpll(clauses, rightAssignment);
    }

    private static long pickBranchVariable(List<List<Long>> clauses, Map<Long, Boolean> assignment)
    {
        for (List<Long> clause : clauses)
        {
            ClauseState state = analyzeClause(clause, assignment);
            if (state.satisfied)
                continue;

            for (long lit : clause)
            {
                long var = Math.abs(lit);
                if (!assignment.containsKey(var))
                    return var;
            }
        }

        return -1;
    }

    private static boolean assignLiteral(Map<Long, Boolean> assignment, long lit)
    {
        long var = Math.abs(lit);
        boolean value = lit > 0;
        Boolean existing = assignment.get(var);

        if (existing != null)
            return existing == value;

        assignment.put(var, value);
        return true;
    }

    private static ClauseState analyzeClause(List<Long> clause, Map<Long, Boolean> assignment)
    {
        ClauseState state = new ClauseState();

        for (long lit : clause)
        {
            long var = Math.abs(lit);
            Boolean value = assignment.get(var);

            if (value == null)
            {
                state.unassignedCount++;
                state.lastUnassignedLiteral = lit;
            }
            else if ((lit > 0 && value) || (lit < 0 && !value))
            {
                state.satisfied = true;
                return state;
            }
        }

        return state;
    }

    private static final class ClauseState
    {
        private boolean satisfied;
        private int unassignedCount;
        private long lastUnassignedLiteral;
    }
}
