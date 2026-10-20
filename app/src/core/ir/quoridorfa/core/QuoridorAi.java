package ir.quoridorfa.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Quoridor AI — Minimax with alpha-beta pruning (per technical design doc §5).
 * Easy   : depth 1 (greedy) with 25% random pawn move
 * Medium : depth 2
 * Hard   : depth 3, move ordering, 1.5 s time budget
 *
 * Implementation note: every node works on its own GameCore COPY (make-state,
 * no undo). State copies are tiny (~200 bytes), this removes an entire class
 * of undo-bookkeeping corruption bugs and is fast enough by a wide margin.
 *
 * Evaluation (side-to-move perspective):
 *   (opponent shortest path - own shortest path) * 100
 *   + (own row progress - opponent row progress) * 3
 *   + (own walls - opponent walls) * 3
 */
public class QuoridorAi {

    public static final int EASY = 0, MEDIUM = 1, HARD = 2;

    private static final int[] DEPTH = {1, 2, 3};
    private static final long[] TIME_BUDGET_MS = {350, 900, 1500};
    private static final int WIN_SCORE = 1_000_000;

    private final Random rnd;
    private long deadline;

    public QuoridorAi(long seed) {
        rnd = new Random(seed);
    }

    public QuoridorAi() {
        this(System.nanoTime());
    }

    /**
     * Choose an action for player p (must be the player on turn).
     * Returns {type, a, b}: 0 = pawn move to (a,b); 1 = H wall (b = r*WN+c); 2 = V wall.
     */
    public int[] chooseAction(GameCore state, int p, int difficulty) {
        int depth = DEPTH[Math.max(0, Math.min(2, difficulty))];
        deadline = System.currentTimeMillis() + TIME_BUDGET_MS[Math.max(0, Math.min(2, difficulty))];

        List<int[]> actions = state.allActions(p);
        if (actions.isEmpty()) return null;

        // Easy: occasionally play a random pawn move to stay beatable
        if (difficulty == EASY && rnd.nextInt(100) < 25) {
            List<int[]> moves = state.legalPawnMoves(p);
            int[] m = moves.get(rnd.nextInt(moves.size()));
            return new int[]{GameCore.ACT_MOVE, m[0], m[1]};
        }

        // --- Root: apply every action on its own copy, order by shallow evaluation ---
        List<Object[]> scored = new ArrayList<>(actions.size());
        for (int[] a : actions) {
            GameCore w = state.copy();
            applyAction(w, a, p);
            scored.add(new Object[]{a, evaluate(w, p)});
        }
        Collections.sort(scored, new Comparator<Object[]>() {
            @Override public int compare(Object[] o1, Object[] o2) {
                return Long.compare((Long) o2[1], (Long) o1[1]); // desc
            }
        });

        // Full window at the ROOT for every action -> exact scores, no fake ties.
        // (Subtrees still prune internally with their own alpha-beta window.)
        final long aFull = Long.MIN_VALUE / 2, bFull = Long.MAX_VALUE / 2;

        int[] bestAction = (int[]) scored.get(0)[0];
        long bestScore = Long.MIN_VALUE;
        for (Object[] entry : scored) {
            int[] a = (int[]) entry[0];
            if (System.currentTimeMillis() > deadline && bestScore != Long.MIN_VALUE) break;
            GameCore w = state.copy();
            applyAction(w, a, p);
            long s;
            if (w.winner == p) {
                // Terminal right after our move: keep the SAME scale as search()
                // terminals so an immediate win always outranks a win found deeper.
                s = WIN_SCORE + (depth - 1);
            } else {
                s = -search(w, p ^ 1, depth - 1, aFull, bFull);
            }
            if (s > bestScore) {
                bestScore = s;
                bestAction = a;
            } else if (s == bestScore && s < WIN_SCORE) {
                // genuine exact tie -> tiny jitter breaks endless shuffles
                if (rnd.nextInt(3) > 0) bestAction = a;
            }
        }
        return bestAction;
    }

    /** Negamax layer: player `mover` to act on state s; score from mover's perspective. */
    private long search(GameCore s, int mover, int depth, long alpha, long beta) {
        if (s.winner != -1) {
            return s.winner == mover ? WIN_SCORE + depth : -(WIN_SCORE + depth);
        }
        if (depth <= 0 || System.currentTimeMillis() > deadline) {
            return evaluate(s, mover);
        }
        List<int[]> actions = candidateActions(s, mover, depth);
        long best = Long.MIN_VALUE / 2;
        for (int[] a : actions) {
            GameCore next = s.copy();
            applyAction(next, a, mover);
            long v = -search(next, mover ^ 1, depth - 1, -beta, -alpha);
            if (v > best) best = v;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break; // beta cut
            if (System.currentTimeMillis() > deadline) break;
        }
        return best;
    }

    /**
     * Candidate actions with pruning for deeper plies:
     * pawn moves always; walls only near either pawn (Chebyshev distance <= 2).
     */
    private List<int[]> candidateActions(GameCore s, int p, int depth) {
        List<int[]> res = new ArrayList<>();
        for (int[] m : s.legalPawnMoves(p)) res.add(new int[]{GameCore.ACT_MOVE, m[0], m[1]});
        if (s.wallsLeft[p] > 0 && depth >= 1) {
            int r0 = s.pawnR[p], c0 = s.pawnC[p], r1 = s.pawnR[p ^ 1], c1 = s.pawnC[p ^ 1];
            int rMin = Math.max(0, Math.min(r0, r1) - 2), rMax = Math.min(GameCore.WN - 1, Math.max(r0, r1) + 2);
            int cMin = Math.max(0, Math.min(c0, c1) - 2), cMax = Math.min(GameCore.WN - 1, Math.max(c0, c1) + 2);
            for (int r = rMin; r <= rMax; r++)
                for (int c = cMin; c <= cMax; c++) {
                    if (s.hWall[r][c] || s.vWall[r][c]) continue;
                    if (s.canPlaceWall(true, r, c)) res.add(new int[]{GameCore.ACT_WALL_H, 0, r * GameCore.WN + c});
                    if (s.canPlaceWall(false, r, c)) res.add(new int[]{GameCore.ACT_WALL_V, 0, r * GameCore.WN + c});
                }
        }
        return res;
    }

    /** Static evaluation from player p's perspective. */
    private long evaluate(GameCore s, int p) {
        if (s.winner == p) return WIN_SCORE;
        if (s.winner != -1) return -WIN_SCORE;
        int my = s.shortestPath(p);
        int op = s.shortestPath(p ^ 1);
        // Row progress toward each goal: breaks corner shuffles; weight 3 per row
        // stays far below the 100-unit path difference and above root jitter (2).
        int myAdv = (p == 0) ? (GameCore.N - 1 - s.pawnR[0]) : s.pawnR[1];
        int opAdv = (p == 0) ? (GameCore.N - 1 - s.pawnR[1]) : s.pawnR[0];
        return (op - my) * 100L
                + (myAdv - opAdv) * 3L
                + (s.wallsLeft[p] - s.wallsLeft[p ^ 1]) * 3L;
    }

    /** Apply an action to a scratch state (no undo needed — callers use copies). */
    private void applyAction(GameCore s, int[] a, int p) {
        if (a[0] == GameCore.ACT_MOVE) {
            s.applyPawnMove(a[1], a[2]);
        } else {
            int r = a[2] / GameCore.WN, c = a[2] % GameCore.WN;
            if (a[0] == GameCore.ACT_WALL_H) s.hWall[r][c] = true; else s.vWall[r][c] = true;
            s.wallsLeft[p]--;
            s.moveCount++;
            s.turn = p ^ 1;
        }
    }
}
